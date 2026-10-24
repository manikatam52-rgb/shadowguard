require('dotenv').config();
const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');

const db = require('./db');
const authRoutes = require('./routes/auth');
const pairingRoutes = require('./routes/pairing');
const deviceRoutes = require('./routes/devices');
const wsManager = require('./wsManager');
const { authenticateController } = require('./middleware/auth');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server, path: '/ws' });

const PORT = process.env.PORT || 3000;

// Security Middleware
app.use(helmet());
app.use(cors());
app.use(express.json());

// Rate Limiter
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 200,
  message: { success: false, message: 'Too many requests, please try again later.' }
});
app.use('/api/', apiLimiter);

// Routes
app.use('/api/auth', authRoutes);
app.use('/api/pairing', pairingRoutes);
app.use('/api/devices', deviceRoutes);

// Audit Logs endpoint
app.get('/api/audit-logs', authenticateController, async (req, res) => {
  try {
    const controllerId = req.controller.controllerId;
    const logs = await db.query(
      'SELECT * FROM audit_logs WHERE controller_id = $1 ORDER BY timestamp DESC LIMIT 50',
      [controllerId]
    );

    res.json({
      success: true,
      data: logs.map(l => ({
        id: l.id,
        controllerId: l.controller_id,
        targetDeviceId: l.target_device_id,
        action: l.action,
        details: l.details,
        timestamp: l.timestamp
      }))
    });
  } catch (e) {
    res.status(500).json({ success: false, message: e.message });
  }
});

// Root Health Check
app.get('/', (req, res) => {
  res.json({
    name: 'ShadowGuard API Server',
    status: 'ONLINE',
    version: '1.0.0',
    timestamp: new Date().toISOString()
  });
});

// Initialize Real-time WebSocket Server
wsManager.initWebSocketServer(wss);

db.initialize().then(() => {
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`=================================================`);
    console.log(`  SHADOWGUARD MONITORING BACKEND RUNNING        `);
    console.log(`  HTTP Server: http://0.0.0.0:${PORT}`);
    console.log(`  WebSocket:   ws://0.0.0.0:${PORT}/ws`);
    console.log(`=================================================`);
  });
}).catch(error => {
  console.error('[Database] Initialization failed:', error);
  process.exitCode = 1;
});
