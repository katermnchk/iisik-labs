CREATE TABLE bookings (
  id uuid PRIMARY KEY,
  client_id uuid NOT NULL,
  schedule_id uuid NOT NULL,
  direction_id_snapshot uuid NOT NULL,
  tour_title_snapshot varchar(180) NOT NULL,
  direction_name_snapshot varchar(180) NOT NULL,
  starts_at_snapshot timestamptz NOT NULL,
  traveler_count smallint NOT NULL CHECK(traveler_count>0),
  unit_price_minor bigint NOT NULL CHECK(unit_price_minor>=0),
  total_amount_minor bigint NOT NULL CHECK(total_amount_minor=unit_price_minor*traveler_count),
  currency char(3) NOT NULL CHECK(currency='BYN'),
  status varchar(24) NOT NULL CHECK(status IN ('RESERVING','PENDING_PAYMENT','CONFIRMED','EXPIRED','CANCELLING','REFUND_PENDING','CANCELLED','COMPLETED','REJECTED')),
  hold_expires_at timestamptz ,
  version bigint NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE TABLE booking_status_history (
  id uuid PRIMARY KEY,
  booking_id uuid NOT NULL REFERENCES bookings(id),
  from_status varchar(24) ,
  to_status varchar(24) NOT NULL,
  reason varchar(255) ,
  changed_by_user_id uuid ,
  changed_at timestamptz NOT NULL
);

CREATE TABLE idempotency_keys (
  scope varchar(80) NOT NULL,
  key varchar(100) NOT NULL,
  request_hash char(64) NOT NULL,
  response_json jsonb NOT NULL,
  expires_at timestamptz NOT NULL,
  PRIMARY KEY(scope,key)
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

CREATE TABLE processed_events (
  consumer_name varchar(80) NOT NULL,
  event_id uuid NOT NULL,
  processed_at timestamptz NOT NULL,
  PRIMARY KEY(consumer_name,event_id)
);

CREATE INDEX bookings_client_idx ON bookings(client_id,created_at);
