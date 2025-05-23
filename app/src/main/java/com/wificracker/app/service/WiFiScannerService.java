package com.wificracker.app.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import com.wificracker.app.WiFiNetwork;

public class WiFiScannerService extends Service {
    private WifiManager wifiManager;
    private List<WiFiNetwork> networks = new ArrayList<>();
    private BroadcastReceiver wifiScanReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        setupWifiScanReceiver();
    }

    private void setupWifiScanReceiver() {
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    List<ScanResult> scanResults = wifiManager.getScanResults();
                    networks.clear();
                    
                    for (ScanResult result : scanResults) {
                        WiFiNetwork network = new WiFiNetwork(
                            result.SSID,
                            result.BSSID,
                            result.level,
                            result.capabilities
                        );
                        networks.add(network);
                    }

                    // Broadcast the results
                    Intent broadcastIntent = new Intent("WIFI_SCAN_RESULTS");
                    broadcastIntent.putParcelableArrayListExtra("networks", new ArrayList<>(networks));
                    sendBroadcast(broadcastIntent);
                }
            }
        };

        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            wifiManager.startScan();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wifiScanReceiver != null) {
            unregisterReceiver(wifiScanReceiver);
        }
    }
} 