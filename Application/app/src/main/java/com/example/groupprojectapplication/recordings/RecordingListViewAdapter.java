package com.example.groupprojectapplication.recordings;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.groupprojectapplication.FileHandler;
import com.example.groupprojectapplication.JSONHandler;
import com.example.groupprojectapplication.R;
import com.example.groupprojectapplication.recordings.media_player.ListMediaPlayer;
import com.example.groupprojectapplication.recordings.media_player.PlayPauseObserver;
import com.example.groupprojectapplication.recordings.recording_tags.Tag;
import com.example.groupprojectapplication.recordings.recording_tags.TagListViewAdapter;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RecordingListViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    final int VIEW_TYPE_LOADING = 0;
    final int VIEW_TYPE_ITEM = 1;
    private List<Recording> dataList;
    private List<Recording> dataListCopy = new ArrayList<>(); //used to keep all items when filtering/searching
    private File storageDir;
    protected ListMediaPlayer lMediaPlayer = new ListMediaPlayer();
    private JSONHandler jsonHandler;
    private FileHandler fileHandler;
    private ConstraintLayout graphLayout;
    private ImageView ivGraph;

    public RecordingListViewAdapter(List<Recording> dataList, JSONHandler jh, FileHandler afh, File sd, ConstraintLayout gl, ImageView iv) {
        this.dataList = dataList;
        dataListCopy.addAll(dataList);
        jsonHandler = jh;
        fileHandler = afh;
        storageDir = sd;
        graphLayout = gl;
        ivGraph = iv;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ITEM) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.recording_view_item, parent, false);

            MyViewHolder myViewHolder = new MyViewHolder(itemView, lMediaPlayer, graphLayout, ivGraph);
            lMediaPlayer.attach(myViewHolder);

            return myViewHolder;
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recording_item_loading,parent, false);
            return new LoadingHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MyViewHolder) {
            Recording rec = dataList.get(position);
            String id = rec.getId();
            ((MyViewHolder) holder).tvID.setText(id);

            LocalDateTime dt = rec.getDatetime();
            ((MyViewHolder) holder).tvDateTime.setText(dt.toString());

            ((MyViewHolder) holder).file = rec.getFilePath();
            ((MyViewHolder) holder).storageDir = storageDir;
            ((MyViewHolder) holder).id = rec.getId();
        }
    }

    @Override
    public int getItemViewType(int position) {
        //if item at position is null, return type loading, else return type item
        return dataList.get(position) == null ? VIEW_TYPE_LOADING : VIEW_TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public ArrayList<Recording> getRecordings() {return (ArrayList<Recording>) dataList;}

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }

    public void addRecording(Recording r) {
        dataList.add(r);
        dataListCopy.add(r);
        notifyItemInserted(dataList.size()-1);
    }

    public void addRecordingsToView(ArrayList<Recording> recs) {
        System.out.println("Adding new recordings to view");
        int start = dataList.size();
        dataList.addAll(recs);
        dataListCopy.addAll(recs);
        notifyItemRangeInserted(start, recs.size());
    }

    public void removeLoadingItemFromView() {
        int lastIndex = dataList.size()-1;
        if (dataList.get(lastIndex)==null) {
            dataList.remove(lastIndex);
        }
    }

    public boolean removeRecordingbyID(String id) {
        Recording toRemove = null;
        for (int i = 0; i < getItemCount(); i++) {
            if (id.equals(dataList.get(i).getId())) {
                toRemove = dataList.get(i);
                dataList.remove(toRemove);
                dataListCopy.remove(toRemove);
                notifyItemRemoved(i);

                fileHandler.deleteFile(toRemove.getFilePath());
                jsonHandler.removeRecording(id);

                return true;
            }
        }

        return false;
    }

    public void removeAllRecordings() {
        int len = dataList.size();
        dataList.clear();
        dataListCopy.clear();

        notifyItemRangeRemoved(0, len);
    }

    public void refresh(ArrayList<Recording> rs) {
        int len = dataList.size();

        dataList.addAll(rs); // add new recordings
        dataList.removeAll(dataListCopy); // remove old recordings
        dataListCopy.clear();
        dataListCopy.addAll(dataList);
        notifyItemRangeInserted(0, dataList.size()); // notify new items added
        notifyItemRangeRemoved(dataList.size(), len);
    }


    protected String getFilePath() {
        return storageDir.getAbsolutePath();
    }

    public Recording getRecordingByID(String id) {
        for (int i = 0; i < getItemCount(); i++) {
            if (id.equals(dataList.get(i).getId())) {
                return dataList.get(i);
            }
        }
        return null;
    }

    public void searchLoadedList(String text) {
        dataList.clear();
        if (text.isEmpty()) {
            System.out.println("Search done, replace items");
            // replace items back in list
            dataList.addAll(dataListCopy);
        } else {
            System.out.println("Searching loaded recordings by tag and ID for contains: " + text);
            text = text.toLowerCase();
            for (Recording r: dataListCopy) {
                // check if id
                if (r.getId().contains(text)) {
                    // TODO: keep search by id?
                    dataList.add(r);
                    continue;
                }

                //check if tag
                ArrayList<Tag> tags = r.getTags();
                for (Tag t: tags) {
                    if (t.getTag().toLowerCase().contains(text)) {
                        dataList.add(r);
                        break;
                    }
                }
            }
            dataList.add(null); // show loading icon since more recordings might be in non-loaded
            System.out.println(dataList.size() - 1 + " matches found");
        }

        notifyDataSetChanged();
    }

    public void displaySearchResults(ArrayList<Recording> recordings) {
        dataList.clear();
        if (recordings == null) {
            // replace items back in list
            dataList.addAll(dataListCopy);
        } else {
            dataList.addAll(recordings);
        }

        notifyDataSetChanged();
    }

    public void sortByDateTimeAsc() {
        System.out.println("Sort by Date/Time Ascending");
        dataList.sort((rec1, rec2) -> rec1.getDatetime().compareTo(rec2.getDatetime()));
        notifyDataSetChanged();
    }

    public void sortByDateTimeDesc() {
        System.out.println("Sort by Date/Time Descending");
        dataList.sort((rec1, rec2) -> -1*rec1.getDatetime().compareTo(rec2.getDatetime()));
        notifyDataSetChanged();
    }

    private class MyViewHolder extends RecyclerView.ViewHolder implements PlayPauseObserver {
        private ImageButton bPlay, bView;
        private TextView tvID, tvDateTime, tvShowRecordingInfo;
        private Button bOptions, bAddTag;
        public String file;
        public File storageDir;
        private ListMediaPlayer lMediaPlayer;
        private String id = null;
        private ConstraintLayout graphLayout;
        private ImageView ivGraph;

        public MyViewHolder(View view, ListMediaPlayer lmp, ConstraintLayout gl, ImageView iv) {
            super(view);
            lMediaPlayer = lmp;
            graphLayout = gl;
            ivGraph = iv;

            bPlay = view.findViewById(R.id.bPlay);
            bView = view.findViewById(R.id.bView);
            tvID = view.findViewById(R.id.tvID);
            tvDateTime = view.findViewById(R.id.tvDateTime);
            bOptions = view.findViewById(R.id.bOptions);

            bPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                   lMediaPlayer.playpause(id, storageDir.getAbsolutePath() + "/" + file);
                }
            });

            bOptions.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("Show recording options menu");
                    showRecordingPopupMenu(v);
                }
            });

            bView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("Show waveform image");
                    String imageFilename = file.substring(0, file.lastIndexOf('.'))+".jpg";
                    File imgFile = new File(storageDir, "/images/" + imageFilename);
                    if (imgFile.exists()) {
                        Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                        ivGraph.setImageBitmap(bitmap);
                    }
                    graphLayout.setVisibility(View.VISIBLE);
                }
            });

        }

        private void showRecordingPopupMenu(View v) {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    System.out.println("Recording option selected: ");
                    switch (item.getItemId()) {
                        case R.id.option_download:
                            System.out.print("Download\n");
                            Toast.makeText(v.getContext(), "Files are stored at: " + getFilePath(), Toast.LENGTH_LONG).show();
                            return true;
                        case R.id.option_manage_tags:
                            System.out.print("Manage Tags\n");
                            setupManageTagsPopup(v.getContext(), v);
                            return true;
                        case R.id.option_delete:
                            System.out.print("Delete\n");
                            Toast.makeText(v.getContext(), "Deleting...", Toast.LENGTH_SHORT).show();
                            removeRecordingbyID(id);
                            return true;
                        default:
                            return false;
                    }
                }
            });
            popup.getMenuInflater().inflate(R.menu.recording_item_menu, popup.getMenu());
            popup.show();
        }

        private void setupManageTagsPopup(Context context, View view) {
            View popupView = LayoutInflater.from(context).inflate(R.layout.manage_tags_view, null);
            //TODO: fix width and height
//            int width = RelativeLayout.LayoutParams.WRAP_CONTENT;
//            int height = RelativeLayout.LayoutParams.WRAP_CONTENT;
            int width = 750;
            int height = 900;
            final PopupWindow popupWindow = new PopupWindow(popupView, width, height, true);
            popupWindow.setElevation(50);

            Recording r = getRecordingByID(id);

            RecyclerView recyclerView = popupView.findViewById(R.id.tagRecyclerView);
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
            TagListViewAdapter tagAdapter = new TagListViewAdapter(r, jsonHandler);
            recyclerView.setAdapter(tagAdapter);

            // show the popup window
            popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);

            bAddTag = popupView.findViewById(R.id.bAddTag);
            bAddTag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("Show add tags popup");
                    showAddTagsPopup(popupView, tagAdapter);
                }
            });

            tvShowRecordingInfo = popupView.findViewById(R.id.tvShowRecordingInfo);
            tvShowRecordingInfo.setText(r.getId());

        }

        private void showAddTagsPopup(View v, TagListViewAdapter tagListViewAdapter) {
            PopupMenu popup = new PopupMenu(v.getContext(), v, Gravity.CENTER);
            for (int i = 0; i < tagListViewAdapter.getUnusedTags().size(); i++) {
                popup.getMenu().add(0,0,1,tagListViewAdapter.getUnusedTags().get(i)); //add all valid tags to menu, order 1 is to ensure '<select tag>' appears first
            }
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    String selection = item.getTitle().toString();
                    if (tagListViewAdapter.getUnusedTags().contains(selection)) { // if its a valid tag, then add it
                        tagListViewAdapter.addUserTag(selection);
                        return true;
                    }
                    //place holder selected, still close
                    return false;
                }
            });

            popup.getMenuInflater().inflate(R.menu.add_tags_menu, popup.getMenu());
            popup.show();
        }

        @Override
        public void update(String id, boolean paused) {
            if (id.equals(this.id)) { // if the thing to be updated is this
                if (paused) { // if audio has been paused/stopped then show play icon
                    bPlay.setImageResource(R.drawable.play_arrow_24);
                } else { // show pause icon
                    bPlay.setImageResource(R.drawable.pause_24);
                }
            }
        }


    }

    private class LoadingHolder extends RecyclerView.ViewHolder {
        public LoadingHolder(@NonNull View itemView) {
            super(itemView);
        }
    }



}
