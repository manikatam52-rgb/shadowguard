const { Pool } = require('pg');
const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');

const usePg = process.env.DB_DRIVER === 'postgres' || Boolean(process.env.PGHOST);

let pgPool = null;
let sqliteDb = null;

if (usePg) {
  pgPool = new Pool({
    user: process.env.PGUSER || 'postgres',
    host: process.env.PGHOST || 'localhost',
    database: process.env.PGDATABASE || 'shadowguard',
    password: process.env.PGPASSWORD || 'postgres',
    port: parseInt(process.env.PGPORT || '5432', 10),
  });
  console.log('[Database] Initialized PostgreSQL connection pool');
} else {
  const dbPath = path.join(__dirname, 'shadowguard.db');
  sqliteDb = new sqlite3.Database(dbPath);
  console.log('[Database] Initialized SQLite local database at', dbPath);

  // Initialize SQLite tables if not existing
  sqliteDb.serialize(() => {
    sqliteDb.run(`
      CREATE TABLE IF NOT EXISTS controllers (
        id TEXT PRIMARY KEY,
        username TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP
      )
    `);

    sqliteDb.run(`
      CREATE TABLE IF NOT EXISTS pairing_tokens (
        token TEXT PRIMARY KEY,
        controller_id TEXT NOT NULL,
        qr_payload TEXT NOT NULL,
        expires_at TEXT NOT NULL,
        used INTEGER DEFAULT 0,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP
      )
    `);

    sqliteDb.run(`
      CREATE TABLE IF NOT EXISTS devices (
        id TEXT PRIMARY KEY,
        controller_id TEXT NOT NULL,
        device_name TEXT NOT NULL,
        device_model TEXT NOT NULL,
        android_version TEXT NOT NULL,
        status TEXT DEFAULT 'ONLINE',
        device_token TEXT NOT NULL,
        fcm_token TEXT,
        last_seen_at TEXT DEFAULT CURRENT_TIMESTAMP,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP
      )
    `);

    sqliteDb.run(`
      CREATE TABLE IF NOT EXISTS device_telemetry (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT NOT NULL,
        battery_level INTEGER NOT NULL,
        is_charging INTEGER DEFAULT 0,
        battery_health TEXT,
        battery_temp REAL,
        available_storage_gb REAL,
        total_storage_gb REAL,
        available_ram_gb REAL,
        total_ram_gb REAL,
        network_type TEXT,
        latitude REAL,
        longitude REAL,
        location_accuracy REAL,
        timestamp TEXT DEFAULT CURRENT_TIMESTAMP
      )
    `);

    sqliteDb.run(`
      CREATE TABLE IF NOT EXISTS commands (
        id TEXT PRIMARY KEY,
        device_id TEXT NOT NULL,
        command_type TEXT NOT NULL,
        status TEXT DEFAULT 'PENDING',
        payload TEXT,
        result TEXT,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
        executed_at TEXT
      )
    `);

    sqliteDb.run(`
      CREATE TABLE IF NOT EXISTS audit_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        controller_id TEXT NOT NULL,
        target_device_id TEXT,
        action TEXT NOT NULL,
        details TEXT,
        timestamp TEXT DEFAULT CURRENT_TIMESTAMP
      )
    `);
  });
}

async function query(text, params = []) {
  if (usePg && pgPool) {
    const res = await pgPool.query(text, params);
    return res.rows;
  } else {
    return new Promise((resolve, reject) => {
      // Normalize $1, $2 or ? to SQLite standard
      let sqliteText = text;
      let paramIdx = 1;
      while (sqliteText.includes(`$${paramIdx}`)) {
        sqliteText = sqliteText.replace(`$${paramIdx}`, '?');
        paramIdx++;
      }

      const isSelect = sqliteText.trim().toUpperCase().startsWith('SELECT');
      if (isSelect) {
        sqliteDb.all(sqliteText, params, (err, rows) => {
          if (err) reject(err);
          else resolve(rows || []);
        });
      } else {
        sqliteDb.run(sqliteText, params, function (err) {
          if (err) reject(err);
          else resolve([{ id: this.lastID, changes: this.changes }]);
        });
      }
    });
  }
}

module.exports = { query };
