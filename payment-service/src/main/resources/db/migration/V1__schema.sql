CREATE TABLE payments (
  id uuid PRIMARY KEY,
  booking_id uuid NOT NULL,
  schedule_id uuid NOT NULL,
  direction_id uuid NOT NULL,
  amount_minor bigint NOT NULL CHECK(amount_minor>=0),
  currency char(3) NOT NULL CHECK(currency='BYN'),
  status varchar(20) NOT NULL CHECK(status IN ('PENDING','SUCCEEDED','FAILED','REFUND_PENDING','REFUNDED')),
  payment_url varchar(500) ,
  provider varchar(80) NOT NULL,
  provider_payment_id varchar(120) UNIQUE,
  external_key varchar(120) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE TABLE payment_callbacks (
  id uuid PRIMARY KEY,
  payment_id uuid NOT NULL REFERENCES payments(id),
  provider_event_id varchar(120) NOT NULL UNIQUE,
  payload jsonb NOT NULL,
  received_at timestamptz NOT NULL
);

CREATE TABLE refunds (
  id uuid PRIMARY KEY,
  payment_id uuid NOT NULL REFERENCES payments(id),
  amount_minor bigint NOT NULL CHECK(amount_minor>0),
  status varchar(20) NOT NULL CHECK(status IN ('PENDING','SUCCEEDED','FAILED')),
  provider_refund_id varchar(120) UNIQUE,
  created_at timestamptz NOT NULL
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

CREATE INDEX payments_booking_idx ON payments(booking_id);

CREATE UNIQUE INDEX one_active_payment_per_booking ON payments(booking_id) WHERE status IN ('PENDING','SUCCEEDED','REFUND_PENDING');
