-- V13: Standardize Schema Database to match 25-table Architecture

-- Group B: Spatial & Routing
ALTER TABLE dbo.slots ADD current_plate VARCHAR(50) NULL;

-- Group C: Operations & Control
-- Rename existing columns in parking_sessions
EXEC sp_rename 'dbo.parking_sessions.entry_gate_id', 'gate_in_id', 'COLUMN';
EXEC sp_rename 'dbo.parking_sessions.exit_gate_id', 'gate_out_id', 'COLUMN';
EXEC sp_rename 'dbo.parking_sessions.entry_time', 'time_in', 'COLUMN';
EXEC sp_rename 'dbo.parking_sessions.exit_time', 'time_out', 'COLUMN';
EXEC sp_rename 'dbo.parking_sessions.entry_image_url', 'pic_in_panorama', 'COLUMN';
EXEC sp_rename 'dbo.parking_sessions.lpr_image_in', 'pic_in_face', 'COLUMN';
EXEC sp_rename 'dbo.parking_sessions.exit_image_url', 'pic_out_panorama', 'COLUMN';
EXEC sp_rename 'dbo.parking_sessions.lpr_image_out', 'pic_out_face', 'COLUMN';
EXEC sp_rename 'dbo.parking_sessions.base_fee', 'global_base_fee', 'COLUMN';
-- Drop existing fk before altering
ALTER TABLE dbo.parking_sessions DROP CONSTRAINT IF EXISTS FK_ParkingSession_Vehicle;
EXEC sp_rename 'dbo.parking_sessions.vehicle_id', 'vehicle_type_id', 'COLUMN';

ALTER TABLE dbo.parking_sessions ADD
    plate VARCHAR(50) NULL,
    plate_out VARCHAR(50) NULL,
    total_fee DECIMAL(18,2) NULL;

-- Make slot_id BIGINT to match slots table
EXEC sp_rename 'dbo.parking_sessions.suggested_zone_id', 'slot_id', 'COLUMN';

EXEC sp_rename 'dbo.vehicles.customer_id', 'user_id', 'COLUMN';
ALTER TABLE dbo.vehicles ADD is_blacklisted BIT NOT NULL DEFAULT 0;

ALTER TABLE dbo.rfid_cards ADD assigned_plate VARCHAR(50) NULL;

-- Group E: B2C & Subscriptions
ALTER TABLE dbo.monthly_tickets ADD
    user_id BIGINT NULL,
    vehicle_type_id BIGINT NULL,
    plate VARCHAR(50) NULL;

ALTER TABLE dbo.monthly_tickets ADD CONSTRAINT FK_Monthly_Users FOREIGN KEY (user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.monthly_tickets ADD CONSTRAINT FK_Monthly_VehicleType FOREIGN KEY (vehicle_type_id) REFERENCES dbo.vehicle_types(id);

-- Create refund_requests table
CREATE TABLE dbo.refund_requests (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    reference_type VARCHAR(50) NOT NULL, -- 'MONTHLY_PASS' or 'RESERVATION'
    reference_id VARCHAR(50) NOT NULL,
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    penalty_fee DECIMAL(18,2) NOT NULL DEFAULT 0,
    refund_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    bank_name VARCHAR(100) NULL,
    account_number VARCHAR(100) NULL,
    account_name VARCHAR(100) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    cancel_time DATETIME NOT NULL,
    reject_reason TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at DATETIME2 NULL,
    CONSTRAINT FK_Refunds_Users FOREIGN KEY (user_id) REFERENCES dbo.users(id)
);

-- Group F: Support & Exceptions
EXEC sp_rename 'dbo.incident_tickets.parking_session_id', 'session_id', 'COLUMN';
EXEC sp_rename 'dbo.incident_tickets.reported_by_id', 'user_id', 'COLUMN';
EXEC sp_rename 'dbo.incident_tickets.assigned_staff_id', 'staff_id', 'COLUMN';
EXEC sp_rename 'dbo.incident_tickets.incident_type', 'issue_type', 'COLUMN';

ALTER TABLE dbo.incident_tickets ADD
    uploaded_doc_url VARCHAR(255) NULL,
    expected_zone_id BIGINT NULL,
    actual_zone_id BIGINT NULL;
