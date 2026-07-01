-- Add RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES to system_configs
IF NOT EXISTS (SELECT 1 FROM dbo.system_configs WHERE config_key = 'RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES')
BEGIN
    INSERT INTO dbo.system_configs (config_key, config_value, description, created_at, updated_at)
    VALUES ('RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES', '30', 'Minutes before reservation time when staff are notified and early arrival is allowed without penalty', GETDATE(), GETDATE());
END

-- Add notified_early_arrival column to reservations
IF NOT EXISTS (
    SELECT 1 FROM sys.columns 
    WHERE object_id = OBJECT_ID('dbo.reservations') 
    AND name = 'notified_early_arrival'
)
BEGIN
    ALTER TABLE dbo.reservations ADD notified_early_arrival BIT DEFAULT 0;
END
