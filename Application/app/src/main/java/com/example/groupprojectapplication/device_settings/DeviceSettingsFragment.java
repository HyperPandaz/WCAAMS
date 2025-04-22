package com.example.groupprojectapplication.device_settings;

import static androidx.core.content.ContextCompat.getExternalFilesDirs;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.groupprojectapplication.FileHandler;
import com.example.groupprojectapplication.JSONHandler;
import com.example.groupprojectapplication.R;
import com.example.groupprojectapplication.machine_learning.ModelCallback;
import com.example.groupprojectapplication.machine_learning.ModelHandler;
import com.example.groupprojectapplication.recordings.recording_tags.Tag;

import java.io.InputStream;
import java.util.ArrayList;

public class DeviceSettingsFragment extends Fragment implements DeviceObserver {
    private TextView tvBattery, tvMode, tvConnected;
    private Button bConnect, bSync;
    private Spinner sMode;

    private DeviceController deviceController;
    private static final String[] VALID_MODES = new String[]{"Standby", "Low", "Continuous", "Demo"};
    private JSONHandler jsonHandler;
    private ModelHandler modelHandler;
    private int deviceStatus = R.string.title_device_status_unknown;

    public DeviceSettingsFragment(JSONHandler jh, ModelHandler mh) {
        jsonHandler = jh;
        modelHandler = mh;
    }

    public void setDeviceController(DeviceController dc) {
        deviceController = dc;
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        System.out.println("Creating view: Device Settings Page");
        View view = inflater.inflate(R.layout.fragment_device_settings, container, false);

        tvBattery = view.findViewById(R.id.tvBattery);
        tvMode = view.findViewById(R.id.tvMode);
        tvConnected = view.findViewById(R.id.tvConnected);
        tvConnected.setText(deviceStatus);

        bConnect = view.findViewById(R.id.bConnect);
        bConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deviceController.isConnected()) {
                    deviceController.disconnect();
                } else {
                    deviceController.connect();
                }
                if (deviceController.isConnected()) { //TODO: implement this with listeners
                    tvConnected.setText("Status: Connected");
                    tvBattery.setText("Battery: " + deviceController.getBattery() + "%");
                    //set spinner mode to match somehow??
                } else {
                    tvConnected.setText("Status: Disconnected");
                    tvBattery.setText("Battery: --%");
                }
            }
        });
        bSync = view.findViewById(R.id.bSync);
        bSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                deviceController.sync();
                Toast.makeText(getContext(), "Coming soon!", Toast.LENGTH_SHORT).show();
               // TODO: remove after testing
               System.out.println("Simulate receiving new recording");
                InputStream in = getResources().openRawResource(R.raw.input);
                FileHandler a = new FileHandler(getExternalFilesDirs(getContext(), null)[0]);
                a.saveFile(in, "2025-03-14-16-36-00.wav");
                deviceController.notifyObservers(DeviceSubject.NEW_DATA, "2025-03-14-16-36-00.wav");
            }
        });

        sMode = view.findViewById(R.id.sMode);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this.getActivity(), android.R.layout.simple_spinner_dropdown_item, VALID_MODES);
        sMode.setAdapter(adapter);
        sMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @SuppressLint("NewApi")
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                deviceController.setMode(VALID_MODES[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //auto generated, not sure if I'll need
            }
        });

        return view;
    }

    @Override
    public void update(int type, String info) {
        switch (type) {
            case (DeviceSubject.CONNECTED):
                deviceStatus = R.string.title_device_status_connected;
                break;
            case (DeviceSubject.DISCONNECTED):
                 deviceStatus = R.string.title_device_status_disconnected;
                break;
            case (DeviceSubject.NEW_DATA):
                String recId = jsonHandler.addRecording(info);
                modelHandler.processRecording(info, new ModelCallback() {
                    @Override
                    public void onComplete(ArrayList<String> result) {
                        ArrayList<Tag> tags = new ArrayList<>();
                        for (String s: result) {
                            tags.add(new Tag(s, true));
                        }
                        jsonHandler.addTags(recId, tags); // add new tags
                        jsonHandler.deleteTag(recId, new Tag("unanalysed", true)); // remove unanalysed tag
                    }
                });
                break;
            default:
                deviceStatus = R.string.title_device_status_unknown;
        }
    }
}