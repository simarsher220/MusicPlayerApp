package com.example.sims.musicplayer.SongUtils;

import android.graphics.Bitmap;

import java.io.Serializable;

/**
 * Created by sims on 20/1/17.
 */

public class Song implements Serializable {

    private long id;
    private String title;
    private String artist;
    private String data;
    private String album;
    private long albumId;

    public long getId() {
        return id;
    }

    public String getData() {
        return data;
    }

    public String getAlbum() {
        return album;
    }

    public long getAlbumId() {
        return albumId;
    }

    public Song(long id, String title, String artist, String data, String album, long albumId) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.data = data;
        this.album = album;
        this.albumId = albumId;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }
}
