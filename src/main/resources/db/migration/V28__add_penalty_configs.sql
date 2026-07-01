-- Add configuration for lost card penalty (PENALTY_LOST_CARD)
IF NOT EXISTS (SELECT 1 FROM dbo.system_configs WHERE config_key = 'PENALTY_LOST_CARD')
BEGIN
    INSERT INTO dbo.system_configs (config_key, config_value, description, created_at, updated_at)
    VALUES ('PENALTY_LOST_CARD', '200000', 'Default penalty fee for lost card (VND)', GETDATE(), GETDATE());
END
GO

-- Add configuration for damaged card penalty (PENALTY_DAMAGED_CARD)
IF NOT EXISTS (SELECT 1 FROM dbo.system_configs WHERE config_key = 'PENALTY_DAMAGED_CARD')
BEGIN
    INSERT INTO dbo.system_configs (config_key, config_value, description, created_at, updated_at)
    VALUES ('PENALTY_DAMAGED_CARD', '50000', 'Default penalty fee for damaged card (VND)', GETDATE(), GETDATE());
END
GO
