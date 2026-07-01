IF COL_LENGTH('users', 'password_hash') IS NOT NULL
BEGIN
    EXEC('ALTER TABLE users ALTER COLUMN password_hash VARCHAR(255) NULL');
END

IF COL_LENGTH('users', 'google_id') IS NULL
BEGIN
    EXEC('ALTER TABLE users ADD google_id VARCHAR(100) NULL');
END

IF COL_LENGTH('users', 'is_verified') IS NULL
BEGIN
    EXEC('ALTER TABLE users ADD is_verified BIT NOT NULL DEFAULT 0');
END
