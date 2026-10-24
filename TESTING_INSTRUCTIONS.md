# ShadowGuard End-to-End Physical Device Testing Guide

This guide details step-by-step instructions for installing, pairing, testing, and revoking access using two physical Android devices running **DeviceController** and **DeviceCompanion**.

---

## 1. Network Setup & Prerequisites

1. Ensure your PC running the backend server and both physical Android devices are connected to the **same Wi-Fi network**.
2. Find your PC's local IP address on Windows:
   ```cmd
   ipconfig
   ```
   *Example output*: `IPv4 Address: 192.168.1.150`
3. Start the backend server on PC:
   ```cmd
   cd C:\Users\surendra\AndroidStudioProjects\shadowguard\backend
   npm start
   ```
4. Verify server accessibility from mobile browser by opening `http://192.168.1.150:3000/`. You should see `{"name":"ShadowGuard API Server","status":"ONLINE",...}`.

---

## 2. Installation on Physical Android Devices

### Device 1: Controller Device (Monitoring Dashboard)
1. Install `controller-debug.apk` on Device 1 via ADB or USB transfer:
   ```cmd
   adb -s <DEVICE_1_ID> install controller/build/outputs/apk/debug/controller-debug.apk
   ```
2. Launch **ShadowGuard Controller**.
3. Tap the Settings icon in the top right and set Server URL to: `http://192.168.1.150:3000/`
4. Tap **Register Account** or Sign In with credentials (`admin` / `admin123`).

### Device 2: Companion Device (Monitored Target)
1. Install `companion-debug.apk` on Device 2 via ADB or USB transfer:
   ```cmd
   adb -s <DEVICE_2_ID> install companion/build/outputs/apk/debug/companion-debug.apk
   ```
2. Launch **ShadowGuard Companion**.
3. Tap the Settings icon in the top right and set Server URL to: `http://192.168.1.150:3000/`

---

## 3. Secure QR-Code Device Pairing Flow

1. **On Controller App (Device 1)**:
   - Tap **Pair Companion Device**.
   - Select the **Display QR Code** tab.
   - A unique QR code and 6-digit authentication token (e.g., `842910`) will be generated.
2. **On Companion App (Device 2)**:
   - Tap **Scan Controller QR Code**.
   - Grant Camera permission if prompted.
   - Point Device 2's camera at Device 1's screen.
   - *(Alternative)*: Type the 6-digit authentication code manually and tap **Pair Device**.
3. **Verification**:
   - Device 2 will pair instantly and navigate to the **Companion Dashboard**.
   - Device 1's Controller Dashboard will update to display Device 2 under **Paired Companion Devices** with status `ONLINE`.

---

## 4. Permission Configuration & Foreground Service Inspection

1. **On Companion Device (Device 2)**:
   - In the **Permissions & Protection Status** section:
     - Tap **Grant** for **Location Access** -> Grant *While using the app* and *Always allow* if background location is prompted.
     - Tap **Grant** for **Notifications Permission** -> Allow notifications.
     - Tap **Grant** for **Battery Optimization Exemption** -> Select *Unrestricted* / Allow.
2. **Persistent Ongoing Notification Verification**:
   - Swipe down the notification drawer on Device 2.
   - Verify the ongoing persistent notification is displayed:
     > **ShadowGuard Companion**
     > *Monitoring Active: ShadowGuard companion device status and authorized location sync active.*
   - This complies with Android foreground service requirements.

---

## 5. Testing Remote Commands & Status Telemetry

1. **On Controller App (Device 1)**:
   - Tap on the paired companion device card in the Dashboard.
   - Observe real-time telemetry:
     - Battery Percentage & Charging state
     - Free Storage (GB) & Free RAM (GB)
     - Device Model & Android Version
     - Authorized Location displayed on the interactive OpenStreetMap view.
2. **Execute Commands**:
   - **Ping Status**: Tap `Ping Status`. Verify telemetry is refreshed.
   - **Request Location**: Tap `Req Location`. Verify latitude, longitude, and accuracy update on the map.
   - **Sound Alarm**: Tap `Sound Alarm`. Verify Companion Device 2 plays an alert tone.
   - **Pause Monitoring**: Tap `Pause`. Verify status changes to `PAUSED` and telemetry pauses.

---

## 6. Access Revocation & Unpairing Testing

1. **Revoke Access from Controller**:
   - On Device 1 (Controller), open the device detail screen and tap the Red Trash icon / **Revoke Access**.
   - Confirm revocation.
   - Verify Device 2 receives the revocation command, stops its foreground service, and resets to the pairing screen.
2. **Revoke Access Locally from Companion**:
   - On Device 2 (Companion), tap **Revoke Access & Unpair Device**.
   - Confirm unpairing.
   - Verify local session tokens are wiped, background services stop, and access is revoked on the backend.
