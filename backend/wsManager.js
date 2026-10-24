const jwt = require('jsonwebtoken');
const { JWT_SECRET } = require('./middleware/auth');

// Connected clients maps:
// controllers: Map<ws, { controllerId, username }>
// devices: Map<deviceId, ws>
const controllerSockets = new Map();
const deviceSockets = new Map();

function initWebSocketServer(wss) {
  wss.on('connection', (ws) => {
    console.log('[WebSocket] New client connected');

    ws.on('message', (message) => {
      try {
        const data = JSON.parse(message);
        handleWsMessage(ws, data);
      } catch (e) {
        console.error('[WebSocket] Invalid JSON message:', e.message);
      }
    });

    ws.on('close', () => {
      cleanupSocket(ws);
      console.log('[WebSocket] Client disconnected');
    });

    ws.on('error', (err) => {
      cleanupSocket(ws);
      console.error('[WebSocket] Socket error:', err.message);
    });
  });
}

function handleWsMessage(ws, data) {
  switch (data.type) {
    case 'PING':
      ws.send(JSON.stringify({ type: 'PONG', timestamp: new Date().toISOString() }));
      break;

    case 'AUTH_CONTROLLER':
      try {
        const decoded = jwt.verify(data.token, JWT_SECRET);
        controllerSockets.set(ws, { controllerId: decoded.controllerId, username: decoded.username });
        ws.send(JSON.stringify({ type: 'AUTH_SUCCESS', role: 'CONTROLLER', controllerId: decoded.controllerId }));
        console.log(`[WebSocket] Controller ${decoded.username} (${decoded.controllerId}) authenticated`);
      } catch (err) {
        ws.send(JSON.stringify({ type: 'AUTH_ERROR', message: 'Invalid token' }));
      }
      break;

    case 'AUTH_DEVICE':
      try {
        const decoded = jwt.verify(data.token, JWT_SECRET);
        deviceSockets.set(decoded.deviceId, ws);
        ws.send(JSON.stringify({ type: 'AUTH_SUCCESS', role: 'DEVICE', deviceId: decoded.deviceId }));
        console.log(`[WebSocket] Device ${decoded.deviceId} authenticated`);
      } catch (err) {
        ws.send(JSON.stringify({ type: 'AUTH_ERROR', message: 'Invalid device token' }));
      }
      break;

    default:
      break;
  }
}

function cleanupSocket(ws) {
  if (controllerSockets.has(ws)) {
    controllerSockets.delete(ws);
  }
  for (const [devId, socket] of deviceSockets.entries()) {
    if (socket === ws) {
      deviceSockets.delete(devId);
      break;
    }
  }
}

// Broadcast telemetry update to watching controller sockets
function broadcastTelemetry(controllerId, deviceId, telemetryData) {
  const payload = JSON.stringify({
    type: 'TELEMETRY_UPDATED',
    deviceId,
    telemetry: telemetryData,
    timestamp: new Date().toISOString()
  });

  for (const [ws, info] of controllerSockets.entries()) {
    if (info.controllerId === controllerId && ws.readyState === 1) { // 1 = OPEN
      ws.send(payload);
    }
  }
}

// Push command directly to companion device over WebSocket
function pushCommandToDevice(deviceId, command) {
  const ws = deviceSockets.get(deviceId);
  if (ws && ws.readyState === 1) {
    ws.send(JSON.stringify({
      type: 'COMMAND_ISSUED',
      command,
      timestamp: new Date().toISOString()
    }));
    return true;
  }
  return false;
}

// Broadcast command execution result to controller
function broadcastCommandAck(controllerId, deviceId, commandAck) {
  const payload = JSON.stringify({
    type: 'COMMAND_ACKNOWLEDGED',
    deviceId,
    ack: commandAck,
    timestamp: new Date().toISOString()
  });

  for (const [ws, info] of controllerSockets.entries()) {
    if (info.controllerId === controllerId && ws.readyState === 1) {
      ws.send(payload);
    }
  }
}

module.exports = {
  initWebSocketServer,
  broadcastTelemetry,
  pushCommandToDevice,
  broadcastCommandAck
};
