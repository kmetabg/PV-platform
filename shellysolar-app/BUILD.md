# ShellySolar Android App - Build Instructions

## Features

- **WebView** - Opens shellysolar.com as a native-feeling app
- **BLE Scanner** - Scans for nearby Shelly Gen2+ devices via Bluetooth Low Energy
- **WS Configuration** - Configures Shelly devices' outbound WebSocket via BLE RPC to connect to your ShellySolar server

## Prerequisites

- Android Studio Arctic Fox or later (recommended: Android Studio Hedgehog 2023.1+)
- Android SDK 35 (API level 35)
- JDK 17+

## Build

### Using Android Studio

1. Open the `shellysolar-app` folder in Android Studio
2. Wait for Gradle sync to complete
3. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**
4. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

### Using Command Line

```bash
cd shellysolar-app

# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease
```

## How BLE Setup Works

1. Open the app and tap the **Bluetooth icon** in the toolbar (or menu > "BLE Setup")
2. Tap **Scan for Shelly Devices** - the app scans for Shelly Gen2+ devices advertising via BLE
3. Select a device from the list
4. In the configuration dialog:
   - **Server URL**: Pre-filled with `wss://shellysolar.com/ws`
   - **Token**: Enter your ShellySolar auth token
5. Tap **Apply** to send the `Ws.SetConfig` RPC command over BLE
6. The device will enable outbound WebSocket and connect to your server

### Shelly BLE Protocol

The app communicates with Shelly devices using the JSON-RPC 2.0 protocol over BLE GATT:

- **Service UUID**: `5F6D4F53-5F52-5043-5F64-6174615F5F5F`
- **Data Characteristic**: `5F6D4F53-5F52-5043-5F64-6174615F5F5F`
- **TX Control**: `5F6D4F53-5F52-5043-5F74-785F63746C5F`
- **RX Control**: `5F6D4F53-5F52-5043-5F72-785F63746C5F`

The RPC command sent to enable outbound WebSocket:

```json
{
  "id": 1,
  "src": "shellysolar_app",
  "method": "Ws.SetConfig",
  "params": {
    "config": {
      "enable": true,
      "server": "wss://shellysolar.com/ws?token=YOUR_TOKEN",
      "ssl_ca": "*"
    }
  }
}
```

## Permissions

The app requires:
- **Internet** - WebView access
- **Bluetooth Scan/Connect** - BLE device scanning and GATT communication
- **Location** - Required by Android for BLE scanning
