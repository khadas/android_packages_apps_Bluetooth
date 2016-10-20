package com.android.bluetooth.hid;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass.Device;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothInputDevice;
import android.bluetooth.BluetoothProfile;
import android.content.IntentFilter;
import android.os.SystemProperties;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;

@SuppressLint("NewApi")
public class BluetoothAutoPairService extends IntentService {
    private static final String TAG = "BluetoothAutoPairService";
    private static BluetoothDevice RemoteDevice;
    private String mBtMacPrefix;
    private String mBtClass;
    private String mBtCallback;
    private BluetoothInputDevice mService = null;
    private Context mContext = null;
    private static final boolean DEBUG = false;
    private BluetoothAdapter mBluetoothAdapter = null;
    private boolean scanState = false;
    private boolean scanFlag = false;
    private static Timer timer = null;
    static final long SNAPSHOT_INTERVAL = 30 * 1000;
    private void Log(String msg) {
        if (DEBUG) {
            Log.i(TAG, msg);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log("onHandleIntent begin");
        if (!getSpecialDeviceInfo()) {
            Log("getSpecialDeviceInfo fail!");
            return;
        }
        if (initBt(this)) {
            try {
                Thread.sleep(5000);
            } catch(Exception e) {
              e.printStackTrace();
            }
            timer = new Timer();
            timer.schedule(new TimerTask() {
            public void run() {
                try{
                   scanState=true;
                }catch(Exception e) {
                   e.printStackTrace();
                }
            }
            }, SNAPSHOT_INTERVAL, SNAPSHOT_INTERVAL );
            if (isSpecialDevicePaired())  {
                Log("Hasfound SpecialDevicePaired!");
            }
            while (!scanState) {
                if (!mBluetoothAdapter.isDiscovering()) {
                    mBluetoothAdapter.startLeScan(mLeScanCallback);
                }
            }
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            Log("Cancel Scan!");
            if ( scanFlag == true ) {
                try {
                    Thread.sleep(5000);
                    connected(RemoteDevice);
                } catch(Exception e) {}
            }
            Log("Exit Services...!");
        }
    }
    private boolean getSpecialDeviceInfo() {
        mBtMacPrefix = SystemProperties.get("ro.autoconnectbt.macprefix");
        Log("getSpecialDeviceInfo mBtMacPrefix:"+mBtMacPrefix);
        mBtClass = SystemProperties.get("ro.autoconnectbt.btclass");
        Log("getSpecialDeviceInfo mBtClass:"+mBtClass);
        return (!mBtMacPrefix.isEmpty() && !mBtClass.isEmpty());
    }
    private boolean isSpecialDevice(BluetoothDevice bd) {
        //return bd.getAddress().startsWith(mBtMacPrefix) &&
        //    bd.getBluetoothClass().toString().equals(mBtClass);
        return bd.getAddress().startsWith(mBtMacPrefix);
    }
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
    new BluetoothAdapter.LeScanCallback(){
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord){
        if ( device.getName() != mBtCallback ) {
                mBtCallback = device.getName();
                Log("BluetoothDevice = " + device.getName() +
                " Address=" + device.getAddress() +
                " class=" + device.getBluetoothClass().toString());
                BluetoothDevice btDevice=device;
                if (btDevice != null) {
                    if (isSpecialDevice(btDevice)) {
                        Log("Scan result isSpecialDevice!");
                        if (btDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                            Log("Device no bond!");
                            Log("Find remoteDevice and parm: name= " + btDevice.getName() +
                            " Address=" + btDevice.getAddress() +
                            " class=" + btDevice.getBluetoothClass().toString());
                            RemoteDevice = mBluetoothAdapter.getRemoteDevice(btDevice.getAddress());
                            try {
                                Log("Device start bond...");
                                if ( createBond( RemoteDevice.getClass(), RemoteDevice ) ) {
                                    timer.cancel();
                                    scanFlag=true;
                                    scanState=true;
                                    Log("RemoteDevice bond ok!");
                                }
                                else
                                {
                                    Log("RemoteDevice bond failed!");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        else
                        {
                            Log("Device has bond");
                            scanState=true;
                        }
                    }
                }
            }
        }
    };
    private void connected(BluetoothDevice device) throws Exception {
        if ( mService != null && device != null ) {
            Log("Connecting to target: " + device.getAddress());
            if (mService.connect(device)) {
                mService.setPriority( device, BluetoothProfile.PRIORITY_AUTO_CONNECT );
                timer.cancel();
            }
            else{
                Log("connect no!");
            }
        }
        else{
            Log("mService or device no work!");
        }
    }
    static public boolean createBond(Class btClass,BluetoothDevice device) throws Exception {
        Method createBondMethod = btClass.getMethod("createBond");
        Boolean returnValue = (Boolean) createBondMethod.invoke(device);
        return returnValue.booleanValue();
    }
    private boolean initBt(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log("No bluetooth device!");
            return false;
        } else {
            Log("Bluetooth device exits!");
            if (!mBluetoothAdapter.isEnabled()) {
                mBluetoothAdapter.enable();
            }
        }
        if (!mBluetoothAdapter.getProfileProxy(mContext, mServiceConnection,
                BluetoothProfile.INPUT_DEVICE)) {
            Log("Bluetooth getProfileProxy failed!");
        }
        return true;
    }
    private BluetoothProfile.ServiceListener mServiceConnection =
            new BluetoothProfile.ServiceListener() {

        @Override
        public void onServiceDisconnected(int profile) {
            Log("Bluetooth service proxy disconnected");
        }

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (DEBUG) {
                Log("Bluetooth service proxy connected");
            }
            mService = (BluetoothInputDevice)proxy;
        }
    };

    public BluetoothAutoPairService() {
        super("HelloIntentService");
    }
    private boolean isSpecialDevicePaired() {
        Set<BluetoothDevice> bts = mBluetoothAdapter.getBondedDevices();
        Iterator<BluetoothDevice> iterator = bts.iterator();
        while (iterator.hasNext()) {
            BluetoothDevice bd = iterator.next();
            if (isSpecialDevice(bd)) {
            Log("RemoteDevice has bond: name=" + bd.getName() +
                " Address=" + bd.getAddress() +
                " class=" + bd.getBluetoothClass().toString());
                try {
                    timer.cancel();
                    scanState=true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }
        }
        return false;
    }
}
