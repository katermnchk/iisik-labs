CREATE TABLE notifications (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL,
  booking_id uuid ,
  kind varchar(40) NOT NULL CHECK(kind IN ('WELCOME','BOOKING_STATUS','TOUR_CHANGE','PAYMENT_STATUS')),
  title varchar(180) NOT NULL,
  body text NOT NULL,
  status varchar(20) NOT NULL CHECK(status IN ('PENDING','SENT','FAILED')),
  created_at timestamptz NOT NULL
);

CREATE TABLE notification_deliveries (
  id uuid PRIMARY KEY,
  notification_id uuid NOT NULL REFERENCES notifications(id),
  channel varchar(20) NOT NULL CHECK(channel IN ('EMAIL','PUSH')),
  status varchar(20) NOT NULL CHECK(status IN ('PENDING','SENT','FAILED')),
  attempt_no smallint NOT NULL CHECK(attempt_no>0),
  sent_at timestamptz ,
  error_message varchar(255) 
);

CREATE TABLE processed_events (
  consumer_name varchar(80) NOT NULL,
  event_id uuid NOT NULL,
  processed_at timestamptz NOT NULL,
  PRIMARY KEY(consumer_name,event_id)
);
