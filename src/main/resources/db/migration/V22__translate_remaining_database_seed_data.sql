-- Complete translation of remaining seed data from V8, V14, V17

-- 1. vehicle_types
UPDATE dbo.vehicle_types 
SET type_name = '4-Seater Car' 
WHERE type_name = N'Ô tô 4 chỗ';

UPDATE dbo.vehicle_types 
SET type_name = '7-Seater Car' 
WHERE type_name = N'Ô tô 7 chỗ';

UPDATE dbo.vehicle_types 
SET type_name = 'Manual Motorbike' 
WHERE type_name = N'Xe máy số';

UPDATE dbo.vehicle_types 
SET type_name = 'Scooter' 
WHERE type_name = N'Xe tay ga';

UPDATE dbo.vehicle_types 
SET type_name = 'Heavy Motorbike' 
WHERE type_name = N'Xe phân khối lớn';

-- 2. pricing_policies (from V14 dynamic pricing policies)
UPDATE dbo.pricing_policies 
SET policy_name = 'Standard Car Pricing' 
WHERE policy_name = N'Bảng giá Ô tô tiêu chuẩn';

UPDATE dbo.pricing_policies 
SET policy_name = 'Standard Motorbike Pricing' 
WHERE policy_name = N'Bảng giá Xe máy tiêu chuẩn';
