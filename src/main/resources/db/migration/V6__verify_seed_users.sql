-- Remove mock seed users - the only admin is systemadministratorweb@gmail.com (Google OAuth)
IF EXISTS (SELECT 1 FROM users WHERE email = 'admin@pbms.com')
    DELETE FROM users WHERE email IN ('admin@pbms.com','manager@pbms.com','staff@pbms.com','customer@pbms.com');
