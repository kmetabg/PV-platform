package com.shellysolar.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class BleSetupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BleSetupActivity"
        private const val SCAN_DURATION_MS = 15000L
        // Shelly BLE manufacturer company ID
        private const val SHELLY_COMPANY_ID = 0x0BA9
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var scanButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var emptyView: TextView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private val deviceList = mutableListOf<ScanResult>()
    private val deviceAddresses = mutableSetOf<String>()
    private lateinit var adapter: DeviceAdapter
    private var isScanning = false
    private var bleManager: ShellyBleManager? = null

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startScan()
        } else {
            Toast.makeText(this, "BLE permissions required for scanning", Toast.LENGTH_LONG).show()
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bluetoothAdapter?.isEnabled == true) {
            startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_setup)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.ble_setup_title)
        }

        recyclerView = findViewById(R.id.device_list)
        scanButton = findViewById(R.id.btn_scan)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)
        emptyView = findViewById(R.id.empty_view)

        adapter = DeviceAdapter(deviceList) { result -> onDeviceSelected(result) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        scanButton.setOnClickListener {
            if (isScanning) {
                stopScan()
            } else {
                checkPermissionsAndScan()
            }
        }

        bleManager = ShellyBleManager(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        bleManager?.disconnect()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun checkPermissionsAndScan() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            if (bluetoothAdapter?.isEnabled != true) {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                startScan()
            }
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            Toast.makeText(this, "BLE scanner not available", Toast.LENGTH_SHORT).show()
            return
        }

        deviceList.clear()
        deviceAddresses.clear()
        adapter.notifyDataSetChanged()
        updateEmptyState()

        isScanning = true
        scanButton.text = getString(R.string.stop_scan)
        progressBar.visibility = View.VISIBLE
        statusText.text = getString(R.string.scanning)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(null, settings, scanCallback)

        handler.postDelayed({ stopScan() }, SCAN_DURATION_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!isScanning) return
        isScanning = false

        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping scan", e)
        }

        scanButton.text = getString(R.string.scan_devices)
        progressBar.visibility = View.GONE
        statusText.text = if (deviceList.isEmpty()) {
            getString(R.string.no_devices_found)
        } else {
            getString(R.string.scan_complete, deviceList.size)
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address

            // Check if this is a Shelly device
            if (!isShellyDevice(result)) return

            if (address !in deviceAddresses) {
                deviceAddresses.add(address)
                deviceList.add(result)
                handler.post {
                    adapter.notifyItemInserted(deviceList.size - 1)
                    updateEmptyState()
                    statusText.text = getString(R.string.found_devices, deviceList.size)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            handler.post {
                Toast.makeText(
                    this@BleSetupActivity,
                    "Scan failed: error $errorCode",
                    Toast.LENGTH_SHORT
                ).show()
                stopScan()
            }
        }
    }

    private fun isShellyDevice(result: ScanResult): Boolean {
        val record = result.scanRecord ?: return false

        // Check manufacturer data for Shelly company ID (0x0BA9)
        val shellyData = record.getManufacturerSpecificData(SHELLY_COMPANY_ID)
        if (shellyData != null) return true

        // Also check device name
        val name = record.deviceName ?: result.device.name ?: ""
        if (name.lowercase().contains("shelly")) return true

        // Check if has the Shelly GATT service UUID
        val serviceUuids = record.serviceUuids
        if (serviceUuids != null) {
            for (uuid in serviceUuids) {
                if (uuid.uuid == ShellyBleManager.SHELLY_SERVICE_UUID) return true
            }
        }

        return false
    }

    private fun updateEmptyState() {
        emptyView.visibility = if (deviceList.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (deviceList.isEmpty()) View.GONE else View.VISIBLE
    }

    @SuppressLint("MissingPermission")
    private fun onDeviceSelected(result: ScanResult) {
        stopScan()

        val device = result.device
        val deviceName = device.name ?: device.address

        showConfigDialog(device, deviceName)
    }

    @SuppressLint("MissingPermission")
    private fun showConfigDialog(device: BluetoothDevice, deviceName: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ws_config, null)
        val serverInput = dialogView.findViewById<EditText>(R.id.input_server)
        val tokenInput = dialogView.findViewById<EditText>(R.id.input_token)

        // Pre-fill with shellysolar.com WebSocket URL
        serverInput.setText("wss://shellysolar.com/ws")

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.configure_device, deviceName))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                val server = serverInput.text.toString().trim()
                val token = tokenInput.text.toString().trim()

                if (server.isEmpty()) {
                    Toast.makeText(this, "Server URL is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val wsUrl = if (token.isNotEmpty()) {
                    "$server?token=$token"
                } else {
                    server
                }

                connectAndConfigure(device, deviceName, wsUrl)
            }
            .setNeutralButton(getString(R.string.get_info)) { _, _ ->
                connectAndGetInfo(device, deviceName)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.show()
    }

    @SuppressLint("MissingPermission")
    private fun connectAndConfigure(device: BluetoothDevice, deviceName: String, wsUrl: String) {
        showProgress("Connecting to $deviceName...")

        bleManager?.connect(device, object : ShellyBleManager.Callback {
            override fun onConnected() {
                showProgress("Connected! Enabling outbound WebSocket...")

                val params = """{"config":{"enable":true,"server":"$wsUrl","ssl_ca":"*"}}"""
                bleManager?.sendRpc("Ws.SetConfig", params)
            }

            override fun onDisconnected() {
                hideProgress()
                Toast.makeText(this@BleSetupActivity, "Disconnected", Toast.LENGTH_SHORT).show()
            }

            override fun onRpcResponse(response: String) {
                hideProgress()
                bleManager?.disconnect()

                try {
                    val json = JSONObject(response)
                    if (json.has("error")) {
                        val error = json.getJSONObject("error")
                        showResultDialog(
                            "Configuration Failed",
                            "Error: ${error.optString("message", "Unknown error")}\n" +
                                "Code: ${error.optInt("code", -1)}"
                        )
                    } else {
                        val result = json.optJSONObject("result")
                        val restartRequired = result?.optBoolean("restart_required", false) ?: false

                        val message = buildString {
                            append("WebSocket outbound configured successfully!\n\n")
                            append("Server: $wsUrl\n")
                            if (restartRequired) {
                                append("\nDevice restart is required for changes to take effect.")
                            }
                        }
                        showResultDialog("Success", message)
                    }
                } catch (e: Exception) {
                    showResultDialog("Response", response)
                }
            }

            override fun onError(message: String) {
                hideProgress()
                bleManager?.disconnect()
                Toast.makeText(this@BleSetupActivity, "Error: $message", Toast.LENGTH_LONG).show()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun connectAndGetInfo(device: BluetoothDevice, deviceName: String) {
        showProgress("Connecting to $deviceName...")

        bleManager?.connect(device, object : ShellyBleManager.Callback {
            private var infoReceived = false

            override fun onConnected() {
                showProgress("Connected! Getting device info...")
                bleManager?.sendRpc("Shelly.GetDeviceInfo")
            }

            override fun onDisconnected() {
                hideProgress()
            }

            override fun onRpcResponse(response: String) {
                if (!infoReceived) {
                    infoReceived = true
                    // After device info, also get WS config
                    showProgress("Getting WebSocket config...")
                    bleManager?.sendRpc("Ws.GetConfig")

                    // Store device info for later display
                    deviceInfoCache = response
                } else {
                    hideProgress()
                    bleManager?.disconnect()

                    // Show both device info and WS config
                    val info = formatDeviceInfo(deviceInfoCache, response)
                    showResultDialog("Device Info: $deviceName", info)
                }
            }

            override fun onError(message: String) {
                hideProgress()
                bleManager?.disconnect()
                Toast.makeText(this@BleSetupActivity, "Error: $message", Toast.LENGTH_LONG).show()
            }
        })
    }

    private var deviceInfoCache: String = ""

    private fun formatDeviceInfo(deviceInfoJson: String, wsConfigJson: String): String {
        return buildString {
            try {
                val deviceInfo = JSONObject(deviceInfoJson)
                val result = deviceInfo.optJSONObject("result")
                if (result != null) {
                    append("Device: ${result.optString("model", "Unknown")}\n")
                    append("Name: ${result.optString("name", "N/A")}\n")
                    append("ID: ${result.optString("id", "N/A")}\n")
                    append("Firmware: ${result.optString("fw_id", "N/A")}\n")
                    append("App: ${result.optString("app", "N/A")}\n")
                    append("\n")
                }
            } catch (e: Exception) {
                append("Device Info: $deviceInfoJson\n\n")
            }

            try {
                val wsConfig = JSONObject(wsConfigJson)
                val result = wsConfig.optJSONObject("result")
                if (result != null) {
                    append("--- WebSocket Config ---\n")
                    append("Enabled: ${result.optBoolean("enable", false)}\n")
                    append("Server: ${result.optString("server", "not set")}\n")
                    append("SSL CA: ${result.optString("ssl_ca", "null")}\n")
                }
            } catch (e: Exception) {
                append("WS Config: $wsConfigJson\n")
            }
        }
    }

    private fun showProgress(message: String) {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            statusText.text = message
        }
    }

    private fun hideProgress() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            statusText.text = ""
        }
    }

    private fun showResultDialog(title: String, message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // RecyclerView Adapter for scanned devices
    class DeviceAdapter(
        private val devices: List<ScanResult>,
        private val onClick: (ScanResult) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.device_name)
            val addressText: TextView = view.findViewById(R.id.device_address)
            val rssiText: TextView = view.findViewById(R.id.device_rssi)
            val modelText: TextView = view.findViewById(R.id.device_model)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_device, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("MissingPermission")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = devices[position]
            val device = result.device

            val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown Shelly"
            holder.nameText.text = name
            holder.addressText.text = device.address
            holder.rssiText.text = "${result.rssi} dBm"

            // Try to extract model from manufacturer data
            val shellyData = result.scanRecord?.getManufacturerSpecificData(SHELLY_COMPANY_ID)
            holder.modelText.text = if (shellyData != null) {
                "Shelly Gen2+ (BLE)"
            } else {
                "Shelly Device"
            }

            holder.itemView.setOnClickListener { onClick(result) }
        }

        override fun getItemCount() = devices.size
    }
}
