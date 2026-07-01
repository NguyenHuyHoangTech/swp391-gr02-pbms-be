-- Khởi tạo Dữ liệu Bảng giá Động mẫu (Dynamic Pricing Policies)
-- Dữ liệu này giúp tự động tính phí Pre-Booking cũng như Check-out mà không cần Hard-code.

-- Xóa các policy rỗng để tránh conflict
DELETE FROM dbo.pricing_policies WHERE global_base_mins IS NULL OR global_base_fee IS NULL;

-- Insert Policy cho FOUR_WHEEL (Ô tô)
INSERT INTO dbo.pricing_policies (
    policy_name, vehicle_type_id, global_base_mins, global_base_fee, max_parking_cap, status, created_at
)
SELECT 
    'Bảng giá Ô tô tiêu chuẩn', 
    MIN(id), 
    60,      -- 1 block = 60 phút
    25000,   -- 25.000 VNĐ / block
    200000,  -- Max 200.000 VNĐ / ngày
    'ACTIVE', 
    CURRENT_TIMESTAMP
FROM dbo.vehicle_types WHERE category = 'FOUR_WHEEL'
AND NOT EXISTS (
    SELECT 1 FROM dbo.pricing_policies p 
    JOIN dbo.vehicle_types v ON p.vehicle_type_id = v.id 
    WHERE v.category = 'FOUR_WHEEL' AND p.status = 'ACTIVE'
)
HAVING MIN(id) IS NOT NULL;

-- Insert Policy cho TWO_WHEEL (Xe máy)
INSERT INTO dbo.pricing_policies (
    policy_name, vehicle_type_id, global_base_mins, global_base_fee, max_parking_cap, status, created_at
)
SELECT 
    'Bảng giá Xe máy tiêu chuẩn', 
    MIN(id), 
    60,      -- 1 block = 60 phút
    5000,    -- 5.000 VNĐ / block
    50000,   -- Max 50.000 VNĐ / ngày
    'ACTIVE', 
    CURRENT_TIMESTAMP
FROM dbo.vehicle_types WHERE category = 'TWO_WHEEL'
AND NOT EXISTS (
    SELECT 1 FROM dbo.pricing_policies p 
    JOIN dbo.vehicle_types v ON p.vehicle_type_id = v.id 
    WHERE v.category = 'TWO_WHEEL' AND p.status = 'ACTIVE'
)
HAVING MIN(id) IS NOT NULL;
