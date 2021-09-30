/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.googlecode.android_scripting.facade.uwb;

import android.app.Service;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.uwb.RangingMeasurement;
import android.uwb.RangingReport;
import android.uwb.RangingSession;
import android.uwb.UwbAddress;
import android.uwb.UwbManager;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.googlecode.android_scripting.Log;
import com.googlecode.android_scripting.facade.EventFacade;
import com.googlecode.android_scripting.facade.FacadeManager;
import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.rpc.Rpc;
import com.googlecode.android_scripting.rpc.RpcParameter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * SL4A for UwbManager and Ranging APIs.
 */
public class UwbManagerFacade extends RpcReceiver {

    private static final String TAG = "UwbManagerFacade: ";
    private final Service mService;
    private final Context mContext;
    private final UwbManager mUwbManager;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final EventFacade mEventFacade;
    private static HashMap<String, RangingSessionCallback> sRangingSessionCallbackMap =
            new HashMap<String, RangingSessionCallback>();

    private enum Event {
        Invalid(0),
        Opened(1 << 0),
        Started(1 << 1),
        Reconfigured(1 << 2),
        Stopped(1 << 3),
        Closed(1 << 4),
        OpenFailed(1 << 5),
        StartFailed(1 << 6),
        ReconfigureFailed(1 << 7),
        StopFailed(1 << 8),
        CloseFailed(1 << 9),
        ReportReceived(1 << 10),
        EventAll(
                1 << 0
                | 1 << 1
                | 1 << 2
                | 1 << 3
                | 1 << 4
                | 1 << 5
                | 1 << 6
                | 1 << 7
                | 1 << 8
                | 1 << 9
                | 1 << 10);

        private int mType;
        Event(int type) {
            mType = type;
        }
        private int getType() {
            return mType;
        }
    }

    class RangingSessionCallback implements RangingSession.Callback {

        public RangingSession rangingSession;
        public PersistableBundle persistableBundle;
        public PersistableBundle sessionInfo;
        public RangingReport rangingReport;
        private int mEvents;
        public String mId;

        RangingSessionCallback(int events) {
            mEvents = events;
            mId = this.toString();
        }

        public void startListeningForEvents(int events) {
            mEvents |= events & Event.EventAll.getType();
        }

        public void stopListeningForEvents(int events) {
            mEvents &= ~(events & Event.EventAll.getType());
        }

        private void handleEvent(Event e) {
            Log.d(TAG + "RangingSessionCallback#handleEvent() for " + e.toString());
            if ((mEvents & e.getType()) == e.getType()) {
                if ((mEvents & e.getType()) == e.getType()) {
                    mEventFacade.postEvent(
                            UwbConstants.EventRangingSessionCallback,
                            new UwbEvents.RangingSessionEvent(mId, e.toString()));
                }
            }
        }

        @Override
        public void onOpened(RangingSession session) {
            Log.d(TAG + "RangingSessionCallback#onOpened() called");
            rangingSession = session;
            handleEvent(Event.Opened);
        }

