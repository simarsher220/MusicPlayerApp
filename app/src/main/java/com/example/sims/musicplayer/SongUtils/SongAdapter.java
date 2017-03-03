package com.example.sims.musicplayer.SongUtils;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;
import com.example.sims.musicplayer.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import xyz.danoz.recyclerviewfastscroller.vertical.VerticalRecyclerViewFastScroller;

/**
 * Created by sims on 20/1/17.
 */

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder>{

    Context mContext;
    ArrayList<Song> mSongList;

    TextView mSongTitle;
    TextView mSongContent;
    MusicItemClickListener mListener;

    public SongAdapter(Context mContext, ArrayList<Song> mSongList, MusicItemClickListener mlistener) {
        this.mContext = mContext;
        this.mSongList = mSongList;
        this.mListener = mlistener;
    }

    public interface MusicItemClickListener{
        void onMusicItemClick(int position);
    }

    @Override
    public SongViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.song_item, parent, false);
        SongViewHolder holder = new SongViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(SongViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return mSongList.size();
    }

    public class SongViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{

        public SongViewHolder(View itemView) {
            super(itemView);
            mSongContent = (TextView) itemView.findViewById(R.id.song_item_content);
            mSongTitle = (TextView) itemView.findViewById(R.id.song_item_title);
            itemView.setOnClickListener(this);
            setIsRecyclable(false);
        }

        public void bind(int position){
            mSongTitle.setText(mSongList.get(position).getTitle());
            mSongContent.setText(mSongList.get(position).getArtist() + " | " + mSongList.get(position).getAlbum());
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            mListener.onMusicItemClick(position);
        }
    }
}
