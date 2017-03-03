package com.example.sims.musicplayer.ServiceUtils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.drm.DrmStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.example.sims.musicplayer.MainActivity;
import com.example.sims.musicplayer.R;
import com.example.sims.musicplayer.SongUtils.Song;
import com.example.sims.musicplayer.StorageUtils;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener
, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnInfoListener, MediaPlayer.OnPreparedListener{

    private final IBinder iBinder = new LocalBinder();
    private MediaPlayer mMediaPlayer;
    private int resumePosition;
    private NoisyReceiver mReceiver;
    private boolean callState;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    //List of available songs
    private ArrayList<Song> mSongList;
    private int songIndex = -1;
    private Song activeSong = null;                 //an object of the currently playing song
    private PlayNewSongReceiver mPlayReceiver;

    //MediaSession
    private MediaSessionManager mManager;
    private MediaControllerCompat.TransportControls mControls;
    private MediaSessionCompat mSessionCompat;

    private static final int NOTIFICATION_ID = 101;

    @Override
    public void onCreate() {
        super.onCreate();
        registerPlayNewSongReceiver();
        registerNoisyReceiver();
        callStateListener();
    }

    private void initMediaSession() throws RemoteException{
        if (mManager != null) {
            return;
        }

        mManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        mSessionCompat = new MediaSessionCompat(getApplicationContext(), "Music Player");       //New Media Session
        mControls = mSessionCompat.getController().getTransportControls();                      // Media Controls
        mSessionCompat.setActive(true);                                     //Ready to receive media commands

        mSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        updateMetaData();

        mSessionCompat.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                skipToPrevious();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED);
            }

            @Override
            public void onPlay() {
                super.onPlay();
                playMedia();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                stopSelf();
            }
        });
    }

    private void skipToPrevious() {
        if (songIndex == 0){
            songIndex = mSongList.size() - 1;
            activeSong = mSongList.get(songIndex);
        }
        else {
            songIndex--;
            activeSong = mSongList.get(songIndex);
        }
        new StorageUtils(this).storeSongIndex(songIndex);
        stopMedia();
        mMediaPlayer.reset();
        initMediaPlayer();
    }

    private void skipToNext(){
        if (songIndex == mSongList.size()-1){
            songIndex = 0;
            activeSong = mSongList.get(songIndex);
        }
        else {
            songIndex++;
            activeSong = mSongList.get(songIndex);
        }
        new StorageUtils(this).storeSongIndex(songIndex);
        stopMedia();
        mMediaPlayer.reset();
        initMediaPlayer();
    }

    private void updateMetaData(){
        mSessionCompat.setMetadata(new MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, getAlbumArt(activeSong.getAlbumId()))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, activeSong.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeSong.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, activeSong.getTitle()).build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            StorageUtils storage = new StorageUtils(getApplicationContext());
            mSongList = storage.loadSong();
            songIndex = storage.loadSongIndex();

            if (songIndex != -1 && songIndex < mSongList.size()) {
                activeSong = mSongList.get(songIndex);
            }

        } catch (NullPointerException e){
            stopSelf();
        }

        if (requestAudioFocus() == false) {
            //Could not gain focus
            stopSelf();
        }

        if (mManager == null){
            try {
                initMediaSession();
                initMediaPlayer();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
            buildNotification(PlaybackStatus.PLAYING);
        }

        handleIncomingEvents(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {

        if (mMediaPlayer != null)
            stopMedia();
        mMediaPlayer.release();
        removeAudioFocus();

        unregisterReceiver(mPlayReceiver);
        unregisterReceiver(mReceiver);
        removeNotification();

        new StorageUtils(getApplicationContext()).clearCachedSongPlayList();
        if (phoneStateListener != null){
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {

    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        playMedia();
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {

        //Handling the errors that might occur
        switch (what){
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("Media Player", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK" + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("Media Player", "MEDIA ERROR SERVER DIED" + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("Media Player", "MEDIA ERROR UNKNOWN" + extra);
                break;
        }

        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        //stop the service
        stopSelf();
        skipToNext();
        buildNotification(PlaybackStatus.PLAYING);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public void onAudioFocusChange(int focusChange) {

        switch (focusChange){

            case AudioManager.AUDIOFOCUS_GAIN:
                if (mMediaPlayer == null)
                    initMediaPlayer();
                if (!mMediaPlayer.isPlaying())
                    mMediaPlayer.start();
                mMediaPlayer.setVolume(1.0f, 1.0f);
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                if (mMediaPlayer.isPlaying())
                    mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mMediaPlayer.isPlaying())
                    mMediaPlayer.pause();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mMediaPlayer.isPlaying())
                    mMediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }

    }

    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    //Initializing the MediaPlayer object
    public void initMediaPlayer(){

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnInfoListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnSeekCompleteListener(this);
        mMediaPlayer.setOnBufferingUpdateListener(this);

        mMediaPlayer.reset(); //so that media player object doesn't point to any resource

        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mMediaPlayer.setDataSource(activeSong.getData());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }

        mMediaPlayer.prepareAsync();
    }

    private void playMedia(){
        if (!mMediaPlayer.isPlaying()){
            mMediaPlayer.start();
        }
    }

    private void stopMedia(){
        if (mMediaPlayer == null) return;
        if (mMediaPlayer.isPlaying())
            mMediaPlayer.stop();
    }

    private void pauseMedia(){
        if (mMediaPlayer.isPlaying()){
            mMediaPlayer.pause();
            resumePosition = mMediaPlayer.getCurrentPosition();
        }
    }

    private void resumeMedia() {
        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.seekTo(resumePosition);
            mMediaPlayer.start();
        }
    }

    private boolean requestAudioFocus(){

        AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int result = manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            return true;
        }
        return false;

    }

    private boolean removeAudioFocus(){

        AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == manager.abandonAudioFocus(this);

    }

    private void registerNoisyReceiver(){
        mReceiver = new NoisyReceiver();
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mReceiver, intentFilter);
    }

    public class NoisyReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            pauseMedia();
            buildNotification(PlaybackStatus.PAUSED);
        }
    }

    private void registerPlayNewSongReceiver(){
        IntentFilter intentFilter = new IntentFilter(MainActivity.BROADCAST_PLAY_NEW_AUDIO);
        mPlayReceiver = new PlayNewSongReceiver();
        registerReceiver(mPlayReceiver, intentFilter);
    }

    public class PlayNewSongReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            StorageUtils storage = new StorageUtils(getApplicationContext());
            int songIndex = storage.loadSongIndex();
            if (songIndex != -1 && songIndex < mSongList.size()){
                activeSong = mSongList.get(songIndex);
            }
            else {
                stopSelf();
            }

            stopMedia();
            updateMetaData();
            mMediaPlayer.reset();
            initMediaPlayer();
            buildNotification(PlaybackStatus.PLAYING);
        }
    }

    private void callStateListener(){
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener(){
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state){
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mMediaPlayer != null){
                            pauseMedia();
                            callState = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (mMediaPlayer != null) {
                            if (callState){
                                callState = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    public void buildNotification(PlaybackStatus status){

        int notificationAction = android.R.drawable.ic_media_pause;
        PendingIntent pendingIntent = null;

        if (status == PlaybackStatus.PLAYING){
            pendingIntent = playbackAction(1);
            notificationAction = android.R.drawable.ic_media_pause;
        }
        else if (status == PlaybackStatus.PAUSED){
            pendingIntent = playbackAction(0);
            notificationAction = android.R.drawable.ic_media_play;
        }

        Bitmap largeIcon = getAlbumArt(activeSong.getAlbumId());
        NotificationCompat.Builder builder = (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                .setShowWhen(false)
                .setContentIntent(pendingIntent)
                .setStyle(new NotificationCompat.MediaStyle()
                    .setMediaSession(mSessionCompat.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2))
                    .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentTitle(activeSong.getAlbum())
                .setContentText(activeSong.getTitle())
                .setContentInfo(activeSong.getArtist())
                .addAction(android.R.drawable.ic_media_previous, "Previous", playbackAction(3))
                .addAction(notificationAction, "Pause/Play", pendingIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", playbackAction(2));

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private PendingIntent playbackAction(int i) {
        Intent intent = new Intent(this, MediaPlayerService.class);
        switch (i){
            case 0:
                intent.setAction(ServiceContract.ACTION_PLAY);
                return PendingIntent.getService(this, i, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            case 1:
                intent.setAction(ServiceContract.ACTION_PAUSE);
                return PendingIntent.getService(this, i, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            case 2:
                intent.setAction(ServiceContract.ACTION_NEXT);
                return PendingIntent.getService(this, i, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            case 3:
                intent.setAction(ServiceContract.ACTION_PREVIOUS);
                return PendingIntent.getService(this, i, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            default:
                return null;
        }
    }

    private void handleIncomingEvents(Intent playbackAction){
        if (playbackAction == null || playbackAction.getAction() == null)
            return;

        if (playbackAction.getAction().equals(ServiceContract.ACTION_PLAY))
            mControls.play();

        if (playbackAction.getAction().equals(ServiceContract.ACTION_PAUSE))
            mControls.pause();

        if (playbackAction.getAction().equals(ServiceContract.ACTION_PREVIOUS))
            mControls.skipToPrevious();

        if (playbackAction.getAction().equals(ServiceContract.ACTION_NEXT))
            mControls.skipToNext();

        if (playbackAction.getAction().equals(ServiceContract.ACTION_STOP))
            mControls.stop();
    }

    public Bitmap getAlbumArt(Long album_id) {

        Bitmap bm = null;
        try
        {
            final Uri sArtworkUri = Uri
                    .parse("content://media/external/audio/albumart");

            Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);

            ParcelFileDescriptor pfd = getContentResolver()
                    .openFileDescriptor(uri, "r");

            if (pfd != null)
            {
                FileDescriptor fd = pfd.getFileDescriptor();
                bm = BitmapFactory.decodeFileDescriptor(fd);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bm;
    }
}
