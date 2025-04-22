package com.example.groupprojectapplication.recordings.media_player;

import android.media.MediaPlayer;

import com.example.groupprojectapplication.R;

import java.io.IOException;
import java.util.ArrayList;

public class ListMediaPlayer extends MediaPlayer implements PlayPauseSubject{
    private String currentPlayingID = null;
    private ArrayList<PlayPauseObserver> observers = new ArrayList<>();

    public ListMediaPlayer() {
        super();
        setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                // on audio finish, set current playing ID back to null and reset media player
                System.out.println("MediaPlayer: audio finished");
                if (currentPlayingID != null ) {
                    notifyObservers(currentPlayingID, true);
                }
                reset();
            }
        });
    }

    public void playpause(String id, String fileToPlay) {
        //if the player isn't playing
        if (!isPlaying()) {
            //it is not paused on this audio (paused something else or not playing at all)
            if (currentPlayingID == null || !id.equals(currentPlayingID)) {
                if (currentPlayingID != null) {
                    // if playing on something else, then reset
//                    notifyObservers(currentPlayingID, true); // update view of previously playing to paused
                    reset();
                }
                setupAndStartMediaPlayer(id, fileToPlay);
            //this is paused
            } else {
                System.out.println("MediaPlayer: resume playing");
                start();
                notifyObservers(currentPlayingID, false); // update view of currently playing to playing
            }
        //if player is playing something
        } else {
            //this is playing
            if (id.equals(currentPlayingID)) {
                System.out.println("MediaPlayer: pause this");
                pause();
                notifyObservers(currentPlayingID, true); // update view of currently playing to paused
            //something else is playing
            } else {
                System.out.println("MediaPlayer: pause currently playing");
                notifyObservers(currentPlayingID, true); // update view of previously playing to paused
                reset();
                setupAndStartMediaPlayer(id, fileToPlay);
            }
        }
    }

    private void setupAndStartMediaPlayer(String id, String file) {
        try {
            setDataSource(file);
            //set on prepared listener so it starts after its prepared (not trying to before + crash)
            setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    System.out.println("MediaPlayer: play " + file);
                    currentPlayingID = id; //update media player to this audio
                    notifyObservers(currentPlayingID, false); // update view of currently playing to playing
                    start();
                }
            });
            prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reset() {
        currentPlayingID = null;
        super.reset();
    }

    @Override
    public void attach(PlayPauseObserver playPauseObserver) {
        observers.add(playPauseObserver);
    }

    @Override
    public void detach(PlayPauseObserver playPauseObserver) {
        observers.remove(playPauseObserver);
    }

    @Override
    public void notifyObservers(String id, boolean paused) {
        for (PlayPauseObserver o: observers) {
            o.update(id, paused);
        }
    }
}