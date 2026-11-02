CREATE TABLE roles (
  id uuid PRIMARY KEY,
  code varchar(20) NOT NULL UNIQUE CHECK(code IN ('CLIENT','EMPLOYEE','ADMIN')),
  name varchar(80) NOT NULL
);

CREATE TABLE users (
  id uuid PRIMARY KEY,
  role_id uuid NOT NULL REFERENCES roles(id),
  email varchar(254) NOT NULL UNIQUE,
  password_hash varchar(255) NOT NULL,
  first_name varchar(80) NOT NULL,
  last_name varchar(80) NOT NULL,
  status varchar(20) NOT NULL CHECK(status IN ('ACTIVE','BLOCKED')),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE TABLE clients (
  user_id uuid PRIMARY KEY REFERENCES users(id),
  phone varchar(32) NOT NULL
);

CREATE TABLE employees (
  user_id uuid PRIMARY KEY REFERENCES users(id),
  position varchar(120) NOT NULL
);

CREATE TABLE outbox_events (
  id uuid PRIMARY KEY,
  aggregate_id uuid NOT NULL,
  aggregate_version bigint NOT NULL,
  event_type varchar(80) NOT NULL,
  schema_version integer NOT NULL,
  correlation_id uuid NOT NULL,
  payload jsonb NOT NULL,
  occurred_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  published_at timestamptz 
);
