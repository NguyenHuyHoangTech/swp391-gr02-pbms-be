DECLARE @ConstraintName nvarchar(200)
SELECT @ConstraintName = Name FROM sys.default_constraints 
WHERE parent_object_id = OBJECT_ID('vehicle_types') 
AND parent_column_id = (SELECT column_id FROM sys.columns WHERE object_id = OBJECT_ID('vehicle_types') AND name = 'requires_plate')

IF @ConstraintName IS NOT NULL
BEGIN
    EXEC('ALTER TABLE vehicle_types DROP CONSTRAINT ' + @ConstraintName)
END

ALTER TABLE vehicle_types DROP COLUMN requires_plate;
ALTER TABLE vehicle_types DROP COLUMN default_plate;
