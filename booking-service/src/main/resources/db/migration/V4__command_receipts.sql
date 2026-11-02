CREATE TABLE command_receipts (
 command_key varchar(200) PRIMARY KEY,
 fingerprint text NOT NULL,
 result_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Local substitute for TourClient. This table is owned only by Booking Service.
CREATE TABLE local_tour_stub (tour_id uuid PRIMARY KEY, payload jsonb NOT NULL);
