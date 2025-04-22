package com.example.groupprojectapplication.device_settings;

import java.util.ArrayList;

//Subject interface for updating when device gets data
public interface DeviceSubject {
    public static int CONNECTED = 1;
    public static int DISCONNECTED = 0;
    public static int NEW_DATA = 2;
    public void attach(DeviceObserver observer);
    public boolean detach(DeviceObserver observer);

    //using the 'push' model, where the subject broadcasts the change made
    //info will be null if connection update, or contain filename if new data update
    public void notifyObservers(int type, String info);
}
