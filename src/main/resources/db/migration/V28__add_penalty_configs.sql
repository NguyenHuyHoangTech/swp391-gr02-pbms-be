-- Thêm cấu hình phí phạt mất thẻ (PENALTY_LOST_CARD)
IF NOT EXISTS (SELECT 1 FROM dbo.system_configs WHERE config_key = 'PENALTY_LOST_CARD')
BEGIN
    INSERT INTO dbo.system_configs (config_key, config_value, description, created_at, updated_at)
    VALUES ('PENALTY_LOST_CARD', '200000', 'Mức phạt mặc định khi khách làm mất thẻ (VNĐ)', GETDATE(), GETDATE());
END
GO

-- Thêm cấu hình phí phạt hư hỏng thẻ do khách (PENALTY_DAMAGED_CARD)
IF NOT EXISTS (SELECT 1 FROM dbo.system_configs WHERE config_key = 'PENALTY_DAMAGED_CARD')
BEGIN
    INSERT INTO dbo.system_configs (config_key, config_value, description, created_at, updated_at)
    VALUES ('PENALTY_DAMAGED_CARD', '50000', 'Mức phạt mặc định khi thẻ bị hỏng do lỗi khách hàng (VNĐ)', GETDATE(), GETDATE());
END
GO
