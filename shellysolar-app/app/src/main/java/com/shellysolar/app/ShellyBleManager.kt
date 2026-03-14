package com.shellysolar.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Handles BLE GATT communication with Shelly devices using the Shelly RPC-over-BLE protocol.
 *
 * Protocol:
 * 1. Write data length (little-endian u32) to TX Control characteristic
 * 2. Write JSON-RPC payload in chunks to Data characteristic
 * 3. Read response length from RX Control characteristic (via notification)
 * 4. Read response data in chunks from Data characteristic
 */
class ShellyBleManager(private val context: Context) {

    companion object {
        private const val TAG = "ShellyBleManager"

        // Shelly BLE GATT UUIDs
        val SHELLY_SERVICE_UUID: UUID = UUID.fromString("5F6D4F53-5F52-5043-5F64-6174615F5F5F")
        val DATA_CHAR_UUID: UUID = UUID.fromString("5F6D4F53-5F52-5043-5F64-6174615F5F5F")
        val TX_CTRL_CHAR_UUID: UUID = UUID.fromString("5F6D4F53-5F52-5043-5F74-785F63746C5F")
        val RX_CTRL_CHAR_UUID: UUID = UUID.fromString("5F6D4F53-5F52-5043-5F72-785F63746C5F")

        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val MAX_CHUNK_SIZE = 512
        private const val RPC_TIMEOUT_MS = 15000L
    }

    interface Callback {
        fun onConnected()
        fun onDisconnected()
        fun onRpcResponse(response: String)
        fun onError(message: String)
    }

    private var gatt: BluetoothGatt? = null
    private var callback: Callback? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var txCtrlChar: BluetoothGattCharacteristic? = null
    private var rxCtrlChar: BluetoothGattCharacteristic? = null

    private var pendingRequest: ByteArray? = null
    private var pendingRequestOffset = 0
    private var responseBuffer = ByteArray(0)
    private var expectedResponseLength = 0
    private var responseOffset = 0

    private val handler = Handler(Looper.getMainLooper())
    private var rpcId = 1

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, callback: Callback) {
        this.callback = callback
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        callback = null
    }

    fun sendRpc(method: String, params: String? = null): Int {
        val id = rpcId++
        val json = if (params != null) {
            """{"id":$id,"src":"shellysolar_app","method":"$method","params":$params}"""
        } else {
            """{"id":$id,"src":"shellysolar_app","method":"$method"}"""
        }
        Log.d(TAG, "Sending RPC: $json")
        sendData(json.toByteArray(Charsets.UTF_8))

        handler.postDelayed({
            if (expectedResponseLength == 0 && responseOffset == 0) {
                callback?.onError("RPC timeout for $method")
            }
        }, RPC_TIMEOUT_MS)

        return id
    }

    @SuppressLint("MissingPermission")
    private fun sendData(data: ByteArray) {
        val txCtrl = txCtrlChar ?: run {
            callback?.onError("TX Control characteristic not found")
            return
        }
        val dataC = dataChar ?: run {
            callback?.onError("Data characteristic not found")
            return
        }

        pendingRequest = data
        pendingRequestOffset = 0
        responseBuffer = ByteArray(0)
        expectedResponseLength = 0
        responseOffset = 0

        // Step 1: Write length to TX Control
        val lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array()
        txCtrl.value = lengthBytes
        txCtrl.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt?.writeCharacteristic(txCtrl)
    }

    @SuppressLint("MissingPermission")
    private fun writeNextChunk() {
        val data = pendingRequest ?: return
        val dataC = dataChar ?: return

        if (pendingRequestOffset >= data.size) {
            pendingRequest = null
            Log.d(TAG, "All data sent, waiting for response...")
            return
        }

        val remaining = data.size - pendingRequestOffset
        val chunkSize = minOf(remaining, MAX_CHUNK_SIZE)
        val chunk = data.copyOfRange(pendingRequestOffset, pendingRequestOffset + chunkSize)
        pendingRequestOffset += chunkSize

        dataC.value = chunk
        dataC.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt?.writeCharacteristic(dataC)
    }

    @SuppressLint("MissingPermission")
    private fun readNextResponseChunk() {
        if (responseOffset >= expectedResponseLength) {
            val responseStr = responseBuffer.toString(Charsets.UTF_8)
            Log.d(TAG, "RPC Response: $responseStr")
            handler.post { callback?.onRpcResponse(responseStr) }
            return
        }
        gatt?.readCharacteristic(dataChar)
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    gatt.requestMtu(517)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    handler.post { callback?.onDisconnected() }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { callback?.onError("Service discovery failed: $status") }
                return
            }

            // Find the Shelly RPC service - iterate all services to find one with our characteristics
            var foundService = gatt.getService(SHELLY_SERVICE_UUID)

            if (foundService == null) {
                // Try to find service by iterating
                for (service in gatt.services) {
                    val hasData = service.getCharacteristic(DATA_CHAR_UUID) != null
                    val hasTx = service.getCharacteristic(TX_CTRL_CHAR_UUID) != null
                    val hasRx = service.getCharacteristic(RX_CTRL_CHAR_UUID) != null
                    if (hasData || hasTx || hasRx) {
                        foundService = service
                        break
                    }
                }
            }

            if (foundService == null) {
                handler.post { callback?.onError("Shelly RPC service not found") }
                return
            }

            dataChar = foundService.getCharacteristic(DATA_CHAR_UUID)
            txCtrlChar = foundService.getCharacteristic(TX_CTRL_CHAR_UUID)
            rxCtrlChar = foundService.getCharacteristic(RX_CTRL_CHAR_UUID)

            if (dataChar == null || txCtrlChar == null || rxCtrlChar == null) {
                handler.post { callback?.onError("Required BLE characteristics not found") }
                return
            }

            // Enable notifications on RX Control
            val rxCtrl = rxCtrlChar!!
            gatt.setCharacteristicNotification(rxCtrl, true)
            val descriptor = rxCtrl.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                handler.post { callback?.onConnected() }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handler.post { callback?.onConnected() }
            } else {
                handler.post { callback?.onError("Failed to enable notifications") }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { callback?.onError("Write failed: $status") }
                return
            }

            when (characteristic.uuid) {
                TX_CTRL_CHAR_UUID -> {
                    // Length written, now send data chunks
                    writeNextChunk()
                }
                DATA_CHAR_UUID -> {
                    // Chunk written, send next
                    writeNextChunk()
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == RX_CTRL_CHAR_UUID) {
                // Response length notification
                val value = characteristic.value
                if (value != null && value.size >= 4) {
                    expectedResponseLength = ByteBuffer.wrap(value)
                        .order(ByteOrder.LITTLE_ENDIAN).int
                    responseBuffer = ByteArray(expectedResponseLength)
                    responseOffset = 0
                    Log.d(TAG, "Response length: $expectedResponseLength")
                    readNextResponseChunk()
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { callback?.onError("Read failed: $status") }
                return
            }

            if (characteristic.uuid == DATA_CHAR_UUID) {
                val chunk = characteristic.value ?: return
                val copyLen = minOf(chunk.size, expectedResponseLength - responseOffset)
                System.arraycopy(chunk, 0, responseBuffer, responseOffset, copyLen)
                responseOffset += copyLen
                readNextResponseChunk()
            }
        }
    }
}
