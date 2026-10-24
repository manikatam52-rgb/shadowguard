# ShadowGuard API Documentation

The **ShadowGuard REST API** enables encrypted Android-to-Android device monitoring, secure QR-code pairing, device telemetry synchronization, remote command execution, and security audit logging.

---

## 1. Authentication & Security

All API communication uses JSON over HTTPS/HTTP REST.
- **Controller Auth**: `Authorization: Bearer <JWT_CONTROLLER_TOKEN>`
- **Companion Device Auth**: `Authorization: Bearer <JWT_DEVICE_TOKEN>`
- **Rate Limiting**: Enforced at 200 requests per 15-minute window per IP (`429 Too Many Requests`).

---

## 2. Authentication Endpoints (`/api/auth`)

### 2.1 Controller Register
- **URL**: `POST /api/auth/register`
- **Request Body**:
  ```json
  {
    "username": "admin",
    "passwordHash": "admin123"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "success": true,
    "message": "Controller registered successfully",
    "data": {
      "token": "eyJhbGciOi...",
      "controllerId": "ctrl_abc123",
      "username": "admin"
    }
  }
  ```

### 2.2 Controller Login
- **URL**: `POST /api/auth/login`
- **Request Body**:
  ```json
  {
    "username": "admin",
    "passwordHash": "admin123"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "success": true,
    "message": "Login successful",
    "data": {
      "token": "eyJhbGciOi...",
      "controllerId": "ctrl_abc123",
      "username": "admin"
    }
  }
  ```

---

## 3. Pairing Endpoints (`/api/pairing`)

### 3.1 Generate One-Time Pairing QR Token
- **Headers**: `Authorization: Bearer <CONTROLLER_TOKEN>`
- **URL**: `POST /api/pairing/generate-token`
- **Request Body**:
  ```json
  {
    "expiresInMinutes": 15
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "success": true,
    "message": "Pairing token generated",
    "data": {
      "token": "842910",
      "qrCodePayload": "{\"token\":\"842910\",\"controllerId\":\"ctrl_abc123\",\"expiresAt\":\"2025-01-01T12:15:00.000Z\"}",
      "expiresAt": "2025-01-01T12:15:00.000Z"
    }
  }
  ```

### 3.2 Companion Device Pairing
- **URL**: `POST /api/pairing/pair-device`
- **Request Body**:
  ```json
  {
    "token": "842910",
    "deviceName": "Google Pixel 8",
    "deviceModel": "Pixel 8",
    "androidVersion": "14 (API 34)",
    "fcmToken": "optional_fcm_token_string"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "success": true,
    "message": "Device paired successfully",
    "data": {
      "deviceId": "dev_x7y8z9",
      "controllerId": "ctrl_abc123",
      "deviceToken": "eyJhbGciOi...",
      "message": "Paired"
    }
  }
  ```

---

## 4. Device Management & Telemetry Endpoints (`/api/devices`)

### 4.1 List Paired Devices
- **Headers**: `Authorization: Bearer <CONTROLLER_TOKEN>`
- **URL**: `GET /api/devices`
- **Response** (`200 OK`):
  ```json
  {
    "success": true,
    "data": [
      {
        "id": "dev_x7y8z9",
        "deviceName": "Google Pixel 8",
        "deviceModel": "Pixel 8",
        "androidVersion": "14 (API 34)",
        "status": "ONLINE",
        "batteryLevel": 85,
        "isCharging": true,
        "batteryHealth": "Good",
        "availableStorageGb": 42.5,
        "totalStorageGb": 128.0,
        "availableRamGb": 3.2,
        "totalRamGb": 8.0,
        "lastSeenAt": "2025-01-01T12:00:00.000Z"
      }
    ]
  }
  ```

### 4.2 Submit Device Telemetry
- **Headers**: `Authorization: Bearer <DEVICE_TOKEN>`
- **URL**: `POST /api/devices/:deviceId/telemetry`
- **Request Body**:
  ```json
  {
    "batteryLevel": 85,
    "isCharging": true,
    "batteryHealth": "Good",
    "batteryTemperature": 31.5,
    "availableStorageGb": 42.5,
    "totalStorageGb": 128.0,
    "availableRamGb": 3.2,
    "totalRamGb": 8.0,
    "networkType": "Wi-Fi",
    "location": {
      "latitude": 37.7749,
      "longitude": -122.4194,
      "accuracy": 8.5,
      "timestamp": "2025-01-01T12:00:00.000Z"
    }
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "success": true,
    "message": "Telemetry updated"
  }
  ```

### 4.3 Get Telemetry History
- **Headers**: `Authorization: Bearer <CONTROLLER_TOKEN>`
- **URL**: `GET /api/devices/:deviceId/telemetry/history?limit=20`

---

## 5. Remote Commands (`/api/devices/:deviceId/commands`)

### 5.1 Issue Command
- **Headers**: `Authorization: Bearer <CONTROLLER_TOKEN>`
- **URL**: `POST /api/devices/:deviceId/commands`
- **Supported Command Types**:
  - `PING`: Immediate status response.
  - `REFRESH_LOCATION`: Trigger immediate GPS fix.
  - `SOUND_ALARM`: Play alert tone on companion device.
  - `PAUSE_MONITORING`: Temporarily pause telemetry gathering.
  - `REVOKE_ACCESS`: Unpair and revoke authorization.
- **Request Body**:
  ```json
  {
    "commandType": "REFRESH_LOCATION",
    "payload": null
  }
  ```

### 5.2 Companion Poll Pending Commands
- **Headers**: `Authorization: Bearer <DEVICE_TOKEN>`
- **URL**: `GET /api/devices/:deviceId/pending-commands`

### 5.3 Companion Acknowledge Command
- **Headers**: `Authorization: Bearer <DEVICE_TOKEN>`
- **URL**: `POST /api/devices/:deviceId/commands/:commandId/ack`
- **Request Body**:
  ```json
  {
    "result": "Location updated successfully",
    "status": "EXECUTED"
  }
  ```

---

## 6. Device Revocation & Audit Logs

### 6.1 Revoke Device Access
- **URL**: `POST /api/devices/:deviceId/revoke`

### 6.2 Get Security Audit Logs
- **Headers**: `Authorization: Bearer <CONTROLLER_TOKEN>`
- **URL**: `GET /api/audit-logs`
