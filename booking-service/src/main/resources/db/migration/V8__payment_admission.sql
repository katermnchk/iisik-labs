CREATE TABLE payment_intents(booking_id uuid PRIMARY KEY,payment_id uuid NOT NULL UNIQUE,created_at timestamptz NOT NULL DEFAULT now());
