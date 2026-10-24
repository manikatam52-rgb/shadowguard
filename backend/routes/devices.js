const express = require('express');
const { randomUUID } = require('crypto');
const db = require('../db');
const { authenticateController, authenticateDevice, authenticateAny } = require('../middleware/auth');
const fcmService = require('../fcmService');
const wsManager = require('../wsManager');

const router = express.Router();

function requireControllerDevice(req, res, next) {
  db.query(
    'SELECT id FROM devices WHERE id = $1 AND controller_id = $2 AND status != $3',
    [req.params.deviceId, req.controller.controllerId, 'REVOKED']
  ).then(devices => {
    if (!devices.length) {
      return res.status(404).json({ success: false, message: 'Device not found' });
    }
    next();
  }).catch(error => {
    console.error(error);
    res.status(500).json({ success: false, message: 'Failed to verify device access' });
  });
}

function requireActiveDevice(req, res, next) {
  if (req.device.deviceId !== req.params.deviceId) {
    return res.status(403).json({ success: false, message: 'Device authorization does not match this device' });
  }

  db.query(
    'SELECT id FROM devices WHERE id = $1 AND status != $2',
    [req.params.deviceId, 'REVOKED']
  ).then(devices => {
    if (!devices.length) {
      return res.status(403).json({ success: false, message: 'Device access has been revoked' });
    }
    next();
  }).catch(error => {
    console.error(error);
    res.status(500).json({ success: false, message: 'Failed to verify device access' });
  });
}

// List all paired devices for controller
router.get('/', authenticateController, async (req, res) => {
  try {
    const controllerId = req.controller.controllerId;
    const devices = await db.query(
      'SELECT * FROM devices WHERE controller_id = $1 AND status != $2 ORDER BY last_seen_at DESC',
      [controllerId, 'REVOKED']
    );

    const deviceList = [];
    for (const d of devices) {
      const telemetryList = await db.query(
        'SELECT * FROM device_telemetry WHERE device_id = $1 ORDER BY timestamp DESC LIMIT 1',
        [d.id]
      );

      let lastTelemetry = null;
      if (telemetryList && telemetryList.length > 0) {
        const t = telemetryList[0];
        lastTelemetry = {
          deviceId: t.device_id,
          batteryLevel: t.battery_level,
          isCharging: Boolean(t.is_charging),
          batteryHealth: t.battery_health,
          batteryTemperature: t.battery_temp,
          availableStorageGb: t.available_storage_gb,
          totalStorageGb: t.total_storage_gb,
          availableRamGb: t.available_ram_gb,
          totalRamGb: t.total_ram_gb,
          networkType: t.network_type,
          location: t.latitude != null && t.longitude != null ? {
            latitude: t.latitude,
            longitude: t.longitude,
            accuracy: t.location_accuracy,
            timestamp: t.timestamp
          } : null,
          timestamp: t.timestamp
        };
      }

      deviceList.push({
        id: d.id,
        deviceName: d.device_name,
        deviceModel: d.device_model,
        androidVersion: d.android_version,
        status: d.status,
        batteryLevel: lastTelemetry ? lastTelemetry.batteryLevel : 0,
        isCharging: lastTelemetry ? lastTelemetry.isCharging : false,
        batteryHealth: lastTelemetry ? lastTelemetry.batteryHealth : 'Good',
        totalStorageGb: lastTelemetry ? lastTelemetry.totalStorageGb : 0,
        availableStorageGb: lastTelemetry ? lastTelemetry.availableStorageGb : 0,
        totalRamGb: lastTelemetry ? lastTelemetry.totalRamGb : 0,
        availableRamGb: lastTelemetry ? lastTelemetry.availableRamGb : 0,
        lastSeenAt: d.last_seen_at,
        createdAt: d.created_at,
        lastTelemetry
      });
    }

    res.json({ success: true, data: deviceList });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: e.message });
  }
});

