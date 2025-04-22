package com.example.groupprojectapplication.recordings;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.groupprojectapplication.FileHandler;
import com.example.groupprojectapplication.JSONHandler;
import com.example.groupprojectapplication.R;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;


public class RecordingsFragment extends Fragment implements MenuProvider {
    private RecyclerView recyclerView;
    private RecordingListViewAdapter recordingListViewAdapter;
    boolean isLoading = false;
    boolean isFiltering = false;

    private Button bTest; //TODO: remove after testing
    private JSONHandler jsonHandler;
    private FileHandler fileHandler;
    private File storageDir;
    private LinearLayout filterGroupLayout = null;
    private LocalDate filterDatePicked = null;
    private LocalTime filterTimePicked = null;
    private int lastPosLoaded; // keeps track of the position of the last recording that was loaded from the json
    private ConstraintLayout graphLayout;
    private ImageView ivGraph;

    public RecordingsFragment(JSONHandler jh, FileHandler afh, File sd) {
        jsonHandler = jh;
        fileHandler = afh;
        storageDir = sd;
    }


    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        System.out.println("Creating view: Recordings Page");
        View view = inflater.inflate(R.layout.fragment_recordings, container, false);

        saveFileSetup(view); //TODO: remove after testing

        graphLayout = view.findViewById(R.id.graph_layout);
        ivGraph = view.findViewById(R.id.ivGraph);
        graphLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("Hide waveform image");
                graphLayout.setVisibility(View.INVISIBLE);
                ivGraph.setImageResource(R.drawable.baseline_error_24);
            }
        });

        filterGroupLayout = view.findViewById(R.id.filter_group);

        return view;
    }



    //TODO: remove after testing
    private void saveFileSetup(View view) {
        bTest = view.findViewById(R.id.bTesting);

        bTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "Doing stuff", Toast.LENGTH_SHORT).show();

                //copies file from 'raw' resource directory onto the device storage
                //delete after testing but will be used as reference for saving recordings when they're synced from device
                InputStream in = getResources().openRawResource(R.raw.test);
                fileHandler.saveFile(in, "info.json");

                in = getResources().openRawResource(R.raw.input);
                fileHandler.saveFile(in, "2025-03-14-16-36-00.wav");

                InputStream in1 = getResources().openRawResource(R.raw.audio1);
                fileHandler.saveFile(in1, "audio1.wav");
                in1 = getResources().openRawResource(R.raw.audio2);
                fileHandler.saveFile(in1, "audio2.wav");
                in1 = getResources().openRawResource(R.raw.audio3);
                fileHandler.saveFile(in1, "audio3.wav");
                in1 = getResources().openRawResource(R.raw.audio4);
                fileHandler.saveFile(in1, "audio4.wav");
                in1 = getResources().openRawResource(R.raw.audio5);
                fileHandler.saveFile(in1, "audio5.wav");



                //testing adding a recording to see if it updates the json
