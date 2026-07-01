-- Drop existing legacy tables
DROP TABLE IF EXISTS dbo.pricing_blocks;
DROP TABLE IF EXISTS dbo.pricing_shifts;
DROP TABLE IF EXISTS dbo.pricing_policies;

-- Bảng 1: pricing_policies (Cấu hình Toàn cục)
CREATE TABLE dbo.pricing_policies (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    policy_name VARCHAR(255) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL, -- CAR, MOTORBIKE
    global_base_mins INT NOT NULL DEFAULT 120,
    global_base_fee DECIMAL(18,2) NOT NULL DEFAULT 20000,
    max_parking_cap DECIMAL(18,2) NOT NULL DEFAULT 3000000,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, ARCHIVED
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Bảng 2: pricing_shifts (Cấu hình Ca 24h)
CREATE TABLE dbo.pricing_shifts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    policy_id BIGINT NOT NULL FOREIGN KEY REFERENCES dbo.pricing_policies(id),
    shift_name VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    total_duration_mins INT NOT NULL
);

-- Bảng 3: pricing_blocks (Cấu hình Phân mảnh trượt - Các Block trong Ca)
CREATE TABLE dbo.pricing_blocks (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    shift_id BIGINT NOT NULL FOREIGN KEY REFERENCES dbo.pricing_shifts(id),
    block_order INT NOT NULL,
    duration_mins INT NOT NULL,
    fee DECIMAL(18,2) NOT NULL
);
