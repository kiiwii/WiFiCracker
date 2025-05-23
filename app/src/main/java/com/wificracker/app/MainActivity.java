package com.wificracker.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
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
    private String selectedPasswordFile;
    private List<WiFiNetwork> selectedNetworks = new ArrayList<>();

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.GetContent(),
        uri -> {
            if (uri != null) {
                selectedPasswordFile = uri.toString();
                btnStartCracking.setEnabled(!selectedNetworks.isEmpty());
            }
        }
    );

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
        rvNetworks = findViewById(R.id.rvNetworks);
        
        rvNetworks.setLayoutManager(new LinearLayoutManager(this));
        networkAdapter = new NetworkAdapter(new ArrayList<>(), this::onNetworkSelected);
        rvNetworks.setAdapter(networkAdapter);
    }

    private void setupClickListeners() {
        btnScan.setOnClickListener(v -> startWifiScan());
        btnSelectPasswordFile.setOnClickListener(v -> filePickerLauncher.launch("text/*"));
        btnStartCracking.setOnClickListener(v -> startCracking());
    }

    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE
        };

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
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
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            wifiManager.startScan();
            // Register for scan results in a real implementation
            Toast.makeText(this, "Scanning for networks...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Please enable WiFi", Toast.LENGTH_SHORT).show();
        }
    }

    private void onNetworkSelected(WiFiNetwork network, boolean isSelected) {
        if (isSelected) {
            selectedNetworks.add(network);
        } else {
            selectedNetworks.remove(network);
        }
        btnStartCracking.setEnabled(!selectedNetworks.isEmpty() && selectedPasswordFile != null);
    }

    private void startCracking() {
        if (selectedNetworks.isEmpty() || selectedPasswordFile == null) {
            Toast.makeText(this, "Please select networks and password file", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        // Start password cracking service
        Intent intent = new Intent(this, PasswordCrackerService.class);
        intent.putExtra("passwordFile", selectedPasswordFile);
        intent.putExtra("networks", new ArrayList<>(selectedNetworks));
        startService(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, 
        int[] grantResults) {
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
                Toast.makeText(this, "Required permissions not granted", 
                    Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
} 