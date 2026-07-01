-- V24__translate_all_final.sql
-- Ensure all seed data is completely translated to English

-- Update User full_names (Remove Vietnamese accents and use standard English names where applicable)
UPDATE users SET full_name = 'Nguyen Van A' WHERE full_name LIKE '%Nguyễn Văn A%';
UPDATE users SET full_name = 'Tran Thi B' WHERE full_name LIKE '%Trần Thị B%';
UPDATE users SET full_name = 'Le Van C' WHERE full_name LIKE '%Lê Văn C%';
UPDATE users SET full_name = 'Pham Thi D' WHERE full_name LIKE '%Phạm Thị D%';
UPDATE users SET full_name = 'System Administrator' WHERE full_name LIKE '%Quản Trị Hệ Thống%' OR full_name = 'Quan Tri He Thong';
UPDATE users SET full_name = 'Manager' WHERE full_name LIKE '%Quản Lý%' OR full_name = 'Quan Ly';
UPDATE users SET full_name = 'Staff 1' WHERE full_name LIKE '%Nhân Viên 1%' OR full_name = 'Nhan Vien 1';
UPDATE users SET full_name = 'Customer 1' WHERE full_name LIKE '%Khách Hàng 1%' OR full_name = 'Khach Hang 1';

-- Update Vehicle Types
UPDATE vehicle_types SET type_name = '4-wheel Car' WHERE type_name LIKE '%Ô tô 4 bánh%' OR type_name LIKE '%Ô Tô%';
UPDATE vehicle_types SET type_name = '2-wheel Vehicle' WHERE type_name LIKE '%Xe 2 bánh%' OR type_name LIKE '%Xe Máy%';

-- Update Pricing Policies
UPDATE pricing_policies SET policy_name = 'Standard Car Pricing' WHERE policy_name LIKE '%Bảng Giá Ô Tô Chuẩn%';
UPDATE pricing_policies SET policy_name = 'Standard Motorbike Pricing' WHERE policy_name LIKE '%Bảng Giá Xe Máy Chuẩn%';
UPDATE pricing_policies SET policy_name = 'VIP Car Pricing' WHERE policy_name LIKE '%Bảng Giá Ô Tô VIP%';
UPDATE pricing_policies SET policy_name = 'VIP Motorbike Pricing' WHERE policy_name LIKE '%Bảng Giá Xe Máy VIP%';

-- Update Zones
UPDATE zones SET zone_name = 'Car Zone A' WHERE zone_name LIKE '%Khu Vực Ô Tô A%';
UPDATE zones SET zone_name = 'Motorbike Zone B' WHERE zone_name LIKE '%Khu Vực Xe Máy B%';
UPDATE zones SET zone_name = 'VIP Zone' WHERE zone_name LIKE '%Khu Vực VIP%';

-- Update Gates
UPDATE gates SET gate_name = 'Main Entry Gate' WHERE gate_name LIKE '%Cổng Vào Chính%';
UPDATE gates SET gate_name = 'Main Exit Gate' WHERE gate_name LIKE '%Cổng Ra Chính%';
UPDATE gates SET gate_name = 'VIP Gate' WHERE gate_name LIKE '%Cổng VIP%';
UPDATE gates SET gate_name = 'Patrol Duty' WHERE gate_name LIKE '%Tuần tra%';
