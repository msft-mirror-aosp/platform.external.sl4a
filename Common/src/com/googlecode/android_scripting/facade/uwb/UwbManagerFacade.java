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
import android.uwb.UwbManager;

import com.googlecode.android_scripting.Log;
import com.googlecode.android_scripting.facade.FacadeManager;
import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.rpc.Rpc;
import com.googlecode.android_scripting.rpc.RpcParameter;


/**
 * SL4A for UwbManager SystemApis.
 */
public class UwbManagerFacade extends RpcReceiver {

    private final Service mService;
    private final Context mContext;
    private final UwbManager mUwbManager;

    public UwbManagerFacade(FacadeManager manager) {
        super(manager);
        mService = manager.getService();
        mContext = mService.getBaseContext();
        mUwbManager = (UwbManager) mService.getSystemService(Context.UWB_SERVICE);
    }

    /**
     * Set Uwb state to enabled or disabled.
     * @param enabled : boolean - true to enable, false to disable.
     */
    @Rpc(description = "Change Uwb state to enabled or disabled")
    public void setUwbEnabled(@RpcParameter(name = "enabled") Boolean enabled) {
        Log.d("UwbManagerFacade: Setting Uwb state to " + enabled);
        mUwbManager.setUwbEnabled(enabled);
    }

    @Override
    public void shutdown() {}
}
