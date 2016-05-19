package org.md2k.microsoftband;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

import com.microsoft.band.BandIOException;

import org.md2k.datakitapi.source.datasource.DataSource;
import org.md2k.microsoftband.sensors.Sensor;
import org.md2k.microsoftband.sensors.Sensors;
import org.md2k.utilities.Report.Log;

import java.util.ArrayList;

/*
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Syed Monowar Hossain <monowar.hossain@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

public class MicrosoftBand extends Device {
    private static final String TAG = MicrosoftBand.class.getSimpleName();
    boolean isConnected = false;
    private Sensors sensors;
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onreceive:" + platformId);

            if (intent.getStringExtra("platformid").equals(platformId)) {
                Log.d(TAG, "Restart msband ... id=" + platformId);
                unregister();
                register();
            }
        }
    };

    MicrosoftBand(Context context, String platformId, String deviceId) {
        super(context, platformId, deviceId);
        resetDataSource();

    }

    public void resetDataSource() {
        sensors = new Sensors(context, getPlatform());
    }

    public ArrayList<Sensor> getSensors() {
        return sensors.getSensors();
    }

    public void setEnabled(boolean enabled){
        this.enabled=enabled;
    }

    public void setEnabled(DataSource dataSource, boolean enabled) {
        sensors.setEnable(dataSource.getType(), enabled);
    }

    public boolean equals(String deviceId) {
        return this.deviceId.equals(deviceId);
    }

    public void register() {
        Log.d(TAG, "MicrosoftBand...register()...enabled=" + enabled+" bandCLient="+bandClient);
        if (!enabled) return;
        connect(new BandCallBack() {
            @Override
            public void onBandConnected() throws BandIOException {
                Log.d(TAG,"band connected...");
                LocalBroadcastManager.getInstance(context).registerReceiver(mMessageReceiver,
                        new IntentFilter("microsoftband_restart"));

                sensors.register(bandClient, getPlatform());
            }
        });
    }

    public void unregister(){
        if(bandClient==null) return;
        if (bandClient.isConnected()) {
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(mMessageReceiver);
                sensors.unregister(bandClient);
            } catch (BandIOException e) {
                e.printStackTrace();
            }
            disconnect();
        }
    }

}