        @Override
        public void onOpenFailed(@Reason int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onOpenedFailed() called");
            Log.d(TAG + "OpenFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.OpenFailed);
        }

        @Override
        public void onStarted(PersistableBundle info) {
            Log.d(TAG + "RangingSessionCallback#onStarted() called");
            sessionInfo = info;
            handleEvent(Event.Started);
        }

        @Override
        public void onStartFailed(@Reason int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onStartFailed() called");
            Log.d(TAG + "StartFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.StartFailed);
        }

        @Override
        public void onReconfigured(PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#oniReconfigured() called");
            persistableBundle = params;
            handleEvent(Event.Reconfigured);
        }

        @Override
        public void onReconfigureFailed(@Reason int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onReconfigureFailed() called");
            Log.d(TAG + "ReconfigureFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.ReconfigureFailed);
        }

        @Override
        public void onStopped(@Reason int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onStopped() called");
            Log.d(TAG + "Stopped reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.Stopped);
        }

        @Override
        public void onStopFailed(@Reason int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onStopFailed() called");
            Log.d(TAG + "StopFailed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.StopFailed);
        }

        @Override
        public void onClosed(@Reason int reason, PersistableBundle params) {
            Log.d(TAG + "RangingSessionCallback#onClosed() called");
            Log.d(TAG + "Closed reason " + String.valueOf(reason));
            persistableBundle = params;
            handleEvent(Event.Closed);
        }

        @Override
        public void onReportReceived(RangingReport report) {
            Log.d(TAG + "RangingSessionCallback#onReportReceived() called");
            rangingReport = report;
            handleEvent(Event.ReportReceived);
        }
    }

    /**
     * Start listening for a ranging session event
     * @param key : hash key of {@link RangingSessionCallback}
     * @param eventString : type of ranging session event.
     * @return True if listening for event successful, false if not
     */
    @Rpc(description = "start listening for RangingSession Event")
    public Boolean rangingSessionStartListeningForEvent(String key, String eventString) {
        RangingSessionCallback mRangingSessionCallback = sRangingSessionCallbackMap.get(key);
        int event = Event.valueOf(eventString).getType();
        if (mRangingSessionCallback == null || event == Event.Invalid.getType()) {
            return false;
        }
        mRangingSessionCallback.startListeningForEvents(event);
        return true;
    }

    /**
     * Stop listening for a ranging session event
     * @param key : hash key of {@link RangingSessionCallback}
     * @param eventString : type of ranging session event.
     * @return True if listening event successful, false if not
     */
    @Rpc(description = "stop listening for RangingSession Event")
    public Boolean rangingSessionStopListeningForEvent(String key, String eventString) {
        RangingSessionCallback mRangingSessionCallback = sRangingSessionCallbackMap.get(key);
        int event = Event.valueOf(eventString).getType();
        if (mRangingSessionCallback == null || event == Event.Invalid.getType()) {
            return false;
        }
        mRangingSessionCallback.stopListeningForEvents(event);
        return true;
    }

    public UwbManagerFacade(FacadeManager manager) {
        super(manager);
        mService = manager.getService();
        mContext = mService.getBaseContext();
        mUwbManager = (UwbManager) mService.getSystemService(Context.UWB_SERVICE);
        mEventFacade = manager.getReceiver(EventFacade.class);
    }

    /**
     * Get Uwb adapter state.
     */
    @Rpc(description = "Get Uwb adapter state")
    public int getAdapterState() {
        return mUwbManager.getAdapterState();
    }

    /**
     * Get the UWB state.
     */
    @Rpc(description = "Get Uwb state")
    public boolean isUwbEnabled() {
        return mUwbManager.isUwbEnabled();
    }

    /**
     * Set Uwb state to enabled or disabled.
     * @param enabled : boolean - true to enable, false to disable.
     */
    @Rpc(description = "Change Uwb state to enabled or disabled")
    public void setUwbEnabled(@RpcParameter(name = "enabled") Boolean enabled) {
        Log.d(TAG + "Setting Uwb state to " + enabled);
        mUwbManager.setUwbEnabled(enabled);
    }

    /**
     * Get UWB specification info.
     */
    @Rpc(description = "Get Uwb specification info")
    public PersistableBundle getSpecificationInfo() {
        return mUwbManager.getSpecificationInfo();
    }

    private byte[] convertJSONArrayToByteArray(JSONArray jArray) throws JSONException {
        if (jArray == null) {
            return null;
        }
        byte[] bArray = new byte[jArray.length()];
        for (int i = 0; i < jArray.length(); i++) {
            bArray[i] = (byte) jArray.getInt(i);
        }
        return bArray;
    }

    private FiraOpenSessionParams generateFiraOpenSessionParams(JSONObject j) throws JSONException {
        if (j == null) {
            return null;
        }
        FiraOpenSessionParams.Builder builder = new FiraOpenSessionParams.Builder();
        builder.setProtocolVersion(FiraParams.PROTOCOL_VERSION_1_1);
        if (j.has("deviceType")) {
            builder.setDeviceType(j.getInt("deviceType"));
        }
        if (j.has("deviceRole")) {
            builder.setDeviceRole(j.getInt("deviceRole"));
        }
        if (j.has("sessionId")) {
            builder.setSessionId(j.getInt("sessionId"));
        }
        if (j.has("channel")) {
            builder.setChannelNumber(j.getInt("channel"));
        }
        if (j.has("multiNodeMode")) {
            builder.setMultiNodeMode(j.getInt("multiNodeMode"));
        }
        if (j.has("deviceAddress")) {
            JSONArray jArray = j.getJSONArray("deviceAddress");
            byte[] bArray = convertJSONArrayToByteArray(jArray);
            UwbAddress deviceAddress = UwbAddress.fromBytes(bArray);
            builder.setDeviceAddress(deviceAddress);
        }
        if (j.has("destinationAddresses")) {
            JSONArray jArray = j.getJSONArray("destinationAddresses");
            UwbAddress[] destinationUwbAddresses = new UwbAddress[jArray.length()];
            for (int i = 0; i < jArray.length(); i++) {
                destinationUwbAddresses[i] = UwbAddress.fromBytes(
                        convertJSONArrayToByteArray(jArray.getJSONArray(i)));
            }
            builder.setDestAddressList(Arrays.asList(destinationUwbAddresses));
        }
        if (j.has("vendorId")) {
            JSONArray jArray = j.getJSONArray("vendorId");
            byte[] bArray = convertJSONArrayToByteArray(jArray);
            builder.setVendorId(bArray);
        }
        if (j.has("staticStsIV")) {
            JSONArray jArray = j.getJSONArray("staticStsIV");
            byte[] bArray = convertJSONArrayToByteArray(jArray);
            builder.setStaticStsIV(bArray);
        }
        return builder.build();
    }

    /**
     * Open UWB ranging session.
     */
    @Rpc(description = "Open UWB ranging session")
    public String openRangingSession(@RpcParameter(name = "config") JSONObject config)
            throws JSONException {
        RangingSessionCallback rangingSessionCallback = new RangingSessionCallback(
                Event.EventAll.getType());
        FiraOpenSessionParams params = generateFiraOpenSessionParams(config);
        CancellationSignal cancellationSignal = mUwbManager.openRangingSession(
                params.toBundle(), mExecutor, rangingSessionCallback);
        String key = rangingSessionCallback.mId;
        sRangingSessionCallbackMap.put(key, rangingSessionCallback);
        return key;
    }

    /**
     * Start UWB ranging.
     */
    @Rpc(description = "Start UWB ranging")
    public void startRangingSession(String key) {
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        rangingSessionCallback.rangingSession.start(new PersistableBundle());
    }

    private RangingMeasurement getRangingMeasurement(String key, JSONArray jArray)
            throws JSONException {
        byte[] bArray = convertJSONArrayToByteArray(jArray);
        UwbAddress peerAddress = UwbAddress.fromBytes(bArray);
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        List<RangingMeasurement> rangingMeasurements =
                rangingSessionCallback.rangingReport.getMeasurements();
        for (RangingMeasurement r: rangingMeasurements) {
            if (r.getStatus() == RangingMeasurement.RANGING_STATUS_SUCCESS
                    && r.getRemoteDeviceAddress().equals(peerAddress)) {
                Log.d(TAG + "Found peer " + peerAddress.toString());
                return r;
            }
        }
        Log.w(TAG + "Invalid ranging status or peer not found.");
        return null;
    }

    /**
     * Find if UWB peer is found.
     */
    @Rpc(description = "Find if UWB peer is found")
    public boolean isUwbPeerFound(String key, JSONArray jArray) throws JSONException {
        return getRangingMeasurement(key, jArray) != null;
    }

    /**
     * Get UWB distance measurement.
     */
    @Rpc(description = "Get UWB ranging distance measurement with peer.")
    public double getDistanceMeasurement(String key, JSONArray jArray) throws JSONException {
        RangingMeasurement rangingMeasurement = getRangingMeasurement(key, jArray);
        if (rangingMeasurement == null || rangingMeasurement.getDistanceMeasurement() == null) {
            throw new NullPointerException("Cannot get Distance Measurement on null object.");
        }
        return rangingMeasurement.getDistanceMeasurement().getMeters();
    }

    /**
     * Get angle of arrival azimuth measurement.
     */
    @Rpc(description = "Get UWB AoA Azimuth measurement.")
    public double getAoAAzimuthMeasurement(String key, JSONArray jArray) throws JSONException {
        RangingMeasurement rangingMeasurement = getRangingMeasurement(key, jArray);
        if (rangingMeasurement == null
                || rangingMeasurement.getAngleOfArrivalMeasurement() == null
                || rangingMeasurement.getAngleOfArrivalMeasurement().getAzimuth() == null) {
            throw new NullPointerException("Cannot get AoA azimuth measurement on null object.");
        }
        return rangingMeasurement.getAngleOfArrivalMeasurement().getAzimuth().getRadians();
    }

    /**
     * Get angle of arrival altitude measurement.
     */
    @Rpc(description = "Get UWB AoA Altitude measurement.")
    public double getAoAAltitudeMeasurement(String key, JSONArray jArray) throws JSONException {
        RangingMeasurement rangingMeasurement = getRangingMeasurement(key, jArray);
        if (rangingMeasurement == null
                || rangingMeasurement.getAngleOfArrivalMeasurement() == null
                || rangingMeasurement.getAngleOfArrivalMeasurement().getAltitude() == null) {
            throw new NullPointerException("Cannot get AoA altitude measurement on null object.");
        }
        return rangingMeasurement.getAngleOfArrivalMeasurement().getAltitude().getRadians();
    }

    /**
     * Stop UWB ranging.
     */
    @Rpc(description = "Stop UWB ranging")
    public void stopRangingSession(String key) {
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        rangingSessionCallback.rangingSession.stop();
    }

    /**
     * Close UWB ranging session.
     */
    @Rpc(description = "Close UWB ranging session")
    public void closeRangingSession(String key) {
        RangingSessionCallback rangingSessionCallback = sRangingSessionCallbackMap.get(key);
        rangingSessionCallback.rangingSession.close();
        sRangingSessionCallbackMap.remove(key);
    }

    @Override
    public void shutdown() {}
}
