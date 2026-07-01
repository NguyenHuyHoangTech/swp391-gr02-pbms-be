-- Migration to link PricingPolicies directly to VehicleType

-- 1. Add new column vehicle_type_id
ALTER TABLE dbo.pricing_policies ADD vehicle_type_id BIGINT;
GO

-- 2. Migrate existing data based on vehicle_type string
-- Map 'CAR' to the first FOUR_WHEEL vehicle type
UPDATE dbo.pricing_policies 
SET vehicle_type_id = (SELECT MIN(id) FROM dbo.vehicle_types WHERE category = 'FOUR_WHEEL') 
WHERE vehicle_type = 'CAR';
GO

-- Map 'MOTORBIKE' to the first TWO_WHEEL vehicle type
UPDATE dbo.pricing_policies 
SET vehicle_type_id = (SELECT MIN(id) FROM dbo.vehicle_types WHERE category = 'TWO_WHEEL') 
WHERE vehicle_type = 'MOTORBIKE';
GO

-- Provide a fallback for any other cases (just take the first vehicle type)
UPDATE dbo.pricing_policies 
SET vehicle_type_id = (SELECT MIN(id) FROM dbo.vehicle_types) 
WHERE vehicle_type_id IS NULL;
GO

-- 3. Make column NOT NULL and add foreign key constraint
ALTER TABLE dbo.pricing_policies ALTER COLUMN vehicle_type_id BIGINT NOT NULL;
GO

ALTER TABLE dbo.pricing_policies ADD CONSTRAINT FK_pricing_policies_vehicle_type 
FOREIGN KEY (vehicle_type_id) REFERENCES dbo.vehicle_types(id);
GO

-- 4. Drop the old string column
ALTER TABLE dbo.pricing_policies DROP COLUMN vehicle_type;
GO
