package com.wificracker.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.wificracker.app.service.PasswordCrackerService;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private WifiManager wifiManager;
    private RecyclerView rvNetworks;
    private NetworkAdapter networkAdapter;
    private MaterialButton btnScan, btnSelectPasswordFile, btnStartCracking;
    private LinearProgressIndicator progressBar;
    private TextView tvSelectedFile, tvEmptyState, tvCrackingStatus;
    private String selectedPasswordFile;
    private List<WiFiNetwork> selectedNetworks = new ArrayList<>();
    private List<WiFiNetwork> allNetworks = new ArrayList<>();
    private boolean isScanning = false;
    private boolean isCracking = false;

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.GetContent(),
        uri -> {
            if (uri != null) {
                selectedPasswordFile = uri.toString();
                tvSelectedFile.setText("Selected: " + uri.getLastPathSegment());
                updateStartButtonState();
            }
        }
    );

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            isScanning = false;
            progressBar.setVisibility(View.GONE);
            btnScan.setEnabled(true);
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                updateNetworkList();
            } else {
                showError("WiFi scan failed");
            }
        }
    };

    private final BroadcastReceiver crackProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case "com.wificracker.app.CRACK_PROGRESS":
                    String ssid = intent.getStringExtra("ssid");
                    String currentPassword = intent.getStringExtra("currentPassword");
                    int progress = intent.getIntExtra("progress", 0);
                    updateCrackingStatus(ssid, currentPassword, progress);
                    break;
                case "com.wificracker.app.CRACK_SUCCESS":
                    String successSsid = intent.getStringExtra("ssid");
                    String password = intent.getStringExtra("password");
                    showSuccess(successSsid, password);
                    resetCrackingState();
                    break;
                case "com.wificracker.app.CRACK_FAILURE":
                    showError("Failed to crack password");
                    resetCrackingState();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        initializeViews();
        setupClickListeners();
        checkPermissions();
    }

    private void initializeViews() {
        btnScan = findViewById(R.id.btnScan);
        btnSelectPasswordFile = findViewById(R.id.btnSelectPasswordFile);
        btnStartCracking = findViewById(R.id.btnStartCracking);
        progressBar = findViewById(R.id.progressBar);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvCrackingStatus = findViewById(R.id.tvCrackingStatus);
        rvNetworks = findViewById(R.id.rvNetworks);
        rvNetworks.setLayoutManager(new LinearLayoutManager(this));
        networkAdapter = new NetworkAdapter(allNetworks, this::onNetworkSelected);
        rvNetworks.setAdapter(networkAdapter);
        progressBar.setVisibility(View.GONE);
        tvSelectedFile.setText("No password file selected");
        tvEmptyState.setVisibility(View.GONE);
        tvCrackingStatus.setVisibility(View.GONE);
        btnStartCracking.setEnabled(false);
    }

    private void setupClickListeners() {
        btnScan.setOnClickListener(v -> startWifiScan());
        btnSelectPasswordFile.setOnClickListener(v -> filePickerLauncher.launch("text/*"));
        btnStartCracking.setOnClickListener(v -> startCracking());
    }

    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                permissionsToRequest.toArray(new String[0]),
                PERMISSION_REQUEST_CODE);
        }
    }

    private void startWifiScan() {
        if (isScanning) return;
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            isScanning = true;
            progressBar.setVisibility(View.VISIBLE);
            btnScan.setEnabled(false);
            tvEmptyState.setVisibility(View.GONE);
            allNetworks.clear();
            networkAdapter.notifyDataSetChanged();
            selectedNetworks.clear();
            updateStartButtonState();
            boolean started = wifiManager.startScan();
            if (!started) {
                isScanning = false;
                progressBar.setVisibility(View.GONE);
                btnScan.setEnabled(true);
                showError("Failed to start WiFi scan");
            }
        } else {
            showError("Please enable WiFi");
        }
    }

    private void updateNetworkList() {
        List<ScanResult> results = wifiManager.getScanResults();
        allNetworks.clear();
        if (results != null && !results.isEmpty()) {
            for (ScanResult result : results) {
                allNetworks.add(new WiFiNetwork(result.SSID, result.BSSID));
            }
            tvEmptyState.setVisibility(View.GONE);
        } else {
            tvEmptyState.setText("No networks found");
            tvEmptyState.setVisibility(View.VISIBLE);
        }
        networkAdapter.notifyDataSetChanged();
    }

    private void onNetworkSelected(WiFiNetwork network, boolean isSelected) {
        if (isSelected) {
            if (!selectedNetworks.contains(network)) selectedNetworks.add(network);
        } else {
            selectedNetworks.remove(network);
        }
        updateStartButtonState();
    }

    private void updateStartButtonState() {
        btnStartCracking.setEnabled(!selectedNetworks.isEmpty() && selectedPasswordFile != null);
    }

    private void startCracking() {
        if (selectedNetworks.isEmpty() || selectedPasswordFile == null) {
            showError("Please select networks and password file");
            return;
        }
        isCracking = true;
        progressBar.setVisibility(View.VISIBLE);
        tvCrackingStatus.setVisibility(View.VISIBLE);
        btnScan.setEnabled(false);
        btnSelectPasswordFile.setEnabled(false);
        btnStartCracking.setEnabled(false);
        // Start password cracking service
        Intent intent = new Intent(this, PasswordCrackerService.class);
        intent.putExtra("passwordFile", selectedPasswordFile);
        intent.putExtra("networks", new ArrayList<>(selectedNetworks));
        startService(intent);
    }

    private void updateCrackingStatus(String ssid, String currentPassword, int progress) {
        String status = String.format("Trying network: %s\nCurrent password: %s\nProgress: %d%%", 
            ssid, currentPassword, progress);
        tvCrackingStatus.setText(status);
        progressBar.setProgress(progress);
    }

    private void showSuccess(String ssid, String password) {
        String message = String.format("Success! Network: %s\nPassword: %s", ssid, password);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void resetCrackingState() {
        isCracking = false;
        progressBar.setVisibility(View.GONE);
        tvCrackingStatus.setVisibility(View.GONE);
        btnScan.setEnabled(true);
        btnSelectPasswordFile.setEnabled(true);
        updateStartButtonState();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.wificracker.app.CRACK_PROGRESS");
        filter.addAction("com.wificracker.app.CRACK_SUCCESS");
        filter.addAction("com.wificracker.app.CRACK_FAILURE");
        registerReceiver(crackProgressReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(wifiScanReceiver);
        unregisterReceiver(crackProgressReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                showError("Required permissions not granted");
                finish();
            }
        }
    }
} 