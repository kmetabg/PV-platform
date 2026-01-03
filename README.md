# ☀️ Solar Monitor

A comprehensive solar energy monitoring and management platform for photovoltaic systems. Built with Node.js and SQLite, featuring real-time data collection, historical analysis, and multi-plant management.

![Version](https://img.shields.io/badge/version-4.0.0-blue)
![Node](https://img.shields.io/badge/node-%3E%3D18.0.0-green)
![License](https://img.shields.io/badge/license-MIT-orange)

## 🌟 Features

### Real-Time Monitoring
- **Live Power Tracking**: PV production, grid import/export, consumption, battery status
- **WebSocket Integration**: Real-time updates via Shelly Cloud WebSocket API
- **Animated Energy Flow**: Visual diagram showing power flow between components
- **Weather Widget**: Hourly forecast with sunrise/sunset times

### Data Collection & Storage
- **Server-Side Polling**: Automatic 10-second data collection (works 24/7, no browser needed)
- **SQLite Database**: Efficient local storage with configurable retention
- **Multi-Resolution Storage**: Raw (10s), hourly aggregates, daily summaries
- **Peak Tracking**: 15-minute average peak detection for grid optimization

### Historical Analysis
- **Graph History**: Day/Week/Month views with interactive charts
- **Generation & Usage History**: Stacked bar charts comparing sources and uses
- **Utilization Analysis**: Production vs consumption breakdown with percentages
- **Daily Summary**: Production, consumption, grid import/export, battery charge/discharge

### Multi-Plant Support
- **Unlimited Plants**: Manage multiple solar installations
- **Per-Plant Configuration**: Independent Shelly device configuration
- **Aggregated Overview**: Total live power and daily production across all plants
- **Drag-and-Drop Ordering**: Visual plant card reordering on home page

### User Management
- **Secure Authentication**: JWT-based login system
- **Multi-User Support**: Each user has their own plants and configurations
- **Password Management**: Secure password change functionality

### Device Integration
- **Shelly Cloud**: OAuth 2.0 authentication with automatic token refresh
- **Direct Connection**: Local RPC calls to Shelly devices on your network
- **Flexible Data Sources**: Configure any Shelly component (Number, Text, Path)

## 📋 Requirements

- Node.js 18.0.0 or higher
- Docker (recommended) or direct Node.js installation
- Shelly devices (Pro 3EM, Plus series, etc.)

## 🚀 Quick Start

### Docker Installation (Recommended)

```bash
# Create data directory
mkdir -p /volume1/docker/solar-monitor/data

# Run container
docker run -d \
  --name solar-monitor \
  --restart unless-stopped \
  -p 3000:3000 \
  -v /volume1/docker/solar-monitor/data:/data \
  -e TZ=Europe/Sofia \
  node:18-alpine sh -c "cd /app && node server.js"
```

### Docker Compose

```yaml
version: '3.8'
services:
  solar-monitor:
    image: node:18-alpine
    container_name: solar-monitor
    restart: unless-stopped
    ports:
      - "3000:3000"
    volumes:
      - ./data:/data
      - ./server.js:/app/server.js
      - ./public:/app/public
    working_dir: /app
    environment:
      - TZ=Europe/Sofia
      - DEBUG=false
    command: node server.js
```

### Manual Installation

```bash
# Clone or download files
cd /path/to/solar-monitor

# Install dependencies
npm install better-sqlite3

# Start server
node server.js
```

## ⚙️ Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3000` | Server port |
| `TZ` | `UTC` | Timezone for data aggregation |
| `DEBUG` | `false` | Enable verbose logging |

### Data Retention

Default retention periods (configurable in server.js):

- **Raw readings**: 48 hours
- **Hourly aggregates**: 90 days  
- **Daily summaries**: 365 days

### Shelly Device Configuration

The dashboard supports two connection methods:

#### 1. Shelly Cloud (Recommended)
- Click "Connect Shelly Cloud" in settings
- OAuth authentication with automatic token refresh
- Works from anywhere, no port forwarding needed

#### 2. Direct Connection
- Enter device IP address (e.g., `192.168.1.100`)
- Requires local network access
- Lower latency, works offline

### Data Source Types

| Type | Description | Example |
|------|-------------|---------|
| `number` | Virtual Number component | `200` (component ID) |
| `text` | Virtual Text component | `201` (component ID) |
| `path` | JSON path in device status | `em:0.total_act_power` |

## 📱 User Interface

### Home Page (`/home`)
- Aggregated totals across all plants
- Plant cards with live values and weather
- Quick access to individual dashboards

### Dashboard (`/dashboard?plant_id=1`)
- Real-time power values with color coding
- Animated energy flow diagram
- Daily summary statistics
- Weather forecast widget
- Historical charts (Graph History, Generation & Usage)
- Utilization analysis (Consumption/Production breakdown)

### Settings (⚙️ button)
- Shelly Cloud OAuth connection
- Device configuration per data source
- Timezone selection
- User management

## 🔌 Supported Devices

### Tested Devices
- Shelly Pro 3EM (grid metering)
- Shelly Pro 4PM (circuit monitoring)
- Shelly Plus 1PM (individual loads)
- Shelly Plus Plug S (appliance monitoring)

### Virtual Components
Any Shelly device with scripting support can expose values via:
- Virtual Number components
- Virtual Text components

## 📊 Database Schema

### Tables
- `users` - User accounts
- `sessions` - Active sessions
- `plants` - Plant configurations
- `readings_raw` - 10-second readings
- `readings_hourly` - Hourly aggregates
- `readings_daily` - Daily summaries
- `daily_peaks` - 15-minute peak tracking

## 🔒 Security

- JWT authentication with secure tokens
- Password hashing with crypto
- Session management with automatic cleanup
- CORS headers for API security
- No external dependencies for auth

## 🐛 Troubleshooting

### No Data Showing
1. Check Shelly Cloud connection (green dot in header)
2. Verify device IDs in settings
3. Check server logs: `docker logs solar-monitor`

### WebSocket Disconnects
- Token may have expired - reconnects automatically
- Check internet connection
- Verify Shelly Cloud server region

### Missing Historical Data
- Data only collected when server is running
- Background fetch requires valid cloud configuration
- Check database: `sqlite3 /data/solar_history.db ".tables"`

### Enable Debug Logging
```bash
docker run -e DEBUG=true ...
```

## 📄 License

MIT License - feel free to use, modify, and distribute.

## 🙏 Acknowledgments

- [Shelly](https://shelly.cloud/) for excellent smart home devices
- [Chart.js](https://www.chartjs.org/) for beautiful charts
- [Open-Meteo](https://open-meteo.com/) for weather data
- [better-sqlite3](https://github.com/WiseLibs/better-sqlite3) for fast database operations

---

Made with ☀️ for the solar energy community
