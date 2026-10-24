const express = require('express');
const bcrypt = require('bcryptjs');
const { randomUUID } = require('crypto');
const db = require('../db');
const { generateToken } = require('../middleware/auth');

const router = express.Router();

// Controller Register
router.post('/register', async (req, res) => {
  try {
    const { username, passwordHash } = req.body;
    if (!username || !passwordHash) {
      return res.status(400).json({ success: false, message: 'Username and password required' });
    }

    const existing = await db.query('SELECT * FROM controllers WHERE username = $1', [username]);
    if (existing && existing.length > 0) {
      return res.status(400).json({ success: false, message: 'Username already exists' });
    }

    const salt = await bcrypt.genSalt(10);
    const hashed = await bcrypt.hash(passwordHash, salt);
    const controllerId = `ctrl_${randomUUID()}`;

    await db.query(
      'INSERT INTO controllers (id, username, password_hash) VALUES ($1, $2, $3)',
      [controllerId, username, hashed]
    );

    const token = generateToken({ controllerId, username });

    await db.query(
      'INSERT INTO audit_logs (controller_id, action, details) VALUES ($1, $2, $3)',
      [controllerId, 'CONTROLLER_REGISTERED', `Controller account ${username} created`]
    );

    res.json({
      success: true,
      message: 'Controller registered successfully',
      data: { token, controllerId, username }
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: 'Server error: ' + e.message });
  }
});

// Controller Login
router.post('/login', async (req, res) => {
  try {
    const { username, passwordHash } = req.body;
    if (!username || !passwordHash) {
      return res.status(400).json({ success: false, message: 'Username and password required' });
    }

    const users = await db.query('SELECT * FROM controllers WHERE username = $1', [username]);
    if (!users || users.length === 0) {
      return res.status(401).json({ success: false, message: 'Invalid credentials' });
    }

    const user = users[0];
    const isMatch = await bcrypt.compare(passwordHash, user.password_hash);
    if (!isMatch) {
      return res.status(401).json({ success: false, message: 'Invalid credentials' });
    }

    const token = generateToken({ controllerId: user.id, username: user.username });

    await db.query(
      'INSERT INTO audit_logs (controller_id, action, details) VALUES ($1, $2, $3)',
      [user.id, 'CONTROLLER_LOGIN', `Controller ${username} logged in`]
    );

    res.json({
      success: true,
      message: 'Login successful',
      data: { token, controllerId: user.id, username: user.username }
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, message: 'Server error: ' + e.message });
  }
});

module.exports = router;
