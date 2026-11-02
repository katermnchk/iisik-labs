CREATE TABLE command_receipts (
 command_key varchar(200) PRIMARY KEY,
 fingerprint text NOT NULL,
 result_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
