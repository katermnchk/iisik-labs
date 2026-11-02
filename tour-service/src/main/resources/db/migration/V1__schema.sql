CREATE TABLE directions (
  id uuid PRIMARY KEY,
  country varchar(100) NOT NULL,
  city varchar(100) NOT NULL,
  description text NOT NULL,
  is_active boolean NOT NULL
);

CREATE TABLE tours (
  id uuid PRIMARY KEY,
  direction_id uuid NOT NULL REFERENCES directions(id),
  title varchar(180) NOT NULL,
  description text NOT NULL,
  duration_days smallint NOT NULL CHECK(duration_days>0),
  status varchar(20) NOT NULL CHECK(status IN ('DRAFT','PUBLISHED','ARCHIVED')),
  created_by_employee_id uuid NOT NULL,
  version bigint NOT NULL CHECK(version>0)
);

CREATE TABLE tour_schedules (
  id uuid PRIMARY KEY,
  tour_id uuid NOT NULL REFERENCES tours(id),
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL CHECK(ends_at>starts_at),
  capacity integer NOT NULL CHECK(capacity>0),
  reserved_seats integer NOT NULL CHECK(reserved_seats>=0 AND reserved_seats<=capacity),
  base_price_minor bigint NOT NULL CHECK(base_price_minor>=0),
  currency char(3) NOT NULL CHECK(currency='BYN'),
  price_version bigint NOT NULL,
  version bigint NOT NULL,
  status varchar(20) NOT NULL CHECK(status IN ('OPEN','CLOSED','CANCELLED'))
);

CREATE TABLE accommodations (
  id uuid PRIMARY KEY,
  schedule_id uuid NOT NULL REFERENCES tour_schedules(id),
  name varchar(180) NOT NULL,
  address varchar(255) NOT NULL,
  check_in_at timestamptz NOT NULL,
  check_out_at timestamptz NOT NULL CHECK(check_out_at>check_in_at),
  room_type varchar(80) NOT NULL
);

CREATE TABLE transports (
  id uuid PRIMARY KEY,
  schedule_id uuid NOT NULL REFERENCES tour_schedules(id),
  kind varchar(20) NOT NULL CHECK(kind IN ('AIR','RAIL','BUS','OTHER')),
  carrier varchar(120) NOT NULL,
  route_number varchar(40) NOT NULL,
  departure_at timestamptz NOT NULL,
  arrival_at timestamptz NOT NULL CHECK(arrival_at>departure_at)
);

CREATE TABLE seat_holds (
  booking_id uuid PRIMARY KEY,
  schedule_id uuid NOT NULL REFERENCES tour_schedules(id),
  seats smallint NOT NULL CHECK(seats>0),
  status varchar(20) NOT NULL CHECK(status IN ('HELD','CONFIRMED','RELEASED')),
  expires_at timestamptz NOT NULL,
  price_version bigint NOT NULL,
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
