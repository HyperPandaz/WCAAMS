package com.example.groupprojectapplication.recordings.media_player;

public interface PlayPauseSubject {
    public void attach(PlayPauseObserver playPauseObserver);
    public void detach(PlayPauseObserver playPauseObserver);
    public void notifyObservers(String id, boolean paused);
}
