package com.shellysolar.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BleSetupActivity extends Activity {

    private static final String TAG = "BleSetupActivity";
    private static final long SCAN_DURATION_MS = 15000;
    private static final int SHELLY_COMPANY_ID = 0x0BA9;
    private static final int REQUEST_PERMISSIONS = 100;

    private ListView listView;
    private Button scanButton;
    private ProgressBar progressBar;
    private TextView statusText;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<ScanResult> deviceList = new ArrayList<>();
    private HashSet<String> deviceAddresses = new HashSet<>();
    private DeviceAdapter adapter;
    private boolean isScanning = false;
    private ShellyBleManager bleManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_setup);

        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setTitle(R.string.ble_setup_title);
        }

        listView = (ListView) findViewById(R.id.device_list);
        scanButton = (Button) findViewById(R.id.btn_scan);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        statusText = (TextView) findViewById(R.id.status_text);

        adapter = new DeviceAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onDeviceSelected(deviceList.get(position));
            }
        });

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isScanning) {
                    stopScan();
                } else {
                    checkPermissionsAndScan();
                }
            }
        });

        bleManager = new ShellyBleManager(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
        if (bleManager != null) bleManager.disconnect();
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    private void checkPermissionsAndScan() {
        String[] perms = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        };

        List<String> missing = new ArrayList<>();
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }

        if (missing.isEmpty()) {
            startScan();
        } else {
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startScan();
            } else {
                Toast.makeText(this, "BLE permissions required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startScan() {
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Toast.makeText(this, "BLE scanner not available. Is Bluetooth on?", Toast.LENGTH_SHORT).show();
            return;
        }

        deviceList.clear();
        deviceAddresses.clear();
        adapter.notifyDataSetChanged();

        isScanning = true;
        scanButton.setText(R.string.stop_scan);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Scanning for Shelly BLE devices...");

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        bleScanner.startScan(null, settings, scanCallback);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, SCAN_DURATION_MS);
    }

    private void stopScan() {
        if (!isScanning) return;
        isScanning = false;

        try {
            if (bleScanner != null) bleScanner.stopScan(scanCallback);
        } catch (Exception e) {
            Log.w(TAG, "Error stopping scan", e);
        }

        scanButton.setText(R.string.scan_devices);
        progressBar.setVisibility(View.GONE);
        statusText.setText(deviceList.isEmpty()
                ? "No Shelly devices found. Make sure BLE is enabled on your devices."
                : "Found " + deviceList.size() + " device(s). Tap to configure.");
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            if (!isShellyDevice(result)) return;

            String address = result.getDevice().getAddress();
            if (!deviceAddresses.contains(address)) {
                deviceAddresses.add(address);
                deviceList.add(result);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                        statusText.setText("Found " + deviceList.size() + " Shelly device(s)...");
                    }
                });
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(BleSetupActivity.this, "Scan failed: error " + errorCode, Toast.LENGTH_SHORT).show();
                    stopScan();
                }
            });
        }
    };

    private boolean isShellyDevice(ScanResult result) {
        android.bluetooth.le.ScanRecord record = result.getScanRecord();
        if (record == null) return false;

        // Check manufacturer data for Shelly company ID
        byte[] shellyData = record.getManufacturerSpecificData(SHELLY_COMPANY_ID);
        if (shellyData != null) return true;

        // Check device name
        String name = record.getDeviceName();
        if (name == null) name = result.getDevice().getName();
        if (name != null && name.toLowerCase().contains("shelly")) return true;

        // Check service UUIDs
        List<ParcelUuid> uuids = record.getServiceUuids();
        if (uuids != null) {
            for (ParcelUuid uuid : uuids) {
                if (uuid.getUuid().equals(ShellyBleManager.SHELLY_SERVICE_UUID)) return true;
            }
        }

        return false;
    }

    private void onDeviceSelected(ScanResult result) {
        stopScan();
        BluetoothDevice device = result.getDevice();
        String deviceName = device.getName();
        if (deviceName == null) deviceName = device.getAddress();
        showConfigDialog(device, deviceName);
    }

    private void showConfigDialog(final BluetoothDevice device, final String deviceName) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ws_config, null);
        final EditText serverInput = (EditText) dialogView.findViewById(R.id.input_server);
        final EditText tokenInput = (EditText) dialogView.findViewById(R.id.input_token);

        serverInput.setText("wss://shellysolar.com/ws");

        new AlertDialog.Builder(this)
                .setTitle("Configure: " + deviceName)
                .setView(dialogView)
                .setPositiveButton(R.string.apply_config, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String server = serverInput.getText().toString().trim();
                        String token = tokenInput.getText().toString().trim();

                        if (server.isEmpty()) {
                            Toast.makeText(BleSetupActivity.this, "Server URL is required", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String wsUrl = token.isEmpty() ? server : server + "?token=" + token;
                        connectAndConfigure(device, deviceName, wsUrl);
                    }
                })
                .setNeutralButton(R.string.get_info, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        connectAndGetInfo(device, deviceName);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void connectAndConfigure(BluetoothDevice device, String deviceName, final String wsUrl) {
        showProgress("Connecting to " + deviceName + "...");

        bleManager.connect(device, new ShellyBleManager.Callback() {
            @Override
            public void onConnected() {
                showProgress("Connected! Enabling outbound WebSocket...");
                String params = "{\"config\":{\"enable\":true,\"server\":\"" + wsUrl + "\",\"ssl_ca\":\"*\"}}";
                bleManager.sendRpc("Ws.SetConfig", params);
            }

            @Override
            public void onDisconnected() {
                hideProgress();
                Toast.makeText(BleSetupActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRpcResponse(String response) {
                hideProgress();
                bleManager.disconnect();

                try {
                    JSONObject json = new JSONObject(response);
                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        showResultDialog("Configuration Failed",
                                "Error: " + error.optString("message", "Unknown") +
                                "\nCode: " + error.optInt("code", -1));
                    } else {
                        JSONObject result = json.optJSONObject("result");
                        boolean restart = result != null && result.optBoolean("restart_required", false);
                        String msg = "WebSocket outbound configured!\n\nServer: " + wsUrl;
                        if (restart) msg += "\n\nDevice restart required for changes to take effect.";
                        showResultDialog("Success", msg);
                    }
                } catch (Exception e) {
                    showResultDialog("Response", response);
                }
            }

            @Override
            public void onError(String message) {
                hideProgress();
                bleManager.disconnect();
                Toast.makeText(BleSetupActivity.this, "Error: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String deviceInfoCache = "";

    private void connectAndGetInfo(BluetoothDevice device, final String deviceName) {
        showProgress("Connecting to " + deviceName + "...");

        bleManager.connect(device, new ShellyBleManager.Callback() {
            private boolean infoReceived = false;

            @Override
            public void onConnected() {
                showProgress("Getting device info...");
                bleManager.sendRpc("Shelly.GetDeviceInfo", null);
            }

            @Override
            public void onDisconnected() {
                hideProgress();
            }

            @Override
            public void onRpcResponse(String response) {
                if (!infoReceived) {
                    infoReceived = true;
                    deviceInfoCache = response;
                    showProgress("Getting WebSocket config...");
                    bleManager.sendRpc("Ws.GetConfig", null);
                } else {
                    hideProgress();
                    bleManager.disconnect();
                    showResultDialog("Device: " + deviceName, formatInfo(deviceInfoCache, response));
                }
            }

            @Override
            public void onError(String message) {
                hideProgress();
                bleManager.disconnect();
                Toast.makeText(BleSetupActivity.this, "Error: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String formatInfo(String deviceInfoJson, String wsConfigJson) {
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject di = new JSONObject(deviceInfoJson);
            JSONObject r = di.optJSONObject("result");
            if (r != null) {
                sb.append("Model: ").append(r.optString("model", "Unknown")).append("\n");
                sb.append("Name: ").append(r.optString("name", "N/A")).append("\n");
                sb.append("ID: ").append(r.optString("id", "N/A")).append("\n");
                sb.append("Firmware: ").append(r.optString("fw_id", "N/A")).append("\n");
                sb.append("App: ").append(r.optString("app", "N/A")).append("\n\n");
            }
        } catch (Exception e) {
            sb.append("Device Info: ").append(deviceInfoJson).append("\n\n");
        }

        try {
            JSONObject ws = new JSONObject(wsConfigJson);
            JSONObject r = ws.optJSONObject("result");
            if (r != null) {
                sb.append("--- WebSocket Config ---\n");
                sb.append("Enabled: ").append(r.optBoolean("enable", false)).append("\n");
                sb.append("Server: ").append(r.optString("server", "not set")).append("\n");
                sb.append("SSL CA: ").append(r.optString("ssl_ca", "null")).append("\n");
            }
        } catch (Exception e) {
            sb.append("WS Config: ").append(wsConfigJson).append("\n");
        }
        return sb.toString();
    }

    private void showProgress(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
                statusText.setText(message);
            }
        });
    }

    private void hideProgress() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
                statusText.setText("");
            }
        });
    }

    private void showResultDialog(final String title, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(BleSetupActivity.this)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    // ListView Adapter
    private class DeviceAdapter extends BaseAdapter {
        @Override
        public int getCount() { return deviceList.size(); }
        @Override
        public Object getItem(int pos) { return deviceList.get(pos); }
        @Override
        public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(BleSetupActivity.this)
                        .inflate(R.layout.item_device, parent, false);
            }

            ScanResult result = deviceList.get(position);
            BluetoothDevice device = result.getDevice();

            TextView nameText = (TextView) convertView.findViewById(R.id.device_name);
            TextView addressText = (TextView) convertView.findViewById(R.id.device_address);
            TextView rssiText = (TextView) convertView.findViewById(R.id.device_rssi);
            TextView modelText = (TextView) convertView.findViewById(R.id.device_model);

            String name = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null;
            if (name == null) name = device.getName();
            if (name == null) name = "Unknown Shelly";

            nameText.setText(name);
            addressText.setText(device.getAddress());
            rssiText.setText(result.getRssi() + " dBm");

            byte[] shellyData = result.getScanRecord() != null
                    ? result.getScanRecord().getManufacturerSpecificData(SHELLY_COMPANY_ID)
                    : null;
            modelText.setText(shellyData != null ? "Shelly Gen2+ (BLE)" : "Shelly Device");

            return convertView;
        }
    }
}
