package com.example.groupprojectapplication;

import android.os.Bundle;
import android.view.MenuItem;

import com.example.groupprojectapplication.device_settings.DeviceController;
import com.example.groupprojectapplication.device_settings.DeviceSettingsFragment;
import com.example.groupprojectapplication.home.HomeFragment;
import com.example.groupprojectapplication.machine_learning.ModelHandler;
import com.example.groupprojectapplication.recordings.RecordingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.navigation.NavigationBarView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomePage extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {
    private BottomNavigationView bottomNavigationView;
    private Toolbar toolbar;
    private HomeFragment homeFragment;
    private RecordingsFragment recFragment;
    private DeviceSettingsFragment devFragment;
    private DeviceController deviceController;
    // threads to allow processes to run in background
    ExecutorService executorService = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println("Initialising...");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(this);

        toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.title_home);


        ////////FILE STORAGE SETUP////////
        File storageDir = getExternalFilesDirs(null)[0]; //list root directory for application allowed external storage

        JSONHandler jsonHandler = new JSONHandler(storageDir);
        FileHandler fileHandler = new FileHandler(storageDir);
        ModelHandler modelHandler = new ModelHandler(executorService, getApplicationContext(), storageDir);
        // create device controller and attach relevant observers before passing to device settings fragment
        homeFragment = new HomeFragment(bottomNavigationView);
        recFragment = new RecordingsFragment(jsonHandler, fileHandler, storageDir);
        devFragment = new DeviceSettingsFragment(jsonHandler, modelHandler);

        deviceController = new DeviceController(getApplicationContext(), this, fileHandler);
        deviceController.attach(homeFragment);
        deviceController.attach(devFragment);

        devFragment.setDeviceController(deviceController);

        bottomNavigationView.setSelectedItemId(R.id.navigation_home);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        for (int g: grantResults){
            if(g!=0){
                return;
            }
        }
        deviceController.init();
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.navigation_home:
                System.out.println("Navigation item selected, going to: Home");
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.frame_layout, homeFragment)
                        .commit();
                getSupportActionBar().setTitle(R.string.title_home);
                return true;
            case R.id.navigation_recordings:
                System.out.println("Navigation item selected, going to: Recordings");
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.frame_layout, recFragment)
                        .commit();
                getSupportActionBar().setTitle(R.string.title_recordings);
                return true;
            case R.id.navigation_device_settings:
                System.out.println("Navigation item selected, going to: Device Settings");
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.frame_layout, devFragment)
                        .commit();
                getSupportActionBar().setTitle(R.string.title_device_settings);
                return true;
        }
        return false;
    }
}