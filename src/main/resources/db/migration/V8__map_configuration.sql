-- Thêm cột phân loại category (FOUR_WHEEL, TWO_WHEEL) vào vehicle_types
ALTER TABLE vehicle_types ADD category VARCHAR(50) DEFAULT 'FOUR_WHEEL';
GO

-- Nếu chưa có dữ liệu mẫu, ta tự động seed vài loại xe cơ bản
IF NOT EXISTS (SELECT 1 FROM vehicle_types)
BEGIN
    INSERT INTO vehicle_types (type_name, matrix_width, matrix_height, category) VALUES 
    ('Ô tô 4 chỗ', 50, 50, 'FOUR_WHEEL'),
    ('Ô tô 7 chỗ', 50, 50, 'FOUR_WHEEL'),
    ('Xe máy số', 50, 50, 'TWO_WHEEL'),
    ('Xe tay ga', 50, 50, 'TWO_WHEEL'),
    ('Xe phân khối lớn', 50, 50, 'TWO_WHEEL');
END

-- Đảm bảo có một category mặc định
UPDATE vehicle_types SET category = 'FOUR_WHEEL' WHERE category IS NULL;

-- Thêm trạng thái xóa mềm vào bảng zones
ALTER TABLE zones ADD status VARCHAR(50) DEFAULT 'ACTIVE';
GO
UPDATE zones SET status = 'ACTIVE' WHERE status IS NULL;
