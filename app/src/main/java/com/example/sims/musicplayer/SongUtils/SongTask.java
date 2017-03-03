package com.example.sims.musicplayer.SongUtils;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.example.sims.musicplayer.R;
import com.example.sims.musicplayer.StorageUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import xyz.danoz.recyclerviewfastscroller.vertical.VerticalRecyclerViewFastScroller;

/**
 * Created by sims on 29/1/17.
 */

public class SongTask extends AsyncTask<Void, Void, ArrayList<Song>> {

    Context context;
    Activity activity;
    RecyclerView mSongRecyclerView;

    public SongTask(Context context) {
        this.context = context;
        activity = (Activity)  context;

    }

    @Override
    protected ArrayList<Song> doInBackground(Void[] params) {
        ContentResolver contentResolver = context.getContentResolver();
        String[] STAR = { "*" };
        Cursor cursor;
        Uri allsongsuri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        ArrayList<Song> mSongList = new ArrayList<>();
        cursor = contentResolver.query(allsongsuri, STAR, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String title = cursor.getString(cursor
                            .getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));

                    int id = cursor.getInt(cursor
                            .getColumnIndex(MediaStore.Audio.Media._ID));

                    String data = cursor.getString(cursor
                            .getColumnIndex(MediaStore.Audio.Media.DATA));

                    String album = cursor.getString(cursor
                            .getColumnIndex(MediaStore.Audio.Media.ALBUM));

                    long album_id = cursor.getLong(cursor.
                            getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));

                    String artist = cursor.getString(cursor
                            .getColumnIndex(MediaStore.Audio.Media.ARTIST));

                    mSongList.add(new Song(id, title, artist, data, album, album_id));

                    Collections.sort(mSongList, new Comparator<Song>() {
                        @Override
                        public int compare(Song o1, Song o2) {
                            return o1.getData().compareTo(o2.getData());
                        }
                    });

                } while (cursor.moveToNext());

            }
            cursor.close();
        }
        return mSongList;
    }

    @Override
    protected void onPostExecute(ArrayList<Song> mSongList) {
        super.onPostExecute(mSongList);
        LinearLayoutManager manager = new LinearLayoutManager(context);
        mSongRecyclerView = (RecyclerView) activity.findViewById(R.id.playlist);
        mSongRecyclerView.setLayoutManager(manager);
        mSongRecyclerView.setHasFixedSize(true);
        new StorageUtils(context).storeSong(mSongList);
        SongAdapter mSongAdapter = new SongAdapter(context, mSongList, (SongAdapter.MusicItemClickListener) context);
        mSongRecyclerView.setAdapter(mSongAdapter);
    }
}
