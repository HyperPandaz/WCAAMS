package com.example.groupprojectapplication.device_settings;

import com.example.groupprojectapplication.FileHandler;

import static android.content.Context.BLUETOOTH_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class DeviceController extends AppCompatActivity implements DeviceSubject, ActivityCompat.OnRequestPermissionsResultCallback {
    private static final List<String> VALID_MODES = Arrays.asList(new String[]{"Off", "Standby", "Low", "Continuous", "Demo"});
    private FileHandler fileHandler;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCallback gattCallback;
    private ScanCallback scanCallback;
    private final Handler handler;
    private final Context context;
    private final Activity activity;

    //OFFLINE = 0
    //RECEIVING = 1
    private int dataStatus = 0;
    private boolean connected = false;
    private boolean writeBusy = false;
    private int mode = 2;

    private String fileName;
    static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    static String UUID_HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    static String UUID_OBJECT_TRANSFER_SERVICE = "00001825-0000-1000-8000-00805f9b34fb";
    static String UUID_OBJECT_DATA = "00002ac3-0000-1000-8000-00805f9b34fb";
    static String GATT_TIME_SYNC_UUID = "029115bf-2e1e-ee90-594c-5616668e826c";
    static String GATT_TIME_UPDATE_UUID = "4e5308b0-9e95-d1b3-1d4d-2fffe268dca7";
    static String GATT_MODE_UUID = "ceb70b6d-94d7-c897-5147-2d1174a7f323";
    static String GATT_MODE_UPDATE_UUID = "cb8cfe21-41a2-019c-104f-74d30f079738";
    private ByteArrayOutputStream data = new ByteArrayOutputStream();
    private ArrayList<DeviceObserver> observers = new ArrayList<>();

    public DeviceController(Context context, Activity activity, FileHandler afh) {
        this.fileHandler = afh;
        this.context = context;
        this.activity = activity;
        handler = new Handler();


        init();

    }

    public void init(){
        System.out.println("in init");
        // Initialises all modules needed for BLE communication
        if(!checkPermissions()){
            return;
        }
        BluetoothAdapter adapter = ((BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        bluetoothLeScanner = adapter.getBluetoothLeScanner();
        initBluetoothScanCallback();
        initGattCallback();

        // Scan for device
        startScan();
    }

    // Initialises all modules needed for BLE communication

    private boolean checkPermissions() {
        int allpermissions = 0;
        ArrayList<String> temp = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            System.out.println("Bluetooth: FINE_LOCATION not granted");
            temp.add("android.permission.ACCESS_FINE_LOCATION");
            allpermissions += 1;
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                temp.add("android.permission.BLUETOOTH_SCAN");
                allpermissions += 2;
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                temp.add("android.permission.BLUETOOTH_CONNECT");
                allpermissions += 4;

            }
        }
        switch (allpermissions){
            case 1:
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                return false;
            case 2:
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 1);
                return false;
            case 3:
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN}, 1);
                return false;
            case 4:
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
                return false;
            case 5:
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT}, 1);
                return false;
            case 6:
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
                return false;
            case 7:
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
                return false;
            default:
                return true;
        }
    }


    @SuppressLint("MissingPermission")
    private void startScan() {
        System.out.println("Bluetooth: Starting scan");
        bluetoothLeScanner.startScan(scanCallback);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, 10000);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        System.out.println("Bluetooth: Stop scan");
        bluetoothLeScanner.stopScan(scanCallback);
    }

    private void initBluetoothScanCallback() {
        scanCallback = new ScanCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
//                System.out.println(result);
                BluetoothDevice bleDevice = result.getDevice();
                @SuppressLint("MissingPermission") String deviceName = bleDevice.getName();
                if (deviceName != null && deviceName.equals("blehr_sensor_1.0") && bluetoothGatt == null) {
                    stopScan();
                    System.out.println("Bluetooth: Connecting Device");
                    bluetoothGatt = bleDevice.connectGatt(context, false, gattCallback); //Triggers onConnectionStateChange()
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                System.out.println("Bluetooth: Scan failed with error code: " + errorCode);
                super.onScanFailed(errorCode);
            }
        };

    }

    private void initGattCallback() {
        gattCallback = new BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                System.out.println("Bluetooth: Gatt state change, status: " + status + ", new state: " + newState);
                if (newState == 2) { // Connected
                    System.out.print(" (connected)");
                    connected = true;
                    notifyObservers(CONNECTED, null);
                    System.out.println("Bluetooth: Discovering services");
                    bluetoothGatt.discoverServices();  // Triggers onServicesDiscovered()
                } else if (newState == 0) { //Disconnected
                    System.out.print(" (disconnected)");
                    connected = false;
                    notifyObservers(DISCONNECTED, null);
                    bluetoothGatt = null;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                System.out.println("Bluetooth: Gatt service discovery, status: " + status);
                if (status == 0) { //SUCCESS
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                                connectService();
                        }
                    }, 2);
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                byte[] value = characteristic.getValue();
                if (dataStatus == 0) {
                    fileName = characteristic.getStringValue(0);
                    System.out.println("Bluetooth: Start receiving file: " + Arrays.toString(value));
                    dataStatus = 1;
                    return;
                }

                if (value.length == 1) {
                    System.out.println("Bluetooth: file transfer finished");
                    InputStream inputStream = new ByteArrayInputStream(data.toByteArray());
                    //save to correct place
                    fileHandler.saveFile(inputStream, fileName);
                    // update observers, which will update json and call ml model
                    notifyObservers(NEW_DATA, fileName);

                    data.reset();
                    dataStatus = 0;
                    return;
                }
                try {
                    data.write(value);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onCharacteristicChanged (BluetoothGatt gatt,BluetoothGattCharacteristic characteristic, byte[] value){
                if (dataStatus == 0) {
                    fileName = characteristic.getStringValue(0);
                    System.out.println("Bluetooth: Start receiving file:  " + Arrays.toString(value));
                    dataStatus = 1;
                    return;
                }

                if (value.length == 1) {
                    System.out.println("Bluetooth: file transfer finished");
                    InputStream inputStream = new ByteArrayInputStream(data.toByteArray());
                    //save to correct place
                    fileHandler.saveFile(inputStream, fileName);
                    // update observers, which will update json and call ml model
                    notifyObservers(NEW_DATA, fileName);

                    data.reset();
                    dataStatus = 0;
                    return;
                }
                try {
                    data.write(value);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onCharacteristicRead (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status){
                //TODO
                System.out.println("Bluetooth: read " + value.length);
            }
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                System.out.println("Bluetooth: write");
                writeBusy = false;
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
            }
        };
    }

    @SuppressLint("MissingPermission")
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        System.out.println("Bluetooth: set notification");
        if (bluetoothGatt == null) {
            System.out.println("Bluetooth: BluetoothGatt not initialized");
            return;
        }
        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid().toString())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        }

        if (UUID_OBJECT_DATA.equals(characteristic.getUuid().toString())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        }
    }

    @SuppressLint("MissingPermission")
    private void connectService() {
        System.out.println("Bluetooth: device connecting...");

        byte[] time = Long.toString(System.currentTimeMillis()).getBytes();
        BluetoothGattService serv = bluetoothGatt.getService(UUID.fromString(GATT_TIME_SYNC_UUID));
        BluetoothGattCharacteristic chara = serv.getCharacteristic(UUID.fromString(GATT_TIME_UPDATE_UUID));

        chara.setValue(time);
        chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        writeBusy = true;
        bluetoothGatt.writeCharacteristic(chara);

        while(writeBusy){
            System.out.print("");
        }
        serv = bluetoothGatt.getService(UUID.fromString(UUID_OBJECT_TRANSFER_SERVICE));
        chara = serv.getCharacteristic(UUID.fromString(UUID_OBJECT_DATA));
        setCharacteristicNotification(chara, true);

        System.out.println("Bluetooth: device connect finish");
    }

    //    returns battery level or -1 if not connected
    public int getBattery() {
        if (connected) {
            return 70;
        }
        return -1;
    }


    public String getMode() {
        return VALID_MODES.get(mode);
    }


    @SuppressLint("MissingPermission")
    public boolean setMode(String newMode) {
        if (VALID_MODES.contains(newMode) && connected) {
            System.out.println("Bluetooth: setting mode: " + newMode);
            BluetoothGattService serv = bluetoothGatt.getService(UUID.fromString(GATT_MODE_UUID));
            BluetoothGattCharacteristic chara = serv.getCharacteristic(UUID.fromString(GATT_MODE_UPDATE_UUID));
            byte[] temp = {(byte) VALID_MODES.indexOf(newMode)};

            chara.setValue(temp);
            chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            bluetoothGatt.writeCharacteristic(chara);

            mode = VALID_MODES.indexOf(newMode);
            return true;
        }
        return false;
    }

    public boolean isConnected(){
//        System.out.println(connected);
        return connected;
    }
    public void connect() {
        startScan();
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        bluetoothGatt.disconnect();
    }

    public void sync() {
        //TODO:implement requesting data
//        device.requestData();
    }

    @Override
    public void attach(DeviceObserver observer) {
        observers.add(observer);
    }

    @Override
    public boolean detach(DeviceObserver observer) {
        return observers.remove(observer);
    }

    @Override
    public void notifyObservers(int type, String info) {
        System.out.println("Notify DeviceObservers of change: " + type);
        for (DeviceObserver o : observers) {
            o.update(type, info);
        }
    }
}