// Get detailed device info
router.get('/:deviceId', authenticateController, requireControllerDevice, async (req, res) => {
  try {
    const { deviceId } = req.params;
    const devices = await db.query('SELECT * FROM devices WHERE id = $1', [deviceId]);
    if (!devices || devices.length === 0) {
      return res.status(404).json({ success: false, message: 'Device not found' });
    }

    const d = devices[0];
    const telemetryList = await db.query(
      'SELECT * FROM device_telemetry WHERE device_id = $1 ORDER BY timestamp DESC LIMIT 1',
      [d.id]
    );

    let lastTelemetry = null;
    if (telemetryList && telemetryList.length > 0) {
      const t = telemetryList[0];
      lastTelemetry = {
        deviceId: t.device_id,
        batteryLevel: t.battery_level,
        isCharging: Boolean(t.is_charging),
        batteryHealth: t.battery_health,
        batteryTemperature: t.battery_temp,
        availableStorageGb: t.available_storage_gb,
        totalStorageGb: t.total_storage_gb,
        availableRamGb: t.available_ram_gb,
        totalRamGb: t.total_ram_gb,
        networkType: t.network_type,
        location: t.latitude != null && t.longitude != null ? {
          latitude: t.latitude,
          longitude: t.longitude,
          accuracy: t.location_accuracy,
          timestamp: t.timestamp
        } : null,
        timestamp: t.timestamp
      };
    }

    res.json({
      success: true,
      data: {
        id: d.id,
        deviceName: d.device_name,
        deviceModel: d.device_model,
        androidVersion: d.android_version,
        status: d.status,
        batteryLevel: lastTelemetry ? lastTelemetry.batteryLevel : 0,
        isCharging: lastTelemetry ? lastTelemetry.isCharging : false,
        batteryHealth: lastTelemetry ? lastTelemetry.batteryHealth : 'Good',
        totalStorageGb: lastTelemetry ? lastTelemetry.totalStorageGb : 0,
        availableStorageGb: lastTelemetry ? lastTelemetry.availableStorageGb : 0,
        totalRamGb: lastTelemetry ? lastTelemetry.totalRamGb : 0,
        availableRamGb: lastTelemetry ? lastTelemetry.availableRamGb : 0,
        lastSeenAt: d.last_seen_at,
        createdAt: d.created_at,
        lastTelemetry
      }
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: e.message });
  }
});

// Companion posts telemetry
router.post('/:deviceId/telemetry', authenticateDevice, requireActiveDevice, async (req, res) => {
  try {
    const { deviceId } = req.params;
    const { batteryLevel, isCharging, batteryHealth, batteryTemperature, availableStorageGb, totalStorageGb, availableRamGb, totalRamGb, networkType, location } = req.body;

    const lat = location ? location.latitude : null;
    const lng = location ? location.longitude : null;
    const acc = location ? location.accuracy : null;

    const now = new Date().toISOString();

    await db.query(
      'INSERT INTO device_telemetry (device_id, battery_level, is_charging, battery_health, battery_temp, available_storage_gb, total_storage_gb, available_ram_gb, total_ram_gb, network_type, latitude, longitude, location_accuracy, timestamp) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)',
      [deviceId, batteryLevel, Boolean(isCharging), batteryHealth, batteryTemperature, availableStorageGb, totalStorageGb, availableRamGb, totalRamGb, networkType, lat, lng, acc, now]
    );

    await db.query('UPDATE devices SET last_seen_at = $1, status = $2 WHERE id = $3', [now, 'ONLINE', deviceId]);

    // Broadcast real-time telemetry over WebSocket to controller
    const devices = await db.query('SELECT controller_id FROM devices WHERE id = $1', [deviceId]);
    if (devices && devices[0]) {
      wsManager.broadcastTelemetry(devices[0].controller_id, deviceId, {
        deviceId,
        batteryLevel,
        isCharging,
        batteryHealth,
        batteryTemperature,
        availableStorageGb,
        totalStorageGb,
        availableRamGb,
        totalRamGb,
        networkType,
        location,
        timestamp: now
      });
    }

    res.json({ success: true, message: 'Telemetry updated' });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: e.message });
  }
});

// Telemetry history timeline
router.get('/:deviceId/telemetry/history', authenticateController, requireControllerDevice, async (req, res) => {
  try {
    const { deviceId } = req.params;
    const requestedLimit = Number.parseInt(req.query.limit || '20', 10);
    const limit = Number.isFinite(requestedLimit) ? Math.min(Math.max(requestedLimit, 1), 100) : 20;

    const rows = await db.query(
      'SELECT * FROM device_telemetry WHERE device_id = $1 ORDER BY timestamp DESC LIMIT $2',
      [deviceId, limit]
    );

    const result = rows.map(t => ({
      deviceId: t.device_id,
      batteryLevel: t.battery_level,
      isCharging: Boolean(t.is_charging),
      batteryHealth: t.battery_health,
      batteryTemperature: t.battery_temp,
      availableStorageGb: t.available_storage_gb,
      totalStorageGb: t.total_storage_gb,
      availableRamGb: t.available_ram_gb,
      totalRamGb: t.total_ram_gb,
      networkType: t.network_type,
      location: t.latitude != null && t.longitude != null ? {
        latitude: t.latitude,
        longitude: t.longitude,
        accuracy: t.location_accuracy,
        timestamp: t.timestamp
      } : null,
      timestamp: t.timestamp
    }));

    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: e.message });
  }
});

