-- Add admin users

-- !Ups
ALTER TABLE USERS ADD COLUMN is_admin boolean;
update users set is_admin = false where true;

-- !Downs
ALTER TABLE users DROP COLUMN is_admin;

