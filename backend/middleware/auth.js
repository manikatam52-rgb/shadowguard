const jwt = require('jsonwebtoken');

if (!process.env.JWT_SECRET) {
  throw new Error('JWT_SECRET must be configured');
}

const JWT_SECRET = process.env.JWT_SECRET;

function authenticateController(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ success: false, message: 'Authentication token required' });
  }

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err || !user.controllerId || user.deviceId) {
      return res.status(403).json({ success: false, message: 'Invalid or expired token' });
    }
    req.controller = user;
    next();
  });
}

function authenticateDevice(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ success: false, message: 'Device token required' });
  }

  jwt.verify(token, JWT_SECRET, (err, device) => {
    if (err || !device.deviceId || !device.controllerId) {
      return res.status(403).json({ success: false, message: 'Invalid device authorization token' });
    }
    req.device = device;
    next();
  });
}

function authenticateAny(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ success: false, message: 'Authentication token required' });
  }

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) {
      return res.status(403).json({ success: false, message: 'Invalid or expired token' });
    }
    req.auth = user;
    next();
  });
}

function generateToken(payload, expiresIn = '7d') {
  return jwt.sign(payload, JWT_SECRET, { expiresIn });
}

module.exports = {
  authenticateController,
  authenticateDevice,
  authenticateAny,
  generateToken,
  JWT_SECRET
};
