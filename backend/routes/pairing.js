const express = require('express');
const { randomInt, randomUUID } = require('crypto');
const db = require('../db');
const { authenticateController, generateToken } = require('../middleware/auth');

const router = express.Router();

// Generate expiring pairing token & QR payload (Controller)
router.post('/generate-token', authenticateController, async (req, res) => {
  try {
    const controllerId = req.controller.controllerId;
    const expiresInMinutes = req.body.expiresInMinutes || 15;

    const tokenCode = randomInt(100000, 1000000).toString();
    const expiresAt = new Date(Date.now() + expiresInMinutes * 60 * 1000).toISOString();

    const qrPayloadObj = {
      token: tokenCode,
      controllerId,
      expiresAt
    };
    const qrPayloadStr = JSON.stringify(qrPayloadObj);

    await db.query(
      'INSERT INTO pairing_tokens (token, controller_id, qr_payload, expires_at) VALUES ($1, $2, $3, $4)',
      [tokenCode, controllerId, qrPayloadStr, expiresAt]
    );

    await db.query(
      'INSERT INTO audit_logs (controller_id, action, details) VALUES ($1, $2, $3)',
      [controllerId, 'PAIRING_TOKEN_GENERATED', `Pairing token generated: ${tokenCode}`]
    );

    res.json({
      success: true,
      message: 'Pairing token generated',
      data: {
        token: tokenCode,
        qrCodePayload: qrPayloadStr,
        expiresAt
      }
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: 'Failed to generate token: ' + e.message });
  }
});

// Companion pairs using token
router.post('/pair-device', async (req, res) => {
  try {
    const { token, deviceName, deviceModel, androidVersion, fcmToken } = req.body;

    if (!token) {
      return res.status(400).json({ success: false, message: 'Pairing token required' });
    }

    const tokens = await db.query('SELECT * FROM pairing_tokens WHERE token = $1', [token]);
    if (!tokens || tokens.length === 0) {
      return res.status(404).json({ success: false, message: 'Invalid pairing token' });
    }

    const tokenRecord = tokens[0];
    if (tokenRecord.used) {
      return res.status(400).json({ success: false, message: 'Pairing token already used' });
    }

    const now = new Date();
    if (new Date(tokenRecord.expires_at) < now) {
      return res.status(400).json({ success: false, message: 'Pairing token expired' });
    }

    const deviceId = `dev_${randomUUID()}`;
    const deviceToken = generateToken({ deviceId, controllerId: tokenRecord.controller_id }, '30d');

    await db.query(
      'INSERT INTO devices (id, controller_id, device_name, device_model, android_version, device_token, fcm_token, status) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)',
      [deviceId, tokenRecord.controller_id, deviceName || deviceModel, deviceModel, androidVersion, deviceToken, fcmToken || null, 'ONLINE']
    );

    // Mark token as used
    await db.query('UPDATE pairing_tokens SET used = TRUE WHERE token = $1', [token]);

    await db.query(
      'INSERT INTO audit_logs (controller_id, target_device_id, action, details) VALUES ($1, $2, $3, $4)',
      [tokenRecord.controller_id, deviceId, 'DEVICE_PAIRED', `Device ${deviceModel} paired successfully`]
    );

    res.json({
      success: true,
      message: 'Device paired successfully',
      data: {
        deviceId,
        controllerId: tokenRecord.controller_id,
        deviceToken,
        message: 'Paired'
      }
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: 'Pairing failed: ' + e.message });
  }
});

module.exports = router;
