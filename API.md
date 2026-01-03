# 📡 Solar Monitor API Documentation

Base URL: `http://localhost:3000`

## Authentication

Most endpoints require authentication via JWT token. Include the token in the `Authorization` header:

```
Authorization: Bearer <token>
```

---

## 🔐 Auth Endpoints

### Check Setup Status
```http
GET /api/auth/status
```

**Response:**
```json
{
  "needsSetup": true,
  "message": "No users registered"
}
```

### Register User
```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securepassword"
}
```

**Response:**
```json
{
  "success": true,
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "email": "user@example.com"
  }
}
```

### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securepassword"
}
```

**Response:**
```json
{
  "success": true,
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "email": "user@example.com"
  }
}
```

### Get Current User
```http
GET /api/auth/me
Authorization: Bearer <token>
```

**Response:**
```json
{
  "id": 1,
  "email": "user@example.com"
}
```

### Change Password
```http
POST /api/auth/change-password
Authorization: Bearer <token>
Content-Type: application/json

{
  "currentPassword": "oldpassword",
  "newPassword": "newpassword"
}
```

### Logout
```http
POST /api/auth/logout
Authorization: Bearer <token>
```

---

## 🌱 Plant Endpoints

### List All Plants
```http
GET /api/plants
Authorization: Bearer <token>
```

**Response:**
```json
{
  "plants": [
    {
      "id": 1,
      "name": "Home",
      "location": "Greece",
      "timezone": "Europe/Athens",
      "latitude": 41.9876,
      "longitude": 23.7294,
      "created_at": "2025-01-01T12:00:00Z"
    }
  ]
}
```

### Create Plant
```http
POST /api/plants
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Home",
  "location": "Greece",
  "timezone": "Europe/Athens",
  "latitude": 41.9876,
  "longitude": 23.7294
}
```

### Update Plant
```http
PUT /api/plants?plant_id=1
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Updated Name",
  "location": "New Location"
}
```

### Delete Plant
```http
DELETE /api/plants?plant_id=1
Authorization: Bearer <token>
```

### Plants Overview (Home Page Data)
```http
GET /api/plants/overview
Authorization: Bearer <token>
```

**Response:**
```json
{
  "plants": [
    {
      "id": 1,
      "name": "Home",
      "location": "Greece",
      "timezone": "Europe/Athens",
      "latitude": 41.9876,
      "longitude": 23.7294,
      "live": {
        "pv": 2120,
        "grid": 74,
        "consumption": 484,
        "batterySoc": 37,
        "batteryPower": 1600,
        "timestamp": 1704189600,
        "age": 4
      },
      "today": {
        "production": 1800,
        "consumption": 470
      }
    }
  ],
  "totals": {
    "livePV": 2120,
    "liveConsumption": 484,
    "todayProduction": 1800,
    "todayConsumption": 470
  }
}
```

---

## ⚙️ Configuration Endpoints

### Get Plant Configuration
```http
GET /api/config?plant_id=1
Authorization: Bearer <token>
```

**Response:**
```json
{
  "cloudServer": "shelly-103-eu.shelly.cloud",
  "cloudAccessToken": "...",
  "cloudRefreshToken": "...",
  "cloudTokenExpires": 1704276000,
  "sources": {
    "pv": {
      "connType": "cloud",
      "device": "shellyproem50-xxx",
      "sourceType": "path",
      "componentId": "em:0.total_act_power"
    },
    "grid": {
      "connType": "cloud",
      "device": "shellypro3em-xxx",
      "sourceType": "path",
      "componentId": "em:0.total_act_power"
    }
  }
}
```

### Save Plant Configuration
```http
POST /api/config?plant_id=1
Authorization: Bearer <token>
Content-Type: application/json

{
  "cloudServer": "shelly-103-eu.shelly.cloud",
  "sources": {
    "pv": {
      "connType": "cloud",
      "device": "shellyproem50-xxx",
      "sourceType": "path",
      "componentId": "em:0.total_act_power"
    }
  }
}
```

---

## 📊 Data Endpoints

### Store Reading (from dashboard)
```http
POST /api/store-reading
Content-Type: application/json

{
  "plant_id": 1,
  "pv": 2120,
  "grid": 74,
  "consumption": 484,
  "batterySoc": 37,
  "batteryPower": 1600,
  "inverterStatus": "charging"
}
```

### Live Update (cache only, no DB write)
```http
POST /api/live-update
Content-Type: application/json

