DECLARE @ConstraintName nvarchar(200)

-- Find the UNIQUE constraint name for 'username' in 'users' table
SELECT @ConstraintName = name 
FROM sys.key_constraints 
WHERE type = 'UQ' AND parent_object_id = OBJECT_ID('users')
AND unique_index_id IN (
    SELECT index_id 
    FROM sys.index_columns 
    WHERE object_id = OBJECT_ID('users') 
    AND column_id = COLUMNPROPERTY(OBJECT_ID('users'), 'username', 'ColumnId')
)

-- Drop the unique constraint if it exists
IF @ConstraintName IS NOT NULL
    EXEC('ALTER TABLE users DROP CONSTRAINT ' + @ConstraintName)

-- Find any non-clustered index that might have been created by UNIQUE keyword
IF @ConstraintName IS NULL
BEGIN
    SELECT @ConstraintName = name 
    FROM sys.indexes 
    WHERE object_id = OBJECT_ID('users') AND is_unique = 1 
    AND index_id IN (
        SELECT index_id 
        FROM sys.index_columns 
        WHERE object_id = OBJECT_ID('users') 
        AND column_id = COLUMNPROPERTY(OBJECT_ID('users'), 'username', 'ColumnId')
    )
    IF @ConstraintName IS NOT NULL
        EXEC('DROP INDEX ' + @ConstraintName + ' ON users')
END

-- Drop the column
IF COL_LENGTH('users', 'username') IS NOT NULL
    EXEC('ALTER TABLE users DROP COLUMN username')
