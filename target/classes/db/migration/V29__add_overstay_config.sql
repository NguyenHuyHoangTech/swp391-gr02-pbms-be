-- Add overstay hours limit configuration (OVERSTAY_HOURS_LIMIT)
IF NOT EXISTS (SELECT 1 FROM dbo.system_configs WHERE config_key = 'OVERSTAY_HOURS_LIMIT')
BEGIN
    INSERT INTO dbo.system_configs (config_key, config_value, description, created_at, updated_at)
    VALUES ('OVERSTAY_HOURS_LIMIT', '72', 'Allowed hours for parking before it is considered an overstay (Hours)', GETDATE(), GETDATE());
END
GO
