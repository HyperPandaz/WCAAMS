package com.example.groupprojectapplication.recordings.recording_tags;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.groupprojectapplication.JSONHandler;
import com.example.groupprojectapplication.R;
import com.example.groupprojectapplication.recordings.Recording;

import java.util.ArrayList;
import java.util.Arrays;

public class TagListViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private JSONHandler jsonHandler;
    private Recording recording;
    private static final ArrayList<String> VALID_USER_TAGS = new ArrayList<>(Arrays.asList(
                                                            new String[] {"resting", "standing", "walking", "exercising"}));

    private ArrayList<String> unusedTags = new ArrayList<>(VALID_USER_TAGS);

    public TagListViewAdapter(Recording r, JSONHandler jh) {
        jsonHandler = jh;
        recording = r;

        ArrayList<String> usedTags = getTagsAsString();
        unusedTags.removeAll(usedTags);
    }

    public ArrayList<String> getUnusedTags() { return unusedTags; }

    private ArrayList<String> getTagsAsString() {
        ArrayList<Tag> ts = getTags();
        ArrayList<String> strings = new ArrayList<>();
        for (Tag t: ts) {
            strings.add(t.getTag());
        }
        return strings;
    }

    private ArrayList<Tag> getTags() {
        return recording.getTags();
    }

    @Override
    public int getItemViewType(int position) {
        return getTags().get(position).getViewType();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == Tag.VIEW_TYPE_APP_GENERATED) {
            return new AppGeneratedTagViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.tag_app_added, parent, false));
        } else if (viewType == Tag.VIEW_TYPE_USER_GENERATED) {
            return new UserGeneratedTagViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.tag_user_added, parent, false));
        }
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AppGeneratedTagViewHolder) {
            Tag t = getTags().get(position);
            ((AppGeneratedTagViewHolder) holder).tvTagName.setText(t.getTag());
        } else if (holder instanceof UserGeneratedTagViewHolder) {
            Tag t = getTags().get(position);
            ((UserGeneratedTagViewHolder) holder).tvTagName.setText(t.getTag());
        }
    }

    @Override
    public int getItemCount() {
        return getTags().size();
    }

    protected void deleteTagByAdapterPosition(int position) {
        Tag removed = getTags().remove(position);
        recording.removeTag(removed);
        notifyItemRemoved(position);

        jsonHandler.deleteTag(recording.getId(), removed);

        unusedTags.add(removed.getTag()); //add tag back to list available to add
    }

    public void addUserTag(String s) {
        Tag t = new Tag(s, false);
        // add tag to recording
        recording.addTag(t);
        //remove from list of valid tags
        unusedTags.remove(s);
        notifyItemInserted(recording.getTags().size());
        // update JSON file
        jsonHandler.addTag(recording.getId(), t);
    }

    private class AppGeneratedTagViewHolder extends RecyclerView.ViewHolder {
        private TextView tvTagName;
        private ImageButton bDeleteTag;
        public AppGeneratedTagViewHolder(View itemView) {
            super(itemView);
            tvTagName = itemView.findViewById(R.id.tvTagNameApp);
            bDeleteTag = itemView.findViewById(R.id.bDeleteTagApp);

            bDeleteTag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(v.getContext(), "Cannot delete application tags", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private class UserGeneratedTagViewHolder extends RecyclerView.ViewHolder {
        private TextView tvTagName;
        private ImageButton bDeleteTag;
        public UserGeneratedTagViewHolder(View itemView) {
            super(itemView);
            tvTagName = itemView.findViewById(R.id.tvTagNameUser);
            bDeleteTag = itemView.findViewById(R.id.bDeleteTagUser);

            bDeleteTag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteTagByAdapterPosition(getAdapterPosition());
                }
            });
        }
    }
}
