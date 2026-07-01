-- Change full_name column to NVARCHAR to support Unicode
ALTER TABLE users ALTER COLUMN full_name NVARCHAR(255);

-- Update the existing corrupted data
UPDATE users SET full_name = N'Nguyễn Huy Hoàng' WHERE email = 'nguyenhuyhoangnle@gmail.com';
UPDATE users SET full_name = N'Tập Học' WHERE email = 'hoctapfu3@gmail.com';
UPDATE users SET full_name = N'Huy Hoàng Nguyễn (ZP Media)' WHERE email = 'zpmediavn@gmail.com';
