package com.example.groupprojectapplication.recordings;

import com.example.groupprojectapplication.recordings.recording_tags.Tag;

import java.time.LocalDateTime;
import java.util.ArrayList;

public class Recording {
    private String filename; //type to be changed this will be the recording itself
    private LocalDateTime datetime;
    private String id;
    private ArrayList<Tag> tags;

    public Recording(String f, LocalDateTime dt, String id, ArrayList<Tag> t) {
        filename = f;
        datetime = dt;
        this.id = id;
        tags = t;
    }

    public String getFilePath() {
        return  filename;
    }

    public LocalDateTime getDatetime() {
        return datetime;
    }

    public String getId() {
        return id;
    }

    public ArrayList<Tag> getTags() {
        return tags;
    }

    public void addTag(Tag t) {
        //add tag to list, if its not already there
        if (!tags.contains(t)) {
            tags.add(t);
        }
    }

    //true if successful, false if did not contain the tag
    public boolean removeTag(Tag t) {return tags.remove(t);
    }
}
