-- ShadowGuard PostgreSQL Database Schema

CREATE TABLE IF NOT EXISTS controllers (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pairing_tokens (
    token VARCHAR(64) PRIMARY KEY,
    controller_id VARCHAR(64) REFERENCES controllers(id) ON DELETE CASCADE,
    qr_payload TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(64) PRIMARY KEY,
    controller_id VARCHAR(64) REFERENCES controllers(id) ON DELETE CASCADE,
    device_name VARCHAR(255) NOT NULL,
    device_model VARCHAR(255) NOT NULL,
    android_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'ONLINE', -- ONLINE, OFFLINE, PAUSED, REVOKED
    device_token VARCHAR(255) NOT NULL,
    fcm_token TEXT,
    last_seen_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS device_telemetry (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(64) REFERENCES devices(id) ON DELETE CASCADE,
    battery_level INT NOT NULL,
    is_charging BOOLEAN DEFAULT FALSE,
    battery_health VARCHAR(50),
    battery_temp FLOAT,
    available_storage_gb FLOAT,
    total_storage_gb FLOAT,
    available_ram_gb FLOAT,
    total_ram_gb FLOAT,
    network_type VARCHAR(50),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    location_accuracy FLOAT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS commands (
    id VARCHAR(64) PRIMARY KEY,
    device_id VARCHAR(64) REFERENCES devices(id) ON DELETE CASCADE,
    command_type VARCHAR(50) NOT NULL, -- PING, REFRESH_LOCATION, SOUND_ALARM, PAUSE_MONITORING, REVOKE_ACCESS
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SENT, EXECUTED, FAILED
    payload TEXT,
    result TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id SERIAL PRIMARY KEY,
    controller_id VARCHAR(64) REFERENCES controllers(id) ON DELETE CASCADE,
    target_device_id VARCHAR(64),
    action VARCHAR(100) NOT NULL,
    details TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index performance optimizations
CREATE INDEX IF NOT EXISTS idx_telemetry_device_time ON device_telemetry(device_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_commands_device_status ON commands(device_id, status);
CREATE INDEX IF NOT EXISTS idx_devices_controller ON devices(controller_id);
