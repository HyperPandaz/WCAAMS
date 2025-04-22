package com.example.groupprojectapplication;

import com.example.groupprojectapplication.recordings.Recording;
import com.example.groupprojectapplication.recordings.recording_tags.Tag;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;

public class JSONHandler {
    public static final int ON = 0; public static final int AT = 1; public static final int BEFORE = 2; public static final int AFTER = 3;
    private String filename = "info.json";
    private File storageDir;
    private JSONObject json;

    public JSONHandler(File storageDirectory) {
        storageDir = storageDirectory;

        json = loadJSON();
    }

    public boolean removeRecording(String id) {
       System.out.println("Removing recording from JSON with id: " + id);
       ArrayList<Recording> recs = getRecordingsFromJSON(0, Integer.MAX_VALUE); //use max value to get end of the array
       int pos = getPositionInJSONFromID(id);

        if (pos == -1) {
            //recording with id not found, so not removed
            return false;
        } else {
            try {
                JSONArray jsonArray = json.getJSONArray("recordings");
                jsonArray.remove(pos); //remove deleted recording
                json.put("recordings", jsonArray); //update json object

                updateFile();
            } catch (JSONException e) {
                //TODO: handle errors
                e.printStackTrace();
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        return true;
    }

    public void deleteAll() {
        System.out.println("Deleting all recording info from JSON");
        try {
            json = new JSONObject("{'recordings':[]}");
            updateFile();
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<Recording> getMostRecent(int num) {
        System.out.println("Getting " + num + " most recent recordings from JSON");
        int start = Math.max(0, getNumberOfRecordings()-num); // if less than num recordings then just get as many as possible
        return getRecordingsFromJSON(start, Integer.MAX_VALUE); // use max value to get the end of the list
    }

    public ArrayList<Recording> getMoreData(int start, int end) {
        System.out.println("Getting " + (end - start) + "more recordings from JSON");
        start = Math.max(0, start);
        return getRecordingsFromJSON(start, end);
    }

    public String addRecording(String filename) {
        String id = null;
        try {
            // calls the 'multi' version (which does the same thing but doesn't update the file) to reduce code duplication
            id = addRecordingMulti(filename);
            System.out.println("Adding " + filename + " to JSON with ID: " + id);
            updateFile();
        }  catch (IOException e) {
            e.printStackTrace();
        }
        return id;
    }

    //add recording to be used by addRecordings so that update file isn't called after every recording added
    // returns id of recording added
    private String addRecordingMulti(String filename) {
        // TODO: check id isnt present before adding
        String id = null;
        try {
            // create json object for the given recording
            JSONObject jsonR = getJSONObjFromFilename(filename);

            id = jsonR.getString("id");

            // add created object to stored json and update
            JSONArray jsonArray = json.getJSONArray("recordings");
            jsonArray.put(jsonR); //add new recording to JSON array
            json.put("recordings", jsonArray); //update stored JSON object
        } catch (JSONException e) {
            //TODO: handle errors
            e.printStackTrace();
        }
        return id;
    }

    public void addRecordings(ArrayList<String> filenames) {
        for (String f: filenames) {
            addRecordingMulti(f);
        }

        try {
            updateFile();
        } catch (IOException e) {
            //TODO: handle error
            e.printStackTrace();
        }
    }

    private JSONObject getJSONObjFromFilename(String filename) throws JSONException{
        // create json object for the given recording
        JSONObject jsonR = new JSONObject();
        jsonR.put("id", filename.split("\\.")[0]); //id is filename without .wav
        //json date is array with pos 0:YYYY, 1:MoMo, 2:DD, 3:HH, 4:MiMi, 5:SS
        int pos = filename.lastIndexOf(".");
        String fileWOExtension = filename.substring(0, pos); // remove extension from filename
        String[] split = fileWOExtension.split("-");
        JSONArray jsonDate = new JSONArray();
        for (int i = 0; i < 6; i++) {
            //add each value to the json date array as an int
            jsonDate.put(Integer.parseInt(split[i]));
        }
        jsonR.put("date", jsonDate);
        jsonR.put("recording", filename);
        jsonR.put("length", -1);
        JSONArray jsonTags = new JSONArray();
        jsonTags.put(new JSONObject("{\"tag\": \"unanalysed\", \"app\": true}"));
        jsonR.put("tags", jsonTags);

        return jsonR;
    }

    public void addTags(String recID, ArrayList<Tag> tags) {
        for (Tag t: tags) {
            addTag(recID, t);
        }
    }

    public boolean addTag(String recID, Tag t) {
        // TODO: check tag not present before adding
        System.out.println("Adding tag '" + t.getTag() + "' to recording (" + recID + ")");
        int posInJSON = getPositionInJSONFromID(recID);

        if (posInJSON == -1) {
            //recording with id not found, so tag not added
            return false;
        } else {
            try {
                //get list of recordings
                JSONArray jsonRecordings = json.getJSONArray("recordings");
                //get jsonRecording with id recID
                JSONObject jsonRecording = (JSONObject) jsonRecordings.get(posInJSON);
                JSONArray jsonTags = jsonRecording.getJSONArray("tags");

                // create JSON object from tag
                JSONObject jsonTag = new JSONObject("{\"tag\": \"" + t.getTag() + "\", \"app\": " + t.isAppGenerated() + "}");
                jsonTags.put(jsonTag); //add tag to list of tags
                jsonRecording.put("tags", jsonTags); //update recording
                jsonRecordings.remove(posInJSON); //remove old recording
                jsonRecordings.put(posInJSON, jsonRecording); //add new recording
                json.put("recordings", jsonRecordings); // update json object
                updateFile();
            } catch (JSONException e) {
                //TODO: handle errors
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    // returns -1 if recording with that id not found
    private int getPositionInJSONFromID(String recID) {
        ArrayList<Recording> recs = getRecordingsFromJSON(0, Integer.MAX_VALUE); //use max value to get end of the array
        Collections.reverse(recs); // getRecordingsFromJson gives them backwards for display
        for (int i = 0; i < recs.size(); i++) {
            if (recs.get(i).getId().equals(recID)) {
                return i;
            }
        }
        return -1;
    }

    //returns true if removed successfully, false otherwise
    public boolean deleteTag(String recID, Tag t) {
        System.out.println("Deleting tag '" + t.getTag() + "' from recording (" + recID + ")");
        int posInJSON = getPositionInJSONFromID(recID);

        if (posInJSON == -1) {
            //recording with id not found, so not removed
            return false;
        } else {
            try {
                //get list of recordings
                JSONArray jsonRecordings = json.getJSONArray("recordings");
                //get jsonRecording with id recID
                JSONObject jsonRecording = (JSONObject) jsonRecordings.get(posInJSON);
                JSONArray jsonTags = jsonRecording.getJSONArray("tags");

                //for each tag, check if it is the same as the one we're trying to remove
                for (int i = 0; i < jsonTags.length(); i++) {
                    JSONObject jsonTag = (JSONObject) jsonTags.get(i);

                    if (t.getTag().equals(jsonTag.getString("tag"))) {
                        //if found, remove tag and put object back together
                        jsonTags.remove(i);
                        jsonRecording.put("tags", jsonTags);
                        jsonRecordings.remove(posInJSON); //delete old record
                        jsonRecordings.put(posInJSON, jsonRecording); //add new one
                        json.put("recordings", jsonRecordings);
                        updateFile();
                        return true;
                    }
                }
            } catch (JSONException e) {
                //TODO: handle errors
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    private JSONObject getJSONObjFromRecording(Recording r) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", r.getId());
        LocalDateTime dateTime = r.getDatetime();
        int[] d = {dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth(), dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond()};
        JSONArray date = new JSONArray(d);
        obj.put("date", date);
        obj.put("recording", r.getFilePath());

        ArrayList<Tag> tags = r.getTags();
        //if there are no tags, write an empty array
        if (tags == null || tags.isEmpty()) {
            obj.put("tags", new JSONArray());
        } else {
            // otherwise create a json object for each tag and then save that as a json array
            JSONObject[] jsonTags = new JSONObject[tags.size()];
            for (int i = 0; i < tags.size(); i++) {
                Tag t = tags.get(i);
                JSONObject jsonTag = new JSONObject();
                jsonTag.put("tag", t.getTag());
                jsonTag.put("app", t.isAppGenerated());
                jsonTags[i] = jsonTag;
            }
            obj.put("tags", jsonTags);
        }

        return obj;
    }

    private JSONObject loadJSON() {
        System.out.println("Loading JSON info from phone...");
        JSONObject obj = null;
        File file = new File(storageDir, filename);
        try {
            if (file.createNewFile()) { //creates new file if one with the name doesn't exist already
                System.out.println("JSON file not found, creating one.");
                System.out.println("Store: " + file.getAbsolutePath());
                json = new JSONObject("{'recordings':[]}");
                updateFile();
            }

            FileInputStream fis = new FileInputStream(file);
            InputStreamReader inputStreamReader = new InputStreamReader(fis, StandardCharsets.UTF_8);
            StringBuilder stringBuilder = new StringBuilder();

            BufferedReader reader = new BufferedReader(inputStreamReader);
            String line = reader.readLine();
            while (line != null) {
                stringBuilder.append(line).append('\n');
                line = reader.readLine();
            }
            String str = stringBuilder.toString();

//            System.out.println("JSON string: " + str);

            obj = new JSONObject(str);
        } catch (IOException e) {
            //TODO:handle read errors
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return obj;
    }

    private ArrayList<Recording> getRecordingsFromJSON(int start, int end) {
        ArrayList<Recording> recs = new ArrayList<>();
        try {
            JSONArray recordingsJSON = json.getJSONArray("recordings");
            end = Math.min(end, recordingsJSON.length()-1);

            //load data from rows given in reverse since most recent recording is at the end
            for (int i = end; i >= start; i--) {
                Recording rec = getRecordingFromPositionInJSON(i);

                recs.add(rec);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return recs;
    }

    private void updateFile() throws IOException {
            File outputfile = new File(storageDir, filename);
            String file = outputfile.getAbsolutePath();

            FileOutputStream out = new FileOutputStream(file);
            String strJSON = json.toString();
            byte[] arr = strJSON.getBytes();

            out.write(arr);

            out.close();
    }

    public ArrayList<Recording> searchJSON(String text) {
        System.out.println("Searching JSON for ID or tag including \"" + text +"\"");
        ArrayList<Recording> matches = new ArrayList<>();
        try {
            JSONArray jsonRecordings = json.getJSONArray("recordings");
            for (int i = 0; i < jsonRecordings.length(); i++) {
                JSONObject jsonRecording = jsonRecordings.getJSONObject(i);

                String id = jsonRecording.getString("id");
                if (id.contains(text)) {
                    matches.add(getRecordingFromPositionInJSON(i));
                    continue;
                }

                // available info, commented out means not searching it
//                JSONArray datetime = jsonRecording.getJSONArray("date");
//                String file = recDetail.getString("recording");
//                int length = recDetail.getInt("length");
                JSONArray jsonTags = jsonRecording.getJSONArray("tags");

                for (int j = 0; j < jsonTags.length(); j++) {
                    JSONObject jsonTag = (JSONObject) jsonTags.get(j);
                    String tag = jsonTag.getString("tag");
                    if (tag.contains(text)) {
                        matches.add(getRecordingFromPositionInJSON(i));
                        break;
                    }
                }

            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println(matches.size() + " matches found");
        return matches;
    }

    public ArrayList<Recording> filterJSON(LocalDate date, int dFilterType, LocalTime time, int tFilterType) {
        ArrayList<Recording> matches = new ArrayList<>();
        try {
            JSONArray jsonRecordings = json.getJSONArray("recordings");
            System.out.println("Filtering JSON...");
            for (int i = 0; i < jsonRecordings.length(); i++) {
                JSONObject jsonRecording = jsonRecordings.getJSONObject(i);
                JSONArray jsonDatetime = jsonRecording.getJSONArray("date");
                //json date is array with pos 0:YYYY, 1:MoMo, 2:DD, 3:HH, 4:MiMi, 5:SS
                LocalDateTime datetime = LocalDateTime.of((int) jsonDatetime.get(0), (int) jsonDatetime.get(1), (int) jsonDatetime.get(2), (int) jsonDatetime.get(3), (int) jsonDatetime.get(4), (int) jsonDatetime.get(5));
                if (date != null) {
                    switch (dFilterType) {
                        case ON:
                            if (!datetime.toLocalDate().isEqual(date)) {
                                continue; // if condition not met then skip to next
                            }
                            break;
                        case BEFORE:
                            if (!datetime.toLocalDate().isBefore(date)) {
                                continue; // if condition not met then skip to next
                            }
                            break;
                        case AFTER:
                            if (!datetime.toLocalDate().isAfter(date)) {
                                continue; // if condition not met then skip to next
                            }
                            break;
                    }
                    System.out.print(date.toString());
                }

                if (time != null) {
                    switch (tFilterType) {
                        case AT:
                            LocalTime recTime = datetime.toLocalTime();
                            if (!(recTime.getHour()==time.getHour()&&recTime.getMinute()== time.getMinute())) {
                                continue; // if condition not met then skip to next
                            }
                            break;
                        case BEFORE:
                            if (!datetime.toLocalTime().isBefore(time)) {
                                continue; // if condition not met then skip to next
                            }
                            break;
                        case AFTER:
                            if (!datetime.toLocalTime().isAfter(time)) {
                                continue; // if condition not met then skip to next
                            }
                            break;
                    }
                    System.out.println(time.toString());
                }
                //if reach here then passed filtering
                matches.add(getRecordingFromPositionInJSON(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println(matches.size() + "matches found");
        return matches;
    }

    public Recording getRecordingFromPositionInJSON(int pos) {
        try {
            JSONArray jsonRecordings = json.getJSONArray("recordings");
            JSONObject jsonRecording = jsonRecordings.getJSONObject(pos);

            String id = jsonRecording.getString("id");
            JSONArray datetime = jsonRecording.getJSONArray("date");
            String file = jsonRecording.getString("recording");
            JSONArray jsonTags = jsonRecording.getJSONArray("tags");

            //json date is array with pos 0:YYYY, 1:MoMo, 2:DD, 3:HH, 4:MiMi, 5:SS
            LocalDateTime dt = LocalDateTime.of((int) datetime.get(0), (int) datetime.get(1), (int) datetime.get(2), (int) datetime.get(3), (int) datetime.get(4), (int) datetime.get(5));

            ArrayList<Tag> tags = new ArrayList<>();
            for (int j = 0; j < jsonTags.length(); j++) {
                JSONObject jsonTag = (JSONObject) jsonTags.get(j);
                String name = jsonTag.getString("tag");
                boolean appGenerated = jsonTag.getBoolean("app");
                tags.add(new Tag(name, appGenerated));
            }

            return new Recording(file, dt, id, tags);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getNumberOfRecordings() {
        try {
            JSONArray recordingsJSON = json.getJSONArray("recordings");
            return recordingsJSON.length();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
