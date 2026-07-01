-- Add status column to support soft block/unblock
ALTER TABLE dbo.vehicle_types ADD status VARCHAR(20) DEFAULT 'ACTIVE';
GO

-- Update existing records to ACTIVE
UPDATE dbo.vehicle_types SET status = 'ACTIVE' WHERE status IS NULL;