//                Recording r = new Recording("audio2.wav", LocalDateTime.now(), 2, null, new ArrayList<String>(), -1);
//                jsonHandler.addRecording(r);
            }
        });


    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceBundle) {
        super.onViewCreated(view, savedInstanceBundle);

        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        // newest values stored at end so get last 15 items from json
        recordingListViewAdapter = new RecordingListViewAdapter(getMostRecent(15), jsonHandler, fileHandler, storageDir, graphLayout, ivGraph); //load in first 15 (visible) recordings
        recyclerView = view.findViewById(R.id.recordings_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(recordingListViewAdapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager llm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (!isLoading) {
                    if (llm != null && llm.findLastCompletelyVisibleItemPosition() == recordingListViewAdapter.getItemCount()-1) { //if not loading and at the end of the view
                        int i = llm.findLastCompletelyVisibleItemPosition();
                        //get more data
                        isLoading = true;
                        //will wait to update until recyclerview is ready
                        recyclerView.post(new Runnable() {
                            @Override
                            public void run() {
                                System.out.println("Getting more recordings to display");
                                recordingListViewAdapter.addRecordingsToView(getMoreData(5)); //load 5 more items starting from current position and working backwards since most recent recording is stored at the end
                            }
                        });
                    }
                    isLoading = false;
                }
            }
        });
    }

    @Override
    public void onCreateMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.recording_page_menu, menu);

        setupSearchOption(menu);
        setupDeleteAllOption(menu);
        setupDownloadAllOption(menu);
        setupFilterOption(menu);
        setupRefreshOption(menu);
    }

    private void setupSearchOption(Menu menu) {
        // set up search
        MenuItem searchItem = menu.findItem(R.id.option_icon_search);

        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("Start searching");
                isLoading = true; //stop loading data when searching
            }
        });
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                System.out.println("Stop searching");
                isLoading = false; //start loading data again when done searching
                return false;
            }
        });
        // set listener for when text is added
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // when text is submitted
                if (query.isEmpty()) {
                    recordingListViewAdapter.displaySearchResults(null);
                } else {
                    recordingListViewAdapter.displaySearchResults(jsonHandler.searchJSON(query));
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // when something is typed search already loaded recordings
                recordingListViewAdapter.searchLoadedList(newText);
                return false;
            }
        });
    }

    private void setupDeleteAllOption(Menu menu) {
        MenuItem deleteAllItem = menu.findItem(R.id.option_delete_all);
        deleteAllItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(@NonNull MenuItem item) {
                System.out.println("Show delete all confirmation");
                showDeleteAllConfirmation();
                return true;
            }
        });
    }

    private void setupRefreshOption(Menu menu) {
        MenuItem refreshItem = menu.findItem(R.id.option_refresh);
        refreshItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(@NonNull MenuItem item) {
                System.out.println("Refreshing...");
                // load 15 most recent from json
                ArrayList<Recording> recordings = getMostRecent(15);
                // update last position counter
                lastPosLoaded = Math.max(0, jsonHandler.getNumberOfRecordings() - 15);
                // refresh the view
                recordingListViewAdapter.refresh(recordings);

                LinearLayoutManager llm = (LinearLayoutManager) recyclerView.getLayoutManager();
                llm.scrollToPositionWithOffset(0, 0); // ensure recordings list scrolls to top when done
                return true;
            }
        });
    }

    private void setupDownloadAllOption(Menu menu) {
        MenuItem downloadAllItem = menu.findItem(R.id.option_icon_download);
        downloadAllItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(@NonNull MenuItem item) {
                Toast.makeText(getContext(), "Coming soon!", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    private void setupFilterOption(Menu menu) {
        MenuItem filterItem = menu.findItem(R.id.option_icon_filter);
        filterItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            Button bFilter, bSetTime, bSetDate;
            RadioGroup rgDate, rgTime;
            TextView tvDateDisplay, tvTimeDisplay;
            DatePickerFragment datePicker;
            TimePickerFragment timePicker;
            FrameLayout outsideFilter;
            ConstraintLayout filterLayout;

            @Override
            public boolean onMenuItemClick(@NonNull MenuItem item) {
                if (filterGroupLayout.getVisibility()==View.VISIBLE) { // if showing then hide
                    System.out.println("Hide filter menu");
                    filterGroupLayout.setVisibility(View.INVISIBLE);
                } else {
                    System.out.println("Show filter menu");
                    filterGroupLayout.setVisibility(View.VISIBLE); //after all setup, make view visible

                    filterLayout = filterGroupLayout.findViewById(R.id.filter_layout);
                    filterLayout.setElevation(50);
                    outsideFilter = filterGroupLayout.findViewById(R.id.filter_mask);

                    bFilter = filterLayout.findViewById(R.id.bFilter);
                    bSetDate = filterLayout.findViewById(R.id.bSetFilterDate);
                    bSetTime = filterLayout.findViewById(R.id.bSetFilterTime);
                    rgDate = filterLayout.findViewById(R.id.rgDate);
                    rgTime = filterLayout.findViewById(R.id.rgTime);
                    tvDateDisplay = filterLayout.findViewById(R.id.tvDateDisplay);
                    tvTimeDisplay = filterLayout.findViewById(R.id.tvTimeDisplay);

                    bSetDate.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            datePicker = new DatePickerFragment(tvDateDisplay);
                            datePicker.show(getParentFragmentManager(), "datePicker");
                        }
                    });

                    bSetTime.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            timePicker = new TimePickerFragment(tvTimeDisplay);
                            timePicker.show(getParentFragmentManager(), "timePicker");
                        }
                    });

                    bFilter.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (isFiltering) {
                                System.out.println("Stop filtering");
                                bFilter.setText(R.string.title_filter);
                                isFiltering = false;
                                recordingListViewAdapter.displaySearchResults(null); //update listview to end filtering
                                isLoading = false; //done filtering, allow infinity scroll again
                            } else {
                                System.out.println("Start filtering");
                                boolean filteringDate = rgDate.getCheckedRadioButtonId() != R.id.rbNoneDate;
                                boolean filteringTime = rgTime.getCheckedRadioButtonId() != R.id.rbNoneTime;
                                boolean dateSet = isDateTimeSet((String) tvDateDisplay.getText());
                                boolean timeSet = isDateTimeSet((String) tvTimeDisplay.getText());


                                int dFilterType = -1;
                                int tFilterType = -1;

                                //if date is being filtered and a date has been set then get date
                                if (filteringDate) {
                                    if (dateSet) {
                                        filterDatePicked = datePicker.getDate();
                                        switch (rgDate.getCheckedRadioButtonId()) {
                                            case R.id.rbOnDate:
                                                dFilterType = JSONHandler.ON;
                                                break;
                                            case R.id.rbBeforeDate:
                                                dFilterType = JSONHandler.BEFORE;
                                                break;
                                            case R.id.rbAfterDate:
                                                dFilterType = JSONHandler.AFTER;
                                                break;
                                        }
                                    } else {
                                        Toast.makeText(getContext(), "Please set date", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                }
                                // if time is being filtered and a time has been set then get time
                                if (filteringTime) {
                                    if (timeSet) {
                                        filterTimePicked = timePicker.getTime();
                                        switch (rgTime.getCheckedRadioButtonId()) {
                                            case R.id.rbAtTime:
                                                tFilterType = JSONHandler.AT;
                                                break;
                                            case R.id.rbBeforeTime:
                                                tFilterType = JSONHandler.BEFORE;
                                                break;
                                            case R.id.rbAfterTime:
                                                tFilterType = JSONHandler.AFTER;
                                                break;
                                        }
                                    } else {
                                        Toast.makeText(getContext(), "Please set time", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                }

                                if (filteringDate || filteringTime) {
                                    ArrayList<Recording> results = jsonHandler.filterJSON(filterDatePicked, dFilterType, filterTimePicked, tFilterType);
                                    recordingListViewAdapter.displaySearchResults(results);
                                    //try filter, if nothing found display a message
                                    if (results.isEmpty()) {
                                        Toast.makeText(getContext(), "No results found", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    return; // if not filtering anything then don't update button text/values
                                }
                                isFiltering = true;
                                isLoading = true; // disable infinity scroll when filtered
                                bFilter.setText(R.string.title_clear_filter);
                            }
                        }
                    });

                    // close filter view if click outside
                    outsideFilter.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            System.out.println("Hide filter menu");
                            filterGroupLayout.setVisibility(View.INVISIBLE);
                        }
                    });
                }

                return true;
            }
        });
    }

    private boolean isDateTimeSet(String text) {
        if (text.contains("-")) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onMenuItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.option_sort_date_asc:
                recordingListViewAdapter.sortByDateTimeAsc();
                break;
            case R.id.option_sort_date_desc:
                recordingListViewAdapter.sortByDateTimeDesc();
                break;
        }
        return false;
    }

    private void showDeleteAllConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage("Permanently delete all recordings?");
        builder.setTitle("Alert !");

        builder.setPositiveButton("Yes", (DialogInterface.OnClickListener) (dialog, which) -> {
            Toast.makeText(getContext(), "Deleting all stored recordings.", Toast.LENGTH_SHORT).show();
            // delete all audio files
            for (int i = 0; i < jsonHandler.getNumberOfRecordings(); i++) {
                Recording r = jsonHandler.getRecordingFromPositionInJSON(i);
                fileHandler.deleteFile(r.getFilePath());
            }
            jsonHandler.deleteAll(); // delete info from json
            recordingListViewAdapter.removeAllRecordings(); // remove from view
        });

        builder.setNegativeButton("No", (DialogInterface.OnClickListener) (dialog, which) -> {
            // If user click no then dialog box is canceled.
            dialog.cancel();
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    //called initially to load in data
    private ArrayList<Recording> getMostRecent(int num) {
        lastPosLoaded = Math.max(0, jsonHandler.getNumberOfRecordings() - num); // position of the last loaded recording is total number -1 (because start at 0) - num because we loaded 'num' recordings

        return jsonHandler.getMostRecent(num);
    }

    //called any time loading data after initialisation (shows loading icon at bottom)
    // this method will not show newly added recordings, they will have to added by the 'refresh' button
    private ArrayList<Recording> getMoreData(int num) {
        //show loading item - commented out because it loads fast enough that you never see it
//        recordingListViewAdapter.addRecording(null); //add null to list to show loading symbol
//        recordingListViewAdapter.notifyItemInserted(recordingListViewAdapter.getItemCount()-1);
//        recordingListViewAdapter.removeLoadingItemFromView();

        ArrayList<Recording> recordings = new ArrayList<>();
        if (lastPosLoaded > 0) { // only try to load when there's more to load
            recordings = jsonHandler.getMoreData(lastPosLoaded-num, lastPosLoaded-1);
            lastPosLoaded = Math.max(0, lastPosLoaded-num); // position of the last loaded recording is last -1 (because load next) - num because we loaded 'num' recordings
        }
        return recordings;
    }



}