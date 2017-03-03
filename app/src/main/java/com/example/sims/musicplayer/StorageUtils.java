package com.example.sims.musicplayer;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.sims.musicplayer.SongUtils.Song;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by sims on 28/1/17.
 */

public class StorageUtils {

    private static final String STORAGE = "com.example.sims.musicplayer.STORAGE";
    private SharedPreferences preferences;
    Context context;

    public StorageUtils(Context context){
        this.context = context;
    }

    public void storeSong(ArrayList<Song> mSongList){
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        Type type = new TypeToken<ArrayList<Song>>(){}.getType();
        Gson gson = new Gson();
        String arrayList = gson.toJson(mSongList, type);
        editor.putString("SongsList", arrayList);
        editor.apply();
    }

    public ArrayList<Song> loadSong(){
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = preferences.getString("SongsList", null);
        Type type = new TypeToken<ArrayList<Song>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public void storeSongIndex(int index){
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("SongIndex", index);
        editor.apply();
    }

    public int loadSongIndex(){
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        return preferences.getInt("SongIndex", -1);
    }

    public void clearCachedSongPlayList(){
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();
    }
}
