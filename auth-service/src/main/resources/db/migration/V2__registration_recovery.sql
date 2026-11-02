ALTER TABLE credentials ADD COLUMN profile_ready boolean NOT NULL DEFAULT true, ADD COLUMN first_name varchar(80), ADD COLUMN last_name varchar(80), ADD COLUMN phone varchar(32);
