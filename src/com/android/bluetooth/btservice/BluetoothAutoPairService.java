package com.android.bluetooth.btservice;

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

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;

@SuppressLint("NewApi")
public class BluetoothAutoPairService extends IntentService {
    private static final String TAG = "BluetoothAutoPairService";
    private static BluetoothDevice RemoteDevice;
    private String mBtMacPrefix;
    private String mBtClass;
    private BluetoothInputDevice mService = null;
    private Context mContext = null;
    private static final boolean DEBUG = true;

    private BluetoothAdapter mBluetoothAdapter;

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
            while (!isSpecialDevicePaired()) {
                pairSpecialDevice();
                while (mBluetoothAdapter.isDiscovering()) {
                    try {
                        Thread.sleep(100);
                    } catch(Exception e) {

                    }
                }
                //wait sometime for pairing and connecting
                try {
                    Thread.sleep(5000);
                } catch(Exception e) {

                }
            }
            uninitBt(this);
        }
        Log("onHandleIntent end");
    }

    private boolean initBt(Context context) {
        Log("initBt()");
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                mBluetoothAdapter.enable();
            }
        }
        if (!mBluetoothAdapter.getProfileProxy(mContext, mServiceConnection,
                BluetoothProfile.INPUT_DEVICE)) {
            Log("failed()");
        }
        IntentFilter BtFilter = new IntentFilter();
        BtFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        BtFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        registerReceiver(BluetoothReciever, BtFilter);

        IntentFilter BtDiscoveryFilter = new IntentFilter();
        BtDiscoveryFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        BtDiscoveryFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        BtDiscoveryFilter.addAction(BluetoothDevice.ACTION_FOUND);
        BtDiscoveryFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(BtDiscoveryReceiver, BtDiscoveryFilter);

        return true;
    }

    private void uninitBt(Context context) {
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        unregisterReceiver(BluetoothReciever);
        unregisterReceiver(BtDiscoveryReceiver);
    }

    private boolean getSpecialDeviceInfo() {
        mBtMacPrefix = SystemProperties.get("ro.autoconnectbt.macprefix");
        mBtClass = SystemProperties.get("ro.autoconnectbt.btclass");
        return (!mBtMacPrefix.isEmpty() && !mBtClass.isEmpty());
    }

    private boolean isSpecialDevicePaired() {
        Set<BluetoothDevice> bts = mBluetoothAdapter.getBondedDevices();
        Iterator<BluetoothDevice> iterator = bts.iterator();
        while (iterator.hasNext()) {
            BluetoothDevice bd = iterator.next();
            if (isSpecialDevice(bd)) {
                try {
                    connected(RemoteDevice);
                    Log("return true");
                    return true;
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public BluetoothAutoPairService() {
        super("HelloIntentService");
    }

    private void pairSpecialDevice() {
        if (!mBluetoothAdapter.isDiscovering()) {
            Log("the bluetooth dont't discovering,startDiscovery!");
            mBluetoothAdapter.startDiscovery();
        }
    }

    private boolean isSpecialDevice(BluetoothDevice bd) {
        return bd.getAddress().startsWith(mBtMacPrefix) &&
               bd.getBluetoothClass().toString().equals(mBtClass);
    }

    static public boolean createBond(Class btClass,BluetoothDevice device) throws Exception {
        Method createBondMethod = btClass.getMethod("createBond");
        Boolean returnValue = (Boolean) createBondMethod.invoke(device);
        return returnValue.booleanValue();
    }

    private BluetoothProfile.ServiceListener mServiceConnection =
            new BluetoothProfile.ServiceListener() {

        @Override
        public void onServiceDisconnected(int profile) {
            Log("Bluetooth service disconnected");
        }

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (DEBUG) {
                Log("Connection made to bluetooth proxy.");
            }
            mService = (BluetoothInputDevice)proxy;
        }
    };

    private void connected(BluetoothDevice device) throws Exception {
        if (mService != null && device != null) {
            Log("Connecting to target: " + device.getAddress());
            mService.connect(device);
            mService.setPriority(device, BluetoothProfile.PRIORITY_AUTO_CONNECT);
        }
    }

    private BroadcastReceiver BluetoothReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                Log("Bluetooth State has changed!");
            } else if(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(intent.getAction())) {
                Log("ACTION_SCAN_MODE_CHANGED!");
            }
        }
    };

    private BroadcastReceiver BtDiscoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(intent.getAction())) {
                Log("ACTION_DISCOVERY_STARTED!");
            } else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                Log("ACTION_DISCOVERY_FINISHED!");
            } else if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                Log("ACTION_FOUND!");
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (btDevice != null) {
                    if (isSpecialDevice(btDevice)) {
                        mBluetoothAdapter.cancelDiscovery();
                        if (btDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                            Log("Jten Name=" + btDevice.getName() + " Address=" + btDevice.
                                    getAddress() + " class=" + btDevice.
                                    getBluetoothClass().toString());
                            RemoteDevice = mBluetoothAdapter.
                                getRemoteDevice(btDevice.getAddress());
                            try {
                                Log("start bond!");
                                createBond(RemoteDevice.getClass(), RemoteDevice);
                                } catch (Exception e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                Log("ACTION_BOND_STATE_CHANGED!");
                if (RemoteDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                    Log("bond Success!");
                    try {
                        connected(RemoteDevice);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    };
}
