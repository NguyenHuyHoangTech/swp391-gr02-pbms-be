CREATE TABLE users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE system_configs (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value VARCHAR(MAX) NOT NULL,
    description VARCHAR(500),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE building_profiles (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    hotline VARCHAR(50),
    operating_hours VARCHAR(255),
    rules VARCHAR(MAX),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vehicle_types (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    type_name VARCHAR(100) NOT NULL UNIQUE,
    matrix_width INT NOT NULL,
    matrix_height INT NOT NULL
);

CREATE TABLE rfid_cards (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    card_code VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) DEFAULT 'ACTIVE'
);

CREATE TABLE floors (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    building_id BIGINT FOREIGN KEY REFERENCES building_profiles(id),
    floor_name VARCHAR(100) NOT NULL,
    floor_level INT NOT NULL,
    capacity INT NOT NULL,
    floor_type VARCHAR(50),
    map_cols INT,
    map_rows INT
);

CREATE TABLE gates (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    floor_id BIGINT FOREIGN KEY REFERENCES floors(id),
    gate_name VARCHAR(100) NOT NULL,
    gate_type VARCHAR(50) NOT NULL,
    live_override_mode VARCHAR(50) DEFAULT 'NORMAL',
    status VARCHAR(50) DEFAULT 'ACTIVE',
    layout_x FLOAT,
    layout_y FLOAT,
    rotation INT
);

CREATE TABLE vehicles (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id BIGINT FOREIGN KEY REFERENCES users(id),
    vehicle_type_id BIGINT FOREIGN KEY REFERENCES vehicle_types(id),
    plate_number VARCHAR(50) NOT NULL UNIQUE,
    color VARCHAR(50),
    brand VARCHAR(100),
    status VARCHAR(50) DEFAULT 'ACTIVE',
    blacklist_reason VARCHAR(MAX),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pricing_policies (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    building_id BIGINT FOREIGN KEY REFERENCES building_profiles(id),
    policy_name VARCHAR(255) NOT NULL,
    policy_type VARCHAR(50) NOT NULL,
    vehicle_type_id BIGINT FOREIGN KEY REFERENCES vehicle_types(id),
    is_active BIT DEFAULT 1,
    start_time TIME,
    end_time TIME,
    base_rate DECIMAL(18,2),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pricing_shifts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    policy_id BIGINT FOREIGN KEY REFERENCES pricing_policies(id),
    shift_name VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    rate_per_block DECIMAL(18,2) NOT NULL
);

CREATE TABLE pricing_blocks (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    policy_id BIGINT FOREIGN KEY REFERENCES pricing_policies(id),
    block_index INT NOT NULL,
    duration_minutes INT NOT NULL,
    rate DECIMAL(18,2) NOT NULL
);

CREATE TABLE zones (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    floor_id BIGINT FOREIGN KEY REFERENCES floors(id),
    vehicle_type_id BIGINT FOREIGN KEY REFERENCES vehicle_types(id),
    zone_name VARCHAR(100) NOT NULL,
    total_slots INT NOT NULL,
    available_slots INT NOT NULL,
    function_type VARCHAR(50) NOT NULL,
    layout_x FLOAT,
    layout_y FLOAT,
    rotation INT,
    overflow_threshold INT
);

CREATE TABLE routing_rules (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    zone_id BIGINT FOREIGN KEY REFERENCES zones(id),
    rule_name VARCHAR(255) NOT NULL,
    fill_threshold_pct INT NOT NULL,
    suggested_zone_id BIGINT FOREIGN KEY REFERENCES zones(id),
    is_active BIT DEFAULT 1
);

CREATE TABLE slots (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    zone_id BIGINT FOREIGN KEY REFERENCES zones(id),
    slot_name VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'AVAILABLE',
    version INT DEFAULT 1
);

CREATE TABLE reservations (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id BIGINT FOREIGN KEY REFERENCES users(id),
    vehicle_id BIGINT FOREIGN KEY REFERENCES vehicles(id),
    slot_id BIGINT FOREIGN KEY REFERENCES slots(id),
    expected_entry_time DATETIME NOT NULL,
    expected_duration_minutes INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    reservation_fee DECIMAL(18,2) NOT NULL,
    qr_code VARCHAR(255) UNIQUE,
    refund_status VARCHAR(50),
    refund_amount DECIMAL(18,2),
    refunded_by BIGINT FOREIGN KEY REFERENCES users(id),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE parking_sessions (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    vehicle_id BIGINT FOREIGN KEY REFERENCES vehicles(id),
    rfid_card_id BIGINT FOREIGN KEY REFERENCES rfid_cards(id),
    entry_gate_id BIGINT FOREIGN KEY REFERENCES gates(id),
    exit_gate_id BIGINT FOREIGN KEY REFERENCES gates(id),
    reservation_id BIGINT FOREIGN KEY REFERENCES reservations(id),
    entry_time DATETIME NOT NULL,
    exit_time DATETIME,
    entry_image_url VARCHAR(500),
    exit_image_url VARCHAR(500),
    lpr_image_in VARCHAR(500),
    lpr_image_out VARCHAR(500),
    base_fee DECIMAL(18,2),
    penalty_fee DECIMAL(18,2),
    discount DECIMAL(18,2),
    status VARCHAR(50) NOT NULL
);

CREATE TABLE monthly_tickets (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id BIGINT FOREIGN KEY REFERENCES users(id),
    vehicle_id BIGINT FOREIGN KEY REFERENCES vehicles(id),
    rfid_card_id BIGINT FOREIGN KEY REFERENCES rfid_cards(id),
    valid_from DATETIME NOT NULL,
    valid_until DATETIME NOT NULL,
    status VARCHAR(50) NOT NULL,
    auto_renew BIT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payment_orders (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    reservation_id BIGINT FOREIGN KEY REFERENCES reservations(id),
    monthly_ticket_id BIGINT FOREIGN KEY REFERENCES monthly_tickets(id),
    order_code VARCHAR(100) NOT NULL UNIQUE,
    amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transactions (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    parking_session_id BIGINT FOREIGN KEY REFERENCES parking_sessions(id),
    monthly_ticket_id BIGINT FOREIGN KEY REFERENCES monthly_tickets(id),
    payment_order_id BIGINT FOREIGN KEY REFERENCES payment_orders(id),
    amount DECIMAL(18,2) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    transaction_reference VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE staff_work_sessions (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    staff_id BIGINT FOREIGN KEY REFERENCES users(id),
    gate_id BIGINT FOREIGN KEY REFERENCES gates(id),
    login_time DATETIME NOT NULL,
    logout_time DATETIME,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE fee_adjustments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    parking_session_id BIGINT FOREIGN KEY REFERENCES parking_sessions(id),
    approved_by_staff_id BIGINT FOREIGN KEY REFERENCES staff_work_sessions(id),
    adjustment_amount DECIMAL(18,2) NOT NULL,
    reason VARCHAR(MAX) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE incident_tickets (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    reported_by_id BIGINT FOREIGN KEY REFERENCES users(id),
    assigned_staff_id BIGINT FOREIGN KEY REFERENCES staff_work_sessions(id),
    parking_session_id BIGINT FOREIGN KEY REFERENCES parking_sessions(id),
    incident_type VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    description VARCHAR(MAX) NOT NULL,
    status VARCHAR(50) NOT NULL,
    resolution_notes VARCHAR(MAX),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME
);

CREATE TABLE audit_logs (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    actor_id BIGINT FOREIGN KEY REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,
    target_entity VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    old_value VARCHAR(MAX),
    new_value VARCHAR(MAX),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE zone_hourly_trends (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    zone_id BIGINT FOREIGN KEY REFERENCES zones(id),
    time_window DATETIME NOT NULL,
    occupancy_pct DECIMAL(5,2) NOT NULL,
    revenue_generated DECIMAL(18,2) NOT NULL,
    entries_count INT NOT NULL,
    exits_count INT NOT NULL
);


-- NOTE: Seed users are managed separately. No mock data inserted here.

