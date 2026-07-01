-- Complete translation of remaining seed data

-- 1. building_profiles
UPDATE dbo.building_profiles 
SET name = 'Smart Parking Facility' 
WHERE name = N'Bai Do Xe Thong Minh';

-- 2. floors
UPDATE dbo.floors 
SET floor_name = 'Car Floor' 
WHERE floor_name = N'fl';

UPDATE dbo.floors 
SET floor_name = 'Motorbike Floor' 
WHERE floor_name = N'Xm';

-- 3. gates
UPDATE dbo.gates 
SET gate_name = 'New Gate' 
WHERE gate_name = N'C?ng M?i';

-- (Other gates are already named Gate01, Gate A, Gate2)
