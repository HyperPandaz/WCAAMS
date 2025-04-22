package com.example.groupprojectapplication.recordings.recording_tags;

public class Tag {
    String tag;
    boolean appGenerated;
    public static final int VIEW_TYPE_APP_GENERATED = 1;
    public static final int VIEW_TYPE_USER_GENERATED = 0;

    public Tag(String t, boolean appGenerated) {
        tag = t;
        this.appGenerated = appGenerated;
    }

    public String getTag() {return tag;}

    public boolean isAppGenerated() {return appGenerated;}

    public int getViewType() {
        if (appGenerated) {
            return VIEW_TYPE_APP_GENERATED;
        } else {
            return VIEW_TYPE_USER_GENERATED;
        }
    }
}