{
  "plant_id": 1,
  "pv": 2120,
  "grid": 74,
  "consumption": 484,
  "batterySoc": 37,
  "batteryPower": 1600
}
```

### Get History (Chart Data)
```http
GET /api/history?plant_id=1&range=day
GET /api/history?plant_id=1&range=day&date=2025-01-02
GET /api/history?plant_id=1&range=week
GET /api/history?plant_id=1&range=month
```

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `plant_id` | int | Plant ID |
| `range` | string | `hour`, `day`, `week`, `month`, `year` |
| `date` | string | Optional: specific date (YYYY-MM-DD) |

**Response:**
```json
{
  "range": "day",
  "date": "2025-01-02",
  "count": 48,
  "data": [
    {
      "timestamp": 1704189600000,
      "pv_power": 2120,
      "grid_power": 74,
      "consumption": 484,
      "battery_soc": 37,
      "battery_power": 1600
    }
  ]
}
```

### Get Daily Summary
```http
GET /api/daily?plant_id=1&days=7
GET /api/daily?plant_id=1&start=2025-01-01&end=2025-01-07
```

**Response:**
```json
{
  "data": [
    {
      "date": "2025-01-02",
      "dayName": "Thu",
      "production": 5420,
      "consumption": 3200,
      "gridImport": 850,
      "gridExport": 2100,
      "batteryCharge": 1500,
      "batteryDischarge": 800
    }
  ]
}
```

### Get Daily Stats (with comparison)
```http
GET /api/daily-stats?plant_id=1&date=2025-01-02
```

**Response:**
```json
{
  "current": {
    "date": "2025-01-02",
    "pv_energy_wh": 5420,
    "consumption_wh": 3200,
    "grid_import_wh": 850,
    "grid_export_wh": 2100,
    "battery_charge_wh": 1500,
    "battery_discharge_wh": 800,
    "pv_power_max": 3500,
    "consumption_max": 2100,
    "battery_soc_min": 20,
    "battery_soc_max": 85
  },
  "previous": {
    "date": "2025-01-01",
    "pv_energy_wh": 4800
  }
}
```

### Get Peaks (15-minute averages)
```http
GET /api/peaks?plant_id=1&period=day&date=2025-01-02
GET /api/peaks?plant_id=1&period=month&date=2025-01-02
GET /api/peaks?plant_id=1&period=year&date=2025-01-02
```

**Response:**
```json
{
  "peaks": [
    {
      "date": "2025-01-02",
      "time": "12:15",
      "pv_avg": 3420,
      "grid_avg": -1200,
      "consumption_avg": 450
    }
  ],
  "maxPvPeak": {
    "date": "2025-01-02",
    "time": "12:15",
    "pv_avg": 3420
  },
  "maxGridPeak": {
    "date": "2025-01-02",
    "time": "19:30",
    "grid_avg": 2100
  }
}
```

---

## 🌐 Timezone Endpoint

### Get Timezone
```http
GET /api/timezone
```

**Response:**
```json
{
  "timezone": "Europe/Sofia"
}
```

### Set Timezone
```http
POST /api/timezone
Content-Type: application/json

{
  "timezone": "Europe/Athens"
}
```

---

## 🔧 Utility Endpoints

### Health Check
```http
GET /health
```

**Response:**
```json
{
  "status": "ok",
  "database": "connected",
  "uptime": 3600,
  "readings": {
    "raw": 8640,
    "hourly": 720,
    "daily": 30
  }
}
```

### Version
```http
GET /api/version
```

**Response:**
```json
{
  "version": "4.0.0"
}
```

### Database Stats
```http
GET /api/stats
```

**Response:**
```json
{
  "raw_count": 8640,
  "hourly_count": 720,
  "daily_count": 30,
  "peaks_count": 96,
  "plants_count": 2,
  "users_count": 1
}
```

---

## 🔌 Shelly Proxy Endpoints

### Single RPC Call
```http
POST /api/shelly
Content-Type: application/json

{
  "ip": "192.168.1.100",
  "method": "Shelly.GetStatus"
}
```

### Batch RPC Calls
```http
POST /api/batch
Content-Type: application/json

{
  "calls": [
    {
      "key": "pv",
      "connType": "direct",
      "device": "192.168.1.100",
      "sourceType": "number",
      "componentId": "200"
    },
    {
      "key": "grid",
      "connType": "cloud",
      "device": "shellypro3em-xxx",
      "sourceType": "path",
      "componentId": "em:0.total_act_power"
    }
  ],
  "storeHistory": true,
  "plant_id": 1,
  "cloudServer": "shelly-103-eu.shelly.cloud",
  "cloudAccessToken": "..."
}
```

---

## 📝 Response Codes

| Code | Description |
|------|-------------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 409 | Conflict (e.g., email exists) |
| 500 | Server Error |

---

## 🔄 WebSocket (Shelly Cloud)

The dashboard connects directly to Shelly Cloud WebSocket for real-time updates:

```
wss://shelly-103-eu.shelly.cloud:6113/shelly/wss/hk_sock?t=<access_token>
```

Events received:
- `NotifyStatus` - Device status updates
- `NotifyFullStatus` - Complete device status

---

## 📁 Static Files

| Path | Description |
|------|-------------|
| `/` or `/home` | Home page (plant overview) |
| `/dashboard` | Dashboard (single plant) |
| `/dashboard?plant_id=1` | Dashboard for specific plant |

