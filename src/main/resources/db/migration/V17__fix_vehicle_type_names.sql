-- Fix vehicle type names encoding issue by changing column to NVARCHAR and updating data
-- Note: the unique constraint name might be auto-generated, but we try to drop it if it exists.
-- Since this is SQL Server, dropping a constraint by name requires knowing the name.
-- Instead of hardcoding the auto-generated name, we can declare a script to find and drop it.

DECLARE @ConstraintName nvarchar(200)
SELECT @ConstraintName = Name FROM sys.default_constraints WHERE PARENT_OBJECT_ID = OBJECT_ID('vehicle_types') AND PARENT_COLUMN_ID = (SELECT column_id FROM sys.columns WHERE NAME = N'type_name' AND object_id = OBJECT_ID(N'vehicle_types'))
IF @ConstraintName IS NOT NULL
    EXEC('ALTER TABLE vehicle_types DROP CONSTRAINT ' + @ConstraintName)

-- For UNIQUE constraints
SELECT @ConstraintName = Name FROM sys.key_constraints WHERE type = 'UQ' AND PARENT_OBJECT_ID = OBJECT_ID('vehicle_types')
IF @ConstraintName IS NOT NULL
    EXEC('ALTER TABLE vehicle_types DROP CONSTRAINT ' + @ConstraintName)

ALTER TABLE vehicle_types ALTER COLUMN type_name NVARCHAR(100) NOT NULL;

ALTER TABLE vehicle_types ADD CONSTRAINT UQ_vehicle_type_name UNIQUE (type_name);

UPDATE vehicle_types SET type_name = N'Ô tô 4 chỗ' WHERE id = 1;
UPDATE vehicle_types SET type_name = N'Ô tô 7 chỗ' WHERE id = 2;
UPDATE vehicle_types SET type_name = N'Xe máy số' WHERE id = 3;
UPDATE vehicle_types SET type_name = N'Xe tay ga' WHERE id = 4;
UPDATE vehicle_types SET type_name = N'Xe phân khối lớn' WHERE id = 5;