// Controller sends command
router.post('/:deviceId/commands', authenticateController, requireControllerDevice, async (req, res) => {
  try {
    const { deviceId } = req.params;
    const { commandType, payload } = req.body;
    const controllerId = req.controller.controllerId;

    const commandId = `cmd_${randomUUID()}`;
    const now = new Date().toISOString();

    await db.query(
      'INSERT INTO commands (id, device_id, command_type, status, payload, created_at) VALUES ($1, $2, $3, $4, $5, $6)',
      [commandId, deviceId, commandType, 'PENDING', payload || null, now]
    );

    await db.query(
      'INSERT INTO audit_logs (controller_id, target_device_id, action, details) VALUES ($1, $2, $3, $4)',
      [controllerId, deviceId, 'COMMAND_SENT', `Command '${commandType}' issued to device ${deviceId}`]
    );

    const commandData = {
      id: commandId,
      deviceId,
      commandType,
      status: 'PENDING',
      payload: payload || null,
      createdAt: now
    };

    // Instant WebSocket push to companion device
    const pushedRealTime = wsManager.pushCommandToDevice(deviceId, commandData);

    // Fallback: Send FCM push notification if device has FCM token
    const devices = await db.query('SELECT fcm_token FROM devices WHERE id = $1', [deviceId]);
    if (devices && devices[0] && devices[0].fcm_token) {
      await fcmService.sendPushNotification(devices[0].fcm_token, {
        commandId,
        commandType,
        payload: payload || ''
      });
    }

    res.json({
      success: true,
      message: pushedRealTime ? 'Command issued and delivered via WebSocket' : 'Command queued for device',
      data: commandData
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: e.message });
  }
});

// Companion polls pending commands
router.get('/:deviceId/pending-commands', authenticateDevice, requireActiveDevice, async (req, res) => {
  try {
    const { deviceId } = req.params;
    const rows = await db.query(
      'SELECT * FROM commands WHERE device_id = $1 AND status = $2 ORDER BY created_at ASC',
      [deviceId, 'PENDING']
    );

    const commands = rows.map(c => ({
      id: c.id,
      deviceId: c.device_id,
      commandType: c.command_type,
      payload: c.payload,
      status: c.status,
      createdAt: c.created_at
    }));

    res.json({ success: true, data: commands });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: e.message });
  }
});

// Companion acknowledges command
router.post('/:deviceId/commands/:commandId/ack', authenticateDevice, requireActiveDevice, async (req, res) => {
  try {
    const { deviceId, commandId } = req.params;
    const { result, status } = req.body;
    const commandStatus = status || 'EXECUTED';
    if (!['EXECUTED', 'FAILED'].includes(commandStatus)) {
      return res.status(400).json({ success: false, message: 'Invalid command status' });
    }
    const now = new Date().toISOString();

    const commands = await db.query(
      'SELECT id FROM commands WHERE id = $1 AND device_id = $2',
      [commandId, deviceId]
    );
    if (!commands.length) {
      return res.status(404).json({ success: false, message: 'Command not found' });
    }

    await db.query(
      'UPDATE commands SET status = $1, result = $2, executed_at = $3 WHERE id = $4',
      [commandStatus, result, now, commandId]
    );

    // Broadcast ACK to watching controllers
    const devices = await db.query('SELECT controller_id FROM devices WHERE id = $1', [deviceId]);
    if (devices && devices[0]) {
      wsManager.broadcastCommandAck(devices[0].controller_id, deviceId, {
        commandId,
        result,
        status: commandStatus,
        executedAt: now
      });
    }

    res.json({ success: true, message: 'Command acknowledged' });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: e.message });
  }
});

// List recent commands for a device
router.get('/:deviceId/commands', authenticateController, requireControllerDevice, async (req, res) => {
  try {
    const commands = await db.query(
      'SELECT * FROM commands WHERE device_id = $1 ORDER BY created_at DESC LIMIT $2',
      [req.params.deviceId, 100]
    );
    res.json({
      success: true,
      data: commands.map(command => ({
        id: command.id,
        deviceId: command.device_id,
        commandType: command.command_type,
        payload: command.payload,
        result: command.result,
        status: command.status,
        createdAt: command.created_at,
        executedAt: command.executed_at
      }))
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: e.message });
  }
});

router.post('/:deviceId/revoke', authenticateAny, async (req, res) => {
  try {
    const { deviceId } = req.params;

    const devices = await db.query('SELECT id, controller_id FROM devices WHERE id = $1', [deviceId]);
    if (!devices.length) {
      return res.status(404).json({ success: false, message: 'Device not found' });
    }

    const device = devices[0];
    if (req.auth.deviceId) {
      if (req.auth.deviceId !== deviceId) {
        return res.status(403).json({ success: false, message: 'Device authorization does not match this device' });
      }
    } else if (req.auth.controllerId !== device.controller_id) {
      return res.status(404).json({ success: false, message: 'Device not found' });
    }

    await db.query('UPDATE devices SET status = $1 WHERE id = $2', ['REVOKED', deviceId]);
    if (!req.auth.deviceId) {
      wsManager.pushCommandToDevice(deviceId, {
        id: `cmd_${randomUUID()}`,
        deviceId,
        commandType: 'REVOKE_ACCESS',
        status: 'PENDING',
        payload: null,
        createdAt: new Date().toISOString()
      });
    }
    await db.query(
      'INSERT INTO audit_logs (controller_id, target_device_id, action, details) VALUES ($1, $2, $3, $4)',
      [device.controller_id, deviceId, 'DEVICE_REVOKED', `Device ${deviceId} access revoked`]
    );

    res.json({ success: true, message: 'Device access revoked successfully' });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: e.message });
  }
});

module.exports = router;
