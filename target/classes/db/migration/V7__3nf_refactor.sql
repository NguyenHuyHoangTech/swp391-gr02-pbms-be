-- Drop redundant columns in 'zones' table to comply with 3NF
ALTER TABLE zones DROP COLUMN total_slots;
ALTER TABLE zones DROP COLUMN available_slots;

-- Drop redundant 'customer_id' in 'reservations' (implied by vehicle_id)
DECLARE @ConstraintName NVARCHAR(200);
SELECT @ConstraintName = fk.name FROM sys.foreign_keys fk INNER JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id WHERE fkc.parent_object_id = OBJECT_ID('reservations') AND fkc.parent_column_id = (SELECT column_id FROM sys.columns WHERE object_id = OBJECT_ID('reservations') AND name = 'customer_id');
IF @ConstraintName IS NOT NULL EXEC('ALTER TABLE reservations DROP CONSTRAINT ' + @ConstraintName);
ALTER TABLE reservations DROP COLUMN customer_id;

-- Drop redundant 'customer_id' in 'monthly_tickets' (implied by vehicle_id)
SELECT @ConstraintName = fk.name FROM sys.foreign_keys fk INNER JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id WHERE fkc.parent_object_id = OBJECT_ID('monthly_tickets') AND fkc.parent_column_id = (SELECT column_id FROM sys.columns WHERE object_id = OBJECT_ID('monthly_tickets') AND name = 'customer_id');
IF @ConstraintName IS NOT NULL EXEC('ALTER TABLE monthly_tickets DROP CONSTRAINT ' + @ConstraintName);
ALTER TABLE monthly_tickets DROP COLUMN customer_id;
