CREATE TABLE sales_facts (
  fact_id uuid PRIMARY KEY,
  source_event_id uuid NOT NULL UNIQUE,
  source_operation_id uuid NOT NULL,
  payment_id uuid NOT NULL,
  booking_id uuid NOT NULL,
  schedule_id uuid NOT NULL,
  direction_id uuid NOT NULL,
  amount_minor bigint NOT NULL CHECK(amount_minor>=0),
  currency char(3) NOT NULL CHECK(currency='BYN'),
  fact_type varchar(20) NOT NULL CHECK(fact_type IN ('SALE','REFUND')),
  occurred_at timestamptz NOT NULL,
  UNIQUE(fact_type,source_operation_id)
);

CREATE TABLE schedule_load_facts (
  schedule_id uuid PRIMARY KEY,
  direction_id uuid NOT NULL,
  starts_at timestamptz NOT NULL,
  capacity integer NOT NULL CHECK(capacity>0),
  confirmed_seats integer NOT NULL CHECK(confirmed_seats>=0 AND confirmed_seats<=capacity),
  updated_at timestamptz NOT NULL
);

CREATE TABLE direction_popularity_daily (
  day date NOT NULL,
  direction_id uuid NOT NULL,
  confirmed_tourists integer NOT NULL CHECK(confirmed_tourists>=0),
  PRIMARY KEY(day,direction_id)
);

CREATE TABLE direction_catalog_projection (
  direction_id uuid PRIMARY KEY,
  country varchar(100) NOT NULL,
  city varchar(100) NOT NULL,
  is_active boolean NOT NULL,
  version bigint NOT NULL
);

CREATE TABLE processed_events (
  consumer_name varchar(80) NOT NULL,
  event_id uuid NOT NULL,
  processed_at timestamptz NOT NULL,
  PRIMARY KEY(consumer_name,event_id)
);
