IF NOT EXISTS (SELECT 1 FROM dbo.building_profiles)
BEGIN
    INSERT INTO dbo.building_profiles (name, address, hotline, operating_hours, rules, created_at, updated_at)
    VALUES ('F-Town 3 Parking', 'Khu cong nghe cao, Quan 9, TP.HCM', '0123456789', '06:00 - 22:00', 'Tuan thu toc do 5km/h', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
END
