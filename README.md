# ShadowGuard: Android-to-Android Device Monitoring System

**ShadowGuard** is a secure, complete Android-to-Android device monitoring system comprising two independent Kotlin Jetpack Compose applications (`DeviceController` and `DeviceCompanion`) and an express Node.js/PostgreSQL backend API server.

---

## Architecture Overview

```
 ┌────────────────────────┐                   ┌────────────────────────┐
 │   DeviceController     │                   │    DeviceCompanion     │
 │ (com.shadowguard.     │                   │ (com.shadowguard.      │
 │    controller)         │                   │    companion)          │
 └───────────┬────────────┘                   └───────────▲────────────┘
             │                                            │
   HTTPS / REST / Auth JWT                      HTTPS / REST / Auth JWT
             │                                            │
             ▼                                            │
 ┌────────────────────────────────────────────────────────┴────────────┐
 │                  Node.js / Express Backend Server                   │
 │                PostgreSQL / SQLite Database Schema                 │
 └─────────────────────────────────────────────────────────────────────┘
```

### 1. `DeviceController` (`:controller`)
- Monitoring Dashboard with dark Material 3 interface.
- Secure QR Code token generator & camera scanner.
- Interactive OpenStreetMap view (`OsmDroid`) showing authorized location, pin, accuracy, and timestamp.
- Telemetry metrics: Battery %, charging state, storage, RAM, OS version, network type.
- Remote command panel: Ping, Request Location, Sound Alarm, Pause Monitoring, Revoke Access.
- Security audit logging view.

### 2. `DeviceCompanion` (`:companion`)
- Companion app installed on target monitored device.
- Authenticated QR scanner / code entry pairing.
- Permission Inspector with interactive action buttons (Location, Background Location, Notifications, Battery Optimization exemption).
- Ongoing Foreground Service (`MonitoringForegroundService`) with persistent notification.
- Scheduled WorkManager task (`StatusSyncWorker`) for periodic background sync.
- Boot Receiver (`BootReceiver`) to restart monitoring on device boot.
- Device owner controls: Pause/Resume toggle and Instant Local Access Revocation / Unpair.

### 3. Backend Server (`backend/`)
- Technology: Node.js, Express, PostgreSQL / SQLite auto-fallback, Firebase Cloud Messaging.
- Secure bcrypt password hashing, JWT authentication tokens, expiring 1-time pairing tokens.
- Express rate-limiting & Helmet security headers.
- Real-time WebSocket support & security audit trail logging.

---

## Quick Start Commands

### Build Android Apps (Windows)
```cmd
.\gradlew.bat :controller:assembleDebug :companion:assembleDebug
```

### Start Backend Server
```cmd
cd backend
npm install
copy .env.example .env
npm start
```
Before starting, set `JWT_SECRET` in `backend/.env` to a long, randomly generated value.

### Deploy Backend to Render

1. Push this project to a GitHub repository and create a new Render Blueprint from it. Render will detect the root `render.yaml`.
2. The Blueprint provisions a PostgreSQL database and links it to the backend using Render's private connection string. Render generates `JWT_SECRET` for the service.
3. On startup, the backend applies `backend/schema.sql` to create the tables and indexes.
4. After deployment, check the service's `onrender.com` URL. A successful health check returns the ShadowGuard API server status. REST endpoints are under `/api`; WebSocket clients connect to `/ws` on the same host using `wss://`.

Firebase Cloud Messaging is optional; set `FIREBASE_SERVICE_ACCOUNT_JSON` in the Render service environment if push notifications are required. Do not commit database credentials, Firebase credentials, or JWT secrets.

The Blueprint uses Render's free web-service and PostgreSQL plans for testing. Free web services may spin down when idle, briefly delaying reconnection, and free PostgreSQL databases expire after 30 days. Upgrade the database to a paid plan before relying on it for long-term data storage.

For complete setup details and testing procedures, refer to:
- `BUILD_INSTRUCTIONS.md`
- `TESTING_INSTRUCTIONS.md`
- `API_DOCUMENTATION.md`
