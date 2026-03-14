package com.shellysolar.app;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class ShellyBleManager {

    private static final String TAG = "ShellyBleManager";

    // Shelly BLE GATT UUIDs
    public static final UUID SHELLY_SERVICE_UUID = UUID.fromString("5F6D4F53-5F52-5043-5F64-6174615F5F5F");
    public static final UUID DATA_CHAR_UUID = UUID.fromString("5F6D4F53-5F52-5043-5F64-6174615F5F5F");
    public static final UUID TX_CTRL_CHAR_UUID = UUID.fromString("5F6D4F53-5F52-5043-5F74-785F63746C5F");
    public static final UUID RX_CTRL_CHAR_UUID = UUID.fromString("5F6D4F53-5F52-5043-5F72-785F63746C5F");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int MAX_CHUNK_SIZE = 512;
    private static final long RPC_TIMEOUT_MS = 15000;

    public interface Callback {
        void onConnected();
        void onDisconnected();
        void onRpcResponse(String response);
        void onError(String message);
    }

    private Context context;
    private BluetoothGatt gatt;
    private Callback callback;
    private BluetoothGattCharacteristic dataChar;
    private BluetoothGattCharacteristic txCtrlChar;
    private BluetoothGattCharacteristic rxCtrlChar;

    private byte[] pendingRequest;
    private int pendingRequestOffset;
    private byte[] responseBuffer;
    private int expectedResponseLength;
    private int responseOffset;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int rpcId = 1;

    public ShellyBleManager(Context context) {
        this.context = context;
    }

    public void connect(BluetoothDevice device, Callback callback) {
        this.callback = callback;
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        callback = null;
    }

    public int sendRpc(String method, String params) {
        int id = rpcId++;
        String json;
        if (params != null) {
            json = "{\"id\":" + id + ",\"src\":\"shellysolar_app\",\"method\":\"" + method + "\",\"params\":" + params + "}";
        } else {
            json = "{\"id\":" + id + ",\"src\":\"shellysolar_app\",\"method\":\"" + method + "\"}";
        }
        Log.d(TAG, "Sending RPC: " + json);
        sendData(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (expectedResponseLength == 0 && responseOffset == 0 && callback != null) {
                    callback.onError("RPC timeout for " + method);
                }
            }
        }, RPC_TIMEOUT_MS);

        return id;
    }

    private void sendData(byte[] data) {
        if (txCtrlChar == null) {
            if (callback != null) callback.onError("TX Control characteristic not found");
            return;
        }
        if (dataChar == null) {
            if (callback != null) callback.onError("Data characteristic not found");
            return;
        }

        pendingRequest = data;
        pendingRequestOffset = 0;
        responseBuffer = new byte[0];
        expectedResponseLength = 0;
        responseOffset = 0;

        byte[] lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length).array();
        txCtrlChar.setValue(lengthBytes);
        txCtrlChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        gatt.writeCharacteristic(txCtrlChar);
    }

    private void writeNextChunk() {
        if (pendingRequest == null) return;
        if (dataChar == null) return;

        if (pendingRequestOffset >= pendingRequest.length) {
            pendingRequest = null;
            Log.d(TAG, "All data sent, waiting for response...");
            return;
        }

        int remaining = pendingRequest.length - pendingRequestOffset;
        int chunkSize = Math.min(remaining, MAX_CHUNK_SIZE);
        byte[] chunk = new byte[chunkSize];
        System.arraycopy(pendingRequest, pendingRequestOffset, chunk, 0, chunkSize);
        pendingRequestOffset += chunkSize;

        dataChar.setValue(chunk);
        dataChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        gatt.writeCharacteristic(dataChar);
    }

    private void readNextResponseChunk() {
        if (responseOffset >= expectedResponseLength) {
            String responseStr = new String(responseBuffer, java.nio.charset.StandardCharsets.UTF_8);
            Log.d(TAG, "RPC Response: " + responseStr);
            final String resp = responseStr;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) callback.onRpcResponse(resp);
                }
            });
            return;
        }
        gatt.readCharacteristic(dataChar);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server");
                g.requestMtu(517);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server");
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onDisconnected();
                    }
                });
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            Log.d(TAG, "MTU changed to " + mtu);
            g.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onError("Service discovery failed");
                    }
                });
                return;
            }

            android.bluetooth.BluetoothGattService service = g.getService(SHELLY_SERVICE_UUID);
            if (service == null) {
                for (android.bluetooth.BluetoothGattService s : g.getServices()) {
                    if (s.getCharacteristic(DATA_CHAR_UUID) != null ||
                        s.getCharacteristic(TX_CTRL_CHAR_UUID) != null ||
                        s.getCharacteristic(RX_CTRL_CHAR_UUID) != null) {
                        service = s;
                        break;
                    }
                }
            }

            if (service == null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onError("Shelly RPC service not found");
                    }
                });
                return;
            }

            dataChar = service.getCharacteristic(DATA_CHAR_UUID);
            txCtrlChar = service.getCharacteristic(TX_CTRL_CHAR_UUID);
            rxCtrlChar = service.getCharacteristic(RX_CTRL_CHAR_UUID);

            if (dataChar == null || txCtrlChar == null || rxCtrlChar == null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onError("Required BLE characteristics not found");
                    }
                });
                return;
            }

            g.setCharacteristicNotification(rxCtrlChar, true);
            BluetoothGattDescriptor descriptor = rxCtrlChar.getDescriptor(CCCD_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(descriptor);
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onConnected();
                    }
                });
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onConnected();
                    }
                });
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onError("Failed to enable notifications");
                    }
                });
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onError("Write failed: " + status);
                    }
                });
                return;
            }

            if (characteristic.getUuid().equals(TX_CTRL_CHAR_UUID)) {
                writeNextChunk();
            } else if (characteristic.getUuid().equals(DATA_CHAR_UUID)) {
                writeNextChunk();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(RX_CTRL_CHAR_UUID)) {
                byte[] value = characteristic.getValue();
                if (value != null && value.length >= 4) {
                    expectedResponseLength = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    responseBuffer = new byte[expectedResponseLength];
                    responseOffset = 0;
                    Log.d(TAG, "Response length: " + expectedResponseLength);
                    readNextResponseChunk();
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onError("Read failed: " + status);
                    }
                });
                return;
            }

            if (characteristic.getUuid().equals(DATA_CHAR_UUID)) {
                byte[] chunk = characteristic.getValue();
                if (chunk == null) return;
                int copyLen = Math.min(chunk.length, expectedResponseLength - responseOffset);
                System.arraycopy(chunk, 0, responseBuffer, responseOffset, copyLen);
                responseOffset += copyLen;
                readNextResponseChunk();
            }
        }
    };
}
