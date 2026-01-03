# Changelog

All notable changes to Solar Monitor are documented in this file.

## [4.0.0] - 2025-01-02

### 🎉 Initial Public Release

#### Core Features
- **Multi-Plant Support**: Manage unlimited solar installations from a single dashboard
- **User Authentication**: Secure JWT-based login with multi-user support
- **SQLite Database**: Efficient local storage with no external dependencies

#### Real-Time Monitoring
- Live power values (PV, Grid, Consumption, Battery)
- Animated energy flow diagram with proportional speeds
- WebSocket connection to Shelly Cloud for instant updates
- Connection status indicator with automatic reconnection

#### Data Collection
- **Server-Side Polling**: Background data collection every 10 seconds
- **Dual Connection Methods**: Shelly Cloud (OAuth) and Direct (local IP)
- **Automatic Token Refresh**: OAuth tokens refreshed before expiration
- **Rate Limiting**: Respects Shelly Cloud 1 req/sec limit

#### Historical Analysis
- **Graph History**: Day/Week/Month views with Chart.js
- **Generation & Usage History**: Stacked bar charts
- **Utilization Analysis**: Production vs consumption breakdown
- **Peak Tracking**: 15-minute average peaks for grid optimization

#### Weather Integration
- Hourly weather forecast via Open-Meteo API
- Sunrise/sunset times with styled SVG icons
- Dynamic hour display based on screen width
- Temperature display per hour

#### User Interface
- Responsive design for desktop, tablet, and mobile
- Dark theme optimized for visibility
- Drag-and-drop plant card reordering
- Consistent date picker design across all charts

#### Data Sources
- PV Power (solar production)
- Grid Power (import/export)
- Home Consumption
- Battery SOC (state of charge)
- Battery Power (charge/discharge)
- Inverter Status (charging/discharging/idle)

### Technical Details
- Node.js 18+ with better-sqlite3
- No external authentication services
- Docker-ready deployment
- Synology NAS compatible
- Configurable data retention

---

## Roadmap

### Planned Features
- [ ] ENTSO-E electricity price integration
- [ ] Revenue calculation dashboard
- [ ] Solar production forecasting
- [ ] Mobile app (PWA)
- [ ] Email notifications
- [ ] Export to CSV/Excel
- [ ] Multiple user roles
- [ ] API rate limiting
- [ ] Backup/restore functionality

---

## Version History

| Version | Date | Description |
|---------|------|-------------|
| 4.0.0 | 2025-01-02 | Initial public release |
| 3.x | Internal | Development versions |
| 2.x | Internal | Prototype versions |
| 1.x | Internal | Proof of concept |
