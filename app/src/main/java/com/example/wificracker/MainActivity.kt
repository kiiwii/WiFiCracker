package com.example.wificracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wificracker.adapter.WiFiListAdapter
import com.example.wificracker.model.WiFiNetwork
import com.example.wificracker.service.WiFiScannerService

class MainActivity : AppCompatActivity() {
    private lateinit var wifiManager: WifiManager
    private lateinit var wifiList: ListView
    private lateinit var scanButton: Button
    private lateinit var adapter: WiFiListAdapter
    private val networks = mutableListOf<WiFiNetwork>()
    private val PERMISSIONS_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        wifiList = findViewById(R.id.wifiList)
        scanButton = findViewById(R.id.scanButton)
        adapter = WiFiListAdapter(this, networks)
        wifiList.adapter = adapter

        scanButton.setOnClickListener {
            if (checkAndRequestPermissions()) {
                startWiFiScan()
            }
        }

        wifiList.setOnItemClickListener { _, _, position, _ ->
            val selectedNetwork = networks[position]
            startPasswordCracking(selectedNetwork)
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        return if (permissionsToRequest.isEmpty()) {
            true
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST_CODE)
            false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startWiFiScan()
            } else {
                Toast.makeText(this, "Permissions required to scan WiFi networks", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startWiFiScan() {
        val intent = Intent(this, WiFiScannerService::class.java)
        startService(intent)
    }

    private fun startPasswordCracking(network: WiFiNetwork) {
        val intent = Intent(this, PasswordCrackerService::class.java).apply {
            putExtra("SSID", network.ssid)
            putExtra("BSSID", network.bssid)
        }
        startService(intent)
    }

    fun updateWiFiList(newNetworks: List<WiFiNetwork>) {
        networks.clear()
        networks.addAll(newNetworks)
        adapter.notifyDataSetChanged()
    }
} 