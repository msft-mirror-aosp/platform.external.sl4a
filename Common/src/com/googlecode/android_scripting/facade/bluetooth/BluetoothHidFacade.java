/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.android_scripting.facade.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;

import com.googlecode.android_scripting.BaseApplication;
import com.googlecode.android_scripting.FutureActivityTaskExecutor;
import com.googlecode.android_scripting.Log;
import com.googlecode.android_scripting.facade.EventFacade;
import com.googlecode.android_scripting.facade.FacadeManager;
import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.rpc.Rpc;
import com.googlecode.android_scripting.rpc.RpcParameter;

import java.util.List;

/*
 * Class Bluetooth HidFacade
 */
public class BluetoothHidFacade extends RpcReceiver {
    public static final ParcelUuid[] UUIDS = {
        BluetoothUuid.HID,
        BluetoothUuid.HOGP
    };

    private final Service mService;
    private final BluetoothAdapter mBluetoothAdapter;
    private final FutureActivityTaskExecutor mTaskQueue;
    private BluetoothHidInputCounterTask mInputCounterTask;

    private static boolean sIsHidReady = false;
    private static BluetoothHidHost sHidProfile = null;

    private final EventFacade mEventFacade;

    public BluetoothHidFacade(FacadeManager manager) {
        super(manager);
        mService = manager.getService();
        mTaskQueue = ((BaseApplication) mService.getApplication()).getTaskExecutor();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.getProfileProxy(mService, new HidServiceListener(),
        BluetoothProfile.HID_HOST);
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED);
        mService.registerReceiver(mHidServiceBroadcastReceiver, pkgFilter, Context.RECEIVER_EXPORTED);
        Log.d(HidServiceBroadcastReceiver.TAG + " registered");
        mEventFacade = manager.getReceiver(EventFacade.class);
    }

    class HidServiceListener implements BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            sHidProfile = (BluetoothHidHost) proxy;
            sIsHidReady = true;
        }

        @Override
        public void onServiceDisconnected(int profile) {
            sIsHidReady = false;
        }
    }

    class HidServiceBroadcastReceiver extends BroadcastReceiver {
        private static final String TAG = "HidServiceBroadcastReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG + " action=" + action);

            switch (action) {
                case BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED: {
                    int previousState = intent.getIntExtra(
                            BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
                    int state = intent.getIntExtra(
                            BluetoothProfile.EXTRA_STATE, -1);
                    Log.d("Connection state changed: "
                            + previousState + " -> " + state);
                }
                break;
                default:
                    break;
            }
        }
    }

    private final BroadcastReceiver mHidServiceBroadcastReceiver =
            new HidServiceBroadcastReceiver();

    /**
     * Connect to Hid Profile.
     * @param device - the Bluetooth Device object to connect to.
     * @return if the connection was successfull or not.
     */
    public Boolean hidConnect(BluetoothDevice device) {
        if (sHidProfile == null) return false;
        return sHidProfile.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
    }

    /**
     * Disconnect to Hid Profile.
     * @param device - the Bluetooth Device object to disconnect to.
     * @return if the disconnection was successfull or not.
     */
    public Boolean hidDisconnect(BluetoothDevice device) {
        if (sHidProfile == null) return false;
        return sHidProfile.setConnectionPolicy(
                device, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
    }

    /**
     * Is Hid profile ready.
     * @return if Hid profile is ready or not.
     */
    @Rpc(description = "Is Hid profile ready.")
    public Boolean bluetoothHidIsReady() {
        return sIsHidReady;
    }

    /**
     * Get all the devices connected through HID.
     * @return List of all the devices connected through HID.
     */
    @Rpc(description = "Get all the devices connected through HID.")
    public List<BluetoothDevice> bluetoothHidGetConnectedDevices() {
        if (!sIsHidReady) return null;
        return sHidProfile.getConnectedDevices();
    }

    /**
     * Get the connection status of a device.
     * @param deviceID - Name or MAC address of a bluetooth device.
     * @return connection status of a device.
     */
    @Rpc(description = "Get the connection status of a device.")
    public Integer bluetoothHidGetConnectionStatus(
            @RpcParameter(name = "deviceID",
                description = "Name or MAC address of a bluetooth device.")
                    String deviceID) {
        if (sHidProfile == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        List<BluetoothDevice> deviceList = sHidProfile.getConnectedDevices();
        BluetoothDevice device;
        try {
            device = BluetoothFacade.getDevice(deviceList, deviceID);
        } catch (Exception e) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return sHidProfile.getConnectionState(device);
    }

    /**
     * Sends the Set_Priority command to the given connected HID input device.
     * @param deviceID name or MAC address or the HID input device
     * @param priority priority level
     * @return True if successfully sent the command; otherwise false
     * @throws Exception error from Bluetooth HidService
     */
    @Rpc(description = "Set priority of the profile")
    public Boolean bluetoothHidSetPriority(
          @RpcParameter(name = "deviceID",
                  description = "Name or MAC address of a bluetooth device.")
                  String deviceID,
          @RpcParameter(name = "priority")
                  Integer priority) throws Exception {
        BluetoothDevice device = BluetoothFacade.getDevice(sHidProfile.getConnectedDevices(),
              deviceID);
        return sHidProfile.setConnectionPolicy(device, priority);
    }

    /**
     * Sends the Get_Priority command to the given connected HID input device.
     * @param deviceID name or MAC address or the HID input device
     * @return The value of the HID input device priority
     * @throws Exception error from Bluetooth HidService
     */
    @Rpc(description = "Get priority of the profile")
    public Integer bluetoothHidGetPriority(
          @RpcParameter(name = "deviceID",
                  description = "Name or MAC address of a bluetooth device.")
                  String deviceID) throws Exception {
        BluetoothDevice device = BluetoothFacade.getDevice(sHidProfile.getConnectedDevices(),
              deviceID);
        return sHidProfile.getConnectionPolicy(device);
    }

    /**
     * Start to monitor HID device input count
     */
    @Rpc(description = "Start keyboard/mouse input counter")
    public void bluetoothHidStartInputCounter() throws InterruptedException {
        mInputCounterTask = new BluetoothHidInputCounterTask();
        mTaskQueue.execute(mInputCounterTask);
        mInputCounterTask.getShowLatch().await();
    }

    /**
     * Stop to monitor HID device input count
     */
    @Rpc(description = "Stop keyboard/mouse input rate checker")
    public void bluetoothHidStopInputCounter() throws InterruptedException {
        if (mInputCounterTask != null) {
            mInputCounterTask.finish();
            mInputCounterTask = null;
        }
    }

    /**
     * Get HID device input rate
     * @return The value of HID device input count during the first and the last input.
     */
    @Rpc(description = "Get HID keyboard/mouse input count")
    public double bluetoothHidGetCount() {
        return mInputCounterTask.getCount();
    }

    /**
     * Test byte transfer.
     */
    @Rpc(description = "Test byte transfer.")
    public byte[] testByte() {
        byte[] bts = {0b01, 0b10, 0b11, 0b100};
        return bts;
    }

    @Override
    public void shutdown() {
        mService.unregisterReceiver(mHidServiceBroadcastReceiver);
    }
}
