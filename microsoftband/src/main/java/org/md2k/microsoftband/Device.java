package org.md2k.microsoftband;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.BandTheme;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.notifications.MessageFlags;
import com.microsoft.band.notifications.VibrationType;
import com.microsoft.band.tiles.BandTile;
import com.microsoft.band.tiles.pages.FlowPanelOrientation;
import com.microsoft.band.tiles.pages.HorizontalAlignment;
import com.microsoft.band.tiles.pages.PageRect;
import com.microsoft.band.tiles.pages.ScrollFlowPanel;
import com.microsoft.band.tiles.pages.VerticalAlignment;

import org.md2k.datakitapi.source.platform.PlatformType;
import org.md2k.utilities.Report.Log;
import org.md2k.utilities.data_format.Notification;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
public abstract class Device {
    private static final String TAG = Device.class.getSimpleName();
    protected Context context;
    protected String deviceId;
    protected String platformId;
    protected String platformName;
    protected String platformType;
    protected String versionFirmware = null;
    protected String versionHardware = null;
    protected int version;
    protected boolean enabled;
    protected BandClient bandClient = null;
    protected Notification notification;
    private Thread connectThread;
    private Handler handler;
    private BandCallBack bandCallBack;
    private Runnable connectRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, deviceId + " connect run()...");
            while (true) {
                boolean res = connectDevice();
                if (res) {
                    Log.d(TAG, deviceId + " connect run() status= CONNECTED");

                    try {
                        bandCallBack.onBandConnected();
                    } catch (BandIOException e) {
//                        e.printStackTrace();
                    }
                    break;
                } else {
                    Log.d(TAG, deviceId + " connect run() status=NOTCONNECTED post delayed()");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Sleep Error");
                    }
                }
            }
            Log.d(TAG, deviceId + "...connect run()");
        }
    };
    private Runnable runAlarm = new Runnable() {
        @Override
        public void run() {
            vibrate();
            sendMessage();
        }
    };

    Device(Context context, String platformId, String deviceId) {
        this.context = context;
        this.deviceId = deviceId;
        this.platformId = platformId;
        platformType = PlatformType.MICROSOFT_BAND;
        this.enabled = false;
        BandInfo bandInfo = findBandInfo(deviceId);
        if (bandInfo != null) {
            platformName = bandInfo.getName();
            bandClient = BandClientManager.getInstance().create(context, bandInfo);
        }
        handler = new Handler();
    }

    public static BandInfo[] findBandInfo() {
        return BandClientManager.getInstance().getPairedBands();
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getPlatformName() {
        return platformName;
    }

    private BandInfo findBandInfo(String deviceId) {
        BandInfo[] mPairBands = BandClientManager.getInstance().getPairedBands();
        for (BandInfo bandInfo : mPairBands) {
            if (bandInfo.getMacAddress().equals(deviceId)) return bandInfo;
        }
        return null;
    }

    private boolean connectDevice() {
        Log.d(TAG, "bandClient=" + bandClient);
        if (bandClient.getConnectionState() == ConnectionState.CONNECTED) return true;
        try {
            ConnectionState state = bandClient.connect().await();
            if (ConnectionState.CONNECTED == state) {
                versionFirmware = bandClient.getFirmwareVersion().await();
                versionHardware = bandClient.getHardwareVersion().await();
                Log.d(TAG, "versionFirmware=" + versionFirmware + " versionHardware=" + versionHardware);
                if (Integer.valueOf(versionHardware) <= 19)
                    version = 1;
                else version = 2;
            }
            return ConnectionState.CONNECTED == state;
        } catch (InterruptedException | BandException e) {
            Log.d(TAG, deviceId + " exception1");
            Log.d(TAG, deviceId + " ...connectDataKit");
            return false;
        }
    }

    public String getPlatformId() {
        return platformId;
    }

    public String getPlatformType() {
        return platformType;
    }

    public void connect(BandCallBack bandCallBack) {
        this.bandCallBack = bandCallBack;
        if (bandClient != null) {
            connectThread = new Thread(connectRunnable);
            connectThread.start();
        }
    }

    public void stopConnectThread() {
        if (connectThread.isAlive())
            connectThread.interrupt();
    }

    public void disconnect() {
        Log.d(TAG, deviceId + "disconnect...");
        stopConnectThread();
        if (bandClient.isConnected())
            try {
                bandClient.disconnect().await();
            } catch (InterruptedException | BandException e) {
                e.printStackTrace();
            }
        Log.d(TAG, deviceId + "...disconnect");
    }

    private void changeBackGround(String wrist) throws BandException, InterruptedException {
        Log.d(TAG, "change background: band connected");
        Bitmap image = getBitmap(wrist);
        BandTheme bandTheme = getTheme(wrist);
        if (image == null || bandTheme == null) {
            Log.d(TAG, "image=" + image + " bandTheme=" + bandTheme);
            return;
        }
        bandClient.getPersonalizationManager().setMeTileImage(image).await();
        bandClient.getPersonalizationManager().setTheme(bandTheme).await();
    }

    private boolean doesTileExist(List<BandTile> tiles, UUID tileId) {
        for (BandTile tile : tiles) {
            if (tile.getTileId().equals(tileId)) {
                return true;
            }
        }
        return false;
    }

    private ScrollFlowPanel createPanel() {
        ScrollFlowPanel panel = new ScrollFlowPanel(new PageRect(0, 0, 225, 102));
        panel.setFlowPanelOrientation(FlowPanelOrientation.VERTICAL);
        panel.setHorizontalAlignment(HorizontalAlignment.LEFT);
        panel.setVerticalAlignment(VerticalAlignment.TOP);
        return panel;
    }

    public void vibrate() {
        try {
            bandClient.getNotificationManager().vibrate(getVibrationType(notification.getVibration_type())).await();
        } catch (InterruptedException | BandException e) {
            Log.e(TAG, "ERROR=" + e.toString());
            //    handle InterruptedException
        }

    }

    public void alarm() {
        handler.post(runAlarm);
    }

    public void sendMessage() {
        if (notification.getNotification_type() == null) return;
        ArrayList<TileInfo> tileInfos = TileInfo.readFile(context);
        for (int i = 0; i < tileInfos.size(); i++)
            if (tileInfos.get(i).name.equals(notification.getNotification_type()))
                try {
                    bandClient.getNotificationManager().sendMessage(tileInfos.get(i).UUID, notification.getMessage()[0], notification.getMessage()[1], new Date(), MessageFlags.SHOW_DIALOG);
                } catch (BandIOException e) {
                    e.printStackTrace();
                }
    }

    private void addTile(Activity activity, TileInfo tileInfo) throws BandException, InterruptedException {
        UUID tileId = tileInfo.UUID;
        if (doesTileExist(bandClient.getTileManager().getTiles().await(), tileId))
            return;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        int idLarge = 0;
        int idSmall = 0;
        switch (tileInfo.name) {
            case "EMA":
                idLarge = R.raw.ic_ema_48;
                idSmall = R.raw.ic_ema_24;

                break;
            case "INTERVENTION":
                idLarge = R.raw.ic_intervention_48;
                idSmall = R.raw.ic_intervention_24;
                break;
            default:
                idLarge = R.raw.ic_data_quality_48;
                idSmall = R.raw.ic_data_quality_24;
                break;
        }
        Bitmap tileIcon = BitmapFactory.decodeResource(context.getResources(), idLarge, options);
        Bitmap tileIconSmall = BitmapFactory.decodeResource(context.getResources(), idSmall, options);
        ScrollFlowPanel panel = createPanel();

        BandTile tile = new BandTile.Builder(tileId, tileInfo.name, tileIcon)
                .setTileSmallIcon(tileIconSmall)
                .build();
        bandClient.getTileManager().addTile(activity, tile).await();
    }

    private void addTiles(Activity activity, String wrist) throws BandException, InterruptedException {
        ArrayList<TileInfo> tileInfos = TileInfo.readFile(context);
        for (int i = 0; i < tileInfos.size(); i++) {
            for (int j = 0; j < tileInfos.get(i).location.size(); j++) {
                if (!tileInfos.get(i).location.get(j).equals(wrist))
                    continue;
                addTile(activity, tileInfos.get(i));
            }
        }
    }

    public synchronized void configureMicrosoftBand(final Activity activity, final String wrist) {
        Log.d(TAG, "change background wrist=" + wrist);
        connect(new BandCallBack() {
            @Override
            public void onBandConnected() {
                try {
                    changeBackGround(wrist);
                    addTiles(activity, wrist);

                    disconnect();

                    Intent intent = new Intent("background");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

                } catch (InterruptedException | BandException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private Bitmap getBitmap(String wrist) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        switch (wrist) {
            case "LEFT_WRIST":
                if (Integer.parseInt(versionHardware) <= 19)
                    return BitmapFactory.decodeResource(context.getResources(), R.raw.left_v1, options);
                else
                    return BitmapFactory.decodeResource(context.getResources(), R.raw.left_v2, options);
            case "RIGHT_WRIST":
                if (Integer.parseInt(versionHardware) <= 19)
                    return BitmapFactory.decodeResource(context.getResources(), R.raw.right_v1, options);
                else
                    return BitmapFactory.decodeResource(context.getResources(), R.raw.right_v2, options);
            default:
                return null;
        }
    }

    private BandTheme getTheme(String wrist) {
        if (wrist.equals(Constants.LEFT_WRIST))
            return new BandTheme(0x39bf6f, 0x41ce7a, 0x35aa65, 0x939982, 0x33a361, 0x2c8454);
        else if (wrist.equals(Constants.RIGHT_WRIST))
            return new BandTheme(0x3366cc, 0x3a78dd, 0x3165ba, 0x8997ab, 0x3a78dd, 0x2b5aa5);
        else return null;
    }

    private VibrationType getVibrationType(int vibrationType) {
        VibrationType vType = VibrationType.NOTIFICATION_TWO_TONE;
        switch (vibrationType) {
            case Notification.VIBRATION.NOTIFICATION_ONE_TONE:
                vType = VibrationType.NOTIFICATION_ONE_TONE;
                break;
            case Notification.VIBRATION.NOTIFICATION_TWO_TONE:
                vType = VibrationType.NOTIFICATION_TWO_TONE;
                break;
            case Notification.VIBRATION.NOTIFICATION_ALARM:
                vType = VibrationType.NOTIFICATION_ALARM;
                break;
            case Notification.VIBRATION.NOTIFICATION_TIMER:
                vType = VibrationType.NOTIFICATION_TIMER;
                break;
            case Notification.VIBRATION.ONE_TONE_HIGH:
                vType = VibrationType.ONE_TONE_HIGH;
                break;
            case Notification.VIBRATION.TWO_TONE_HIGH:
                vType = VibrationType.TWO_TONE_HIGH;
                break;
            case Notification.VIBRATION.THREE_TONE_HIGH:
                vType = VibrationType.THREE_TONE_HIGH;
                break;
            case Notification.VIBRATION.RAMP_UP:
                vType = VibrationType.RAMP_UP;
                break;
            case Notification.VIBRATION.RAMP_DOWN:
                vType = VibrationType.RAMP_DOWN;
                break;
        }
        Log.d(TAG, "vibrationtype=" + vibrationType + " enum=" + vType.toString());
        return vType;
    }

}
