# ShadowGuard Windows Build & Setup Instructions

This document provides step-by-step instructions to compile, build, and run the **ShadowGuard Device Controller App**, **Device Companion App**, and **Node.js Backend Server** on Windows.

---

## 1. Prerequisites

### 1.1 Android Development Tools
- **JDK**: Java Development Kit 17 or higher (JDK 17/21 installed and configured in `JAVA_HOME`).
- **Android Studio**: Android Studio Jellyfish / Ladybug / Meerkat or later.
- **Android SDK Platform 35**: Installed via SDK Manager in Android Studio.

### 1.2 Backend Prerequisites
- **Node.js**: Node.js v18.0.0 or higher ([Download Node.js](https://nodejs.org/)).
- **PostgreSQL** (Optional): PostgreSQL 14+ installed locally or running via Docker, OR run with automatic built-in SQLite database mode.

---

## 2. Building the Node.js Backend Server

1. Open PowerShell or Command Prompt and navigate to the project root:
   ```cmd
   cd C:\Users\surendra\AndroidStudioProjects\shadowguard\backend
   ```

2. Install Node.js dependencies:
   ```cmd
   npm install
   ```

3. Configure authentication and the database:
   - Copy `.env.example` to `.env` and set `JWT_SECRET` to a long, randomly generated value. The server will not start without it.
   - For **SQLite** (Default, zero database setup): Set `DB_DRIVER=sqlite`. The backend automatically creates `shadowguard.db` on launch.
   - For **PostgreSQL**:
     - Create a database in PostgreSQL: `CREATE DATABASE shadowguard;`
     - Apply schema: `psql -U postgres -d shadowguard -f schema.sql`
     - Set the database configuration in `.env`:
       ```env
       PORT=3000
       DB_DRIVER=postgres
       PGHOST=localhost
       PGUSER=postgres
       PGPASSWORD=your_password
       PGDATABASE=shadowguard
       PGPORT=5432
       ```

4. Start the backend server:
   ```cmd
   npm start
   ```
   You will see:
   ```text
   =================================================
     SHADOWGUARD MONITORING BACKEND RUNNING
     HTTP Server: http://0.0.0.0:3000
     WebSocket:   ws://0.0.0.0:3000/ws
   =================================================
   ```

---

## 3. Building the Android Applications on Windows

The repository contains two separately buildable subprojects:
- `:controller` -> **DeviceController** (`com.shadowguard.controller`)
- `:companion` -> **DeviceCompanion** (`com.shadowguard.companion`)

### 3.1 Option A: Building via Gradle Command Line (PowerShell / CMD)

Navigate to the project root directory:
```cmd
cd C:\Users\surendra\AndroidStudioProjects\shadowguard
```

1. **Build Controller APK**:
   ```cmd
   .\gradlew.bat :controller:assembleDebug
   ```
   *Generated APK path*: `C:\Users\surendra\AndroidStudioProjects\shadowguard\controller\build\outputs\apk\debug\controller-debug.apk`

2. **Build Companion APK**:
   ```cmd
   .\gradlew.bat :companion:assembleDebug
   ```
   *Generated APK path*: `C:\Users\surendra\AndroidStudioProjects\shadowguard\companion\build\outputs\apk\debug\companion-debug.apk`

3. **Build Both Applications Simultaneously**:
   ```cmd
   .\gradlew.bat :controller:assembleDebug :companion:assembleDebug
   ```

---

### 3.2 Option B: Building via Android Studio GUI

1. Open Android Studio and select **File -> Open...**
2. Choose directory `C:\Users\surendra\AndroidStudioProjects\shadowguard`.
3. Wait for Gradle sync to complete.
4. In the run configuration dropdown at the top toolbar, choose:
   - Select `controller` to run/debug **DeviceController**.
   - Select `companion` to run/debug **DeviceCompanion**.
5. Click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.

Debug builds allow cleartext HTTP for local development. Release builds require HTTPS/WSS; configure the deployed backend URL accordingly.
