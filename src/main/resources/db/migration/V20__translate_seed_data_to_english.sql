-- 1. Truncate Transactional Data to start fresh in English
-- Note: Disabling constraints temporarily if needed, or deleting in order.
-- DELETE FROM dbo.invoices;
-- DELETE FROM dbo.transactions;
-- DELETE FROM dbo.staff_work_sessions;
-- DELETE FROM dbo.monthly_tickets;
-- DELETE FROM dbo.reservations;
-- DELETE FROM dbo.parking_records;
-- DELETE FROM dbo.vehicles;


-- 2. Translate vehicle_types
UPDATE dbo.vehicle_types SET type_name = 'Motorbike' WHERE type_name = N'Xe Máy';
UPDATE dbo.vehicle_types SET type_name = 'Car' WHERE type_name = N'Ô tô';
UPDATE dbo.vehicle_types SET type_name = 'Bicycle' WHERE type_name = N'Xe Đạp';
UPDATE dbo.vehicle_types SET type_name = 'Electric Bike' WHERE type_name = N'Xe Đạp Điện';
UPDATE dbo.vehicle_types SET type_name = 'Electric Car' WHERE type_name = N'Ô tô Điện';

-- 3. Translate pricing_policies
UPDATE dbo.pricing_policies SET policy_name = 'Standard Motorbike Rate' WHERE policy_name = N'Vé lượt xe máy';
UPDATE dbo.pricing_policies SET policy_name = 'Standard Car Rate' WHERE policy_name = N'Vé lượt ô tô';
UPDATE dbo.pricing_policies SET policy_name = 'Motorbike Monthly Pass' WHERE policy_name = N'Vé tháng xe máy';
UPDATE dbo.pricing_policies SET policy_name = 'Car Monthly Pass' WHERE policy_name = N'Vé tháng ô tô';
UPDATE dbo.pricing_policies SET policy_name = 'Reservation Fee' WHERE policy_name = N'Phí đặt trước';
UPDATE dbo.pricing_policies SET policy_name = 'Late Penalty Fee' WHERE policy_name = N'Phí quá hạn';

-- 4. Translate system_configs
UPDATE dbo.system_configs SET description = 'Default grace period (minutes) before late fees apply' WHERE config_key = 'grace_period_minutes';
UPDATE dbo.system_configs SET description = 'Threshold for zone overload alert' WHERE config_key = 'overload_threshold';

-- 5. Translate building_profiles
UPDATE dbo.building_profiles SET rules = '1. Speed limit 5km/h. 2. Follow attendant instructions. 3. Do not leave valuables in vehicle.' WHERE name = N'PBMS Headquarter';
