package com.example.groupprojectapplication.device_settings;

import java.util.ArrayList;

//Observer interface for updating when device gets new data
public interface DeviceObserver {
    public void update(int type, String info);
}
