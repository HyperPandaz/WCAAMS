package com.example.groupprojectapplication.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.groupprojectapplication.R;
import com.example.groupprojectapplication.device_settings.DeviceObserver;
import com.example.groupprojectapplication.device_settings.DeviceSubject;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executor;

public class HomeFragment extends Fragment implements DeviceObserver {

    private Button bDeviceSettings, bRecordings, bInstructions;
    private TextView tvDeviceStatus;
    private int deviceStatusTextID = R.string.title_device_status_unknown;
    private BottomNavigationView bottomNavigationView;

    public HomeFragment(BottomNavigationView bnv) {
        bottomNavigationView = bnv;
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        System.out.println("Creating view: Home Page");
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvDeviceStatus = view.findViewById(R.id.tvHomeDevStatus);
        tvDeviceStatus.setText(deviceStatusTextID);
        //define buttons
        bDeviceSettings = view.findViewById(R.id.bDeviceSettings);
        bRecordings = view.findViewById(R.id.bRecordings);
        bInstructions = view.findViewById(R.id.bPatientInstruct);

        bDeviceSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bottomNavigationView.setSelectedItemId(R.id.navigation_device_settings);
            }
        });

        bRecordings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bottomNavigationView.setSelectedItemId(R.id.navigation_recordings);
            }
        });

        bInstructions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(v.getContext(), "Coming Soon!", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }


    @Override
    public void update(int type, String info) {
        switch (type) {
            case (DeviceSubject.CONNECTED):
                deviceStatusTextID = R.string.title_device_status_connected;
                break;
            case (DeviceSubject.DISCONNECTED):
                deviceStatusTextID = R.string.title_device_status_disconnected;
                break;
            case (DeviceSubject.NEW_DATA):
                break;
            default:
                deviceStatusTextID = R.string.title_device_status_unknown;
        }
    }
}