ALTER TABLE integration_outbox ADD COLUMN sequence bigint GENERATED ALWAYS AS IDENTITY;
ALTER TABLE integration_outbox ADD COLUMN topic varchar(120);
ALTER TABLE integration_outbox ADD COLUMN schema_version integer NOT NULL DEFAULT 1;
ALTER TABLE integration_outbox ADD COLUMN aggregate_version bigint;
ALTER TABLE integration_outbox ADD COLUMN occurred_at timestamptz NOT NULL DEFAULT now();
UPDATE integration_outbox SET occurred_at=created_at,
 topic=CASE WHEN event_type='BookingChanged' THEN 'booking.events.v1' WHEN event_type='PaymentSettled' THEN 'payment.events.v1' END,
 event_type=CASE WHEN event_type='BookingChanged' THEN 'BookingStatusChanged' WHEN event_type='PaymentSettled' THEN 'PaymentSucceeded' ELSE event_type END;
CREATE TABLE integration_aggregate_versions(aggregate_id uuid PRIMARY KEY,version bigint NOT NULL CHECK(version>0));
WITH ranked AS (SELECT id,row_number() OVER(PARTITION BY aggregate_id ORDER BY created_at,sequence) AS v FROM integration_outbox)
 UPDATE integration_outbox o SET aggregate_version=r.v FROM ranked r WHERE r.id=o.id;
INSERT INTO integration_aggregate_versions(aggregate_id,version) SELECT aggregate_id::uuid,max(aggregate_version) FROM integration_outbox GROUP BY aggregate_id;
ALTER TABLE integration_outbox ALTER COLUMN topic SET NOT NULL;
ALTER TABLE integration_outbox ALTER COLUMN aggregate_version SET NOT NULL;
CREATE UNIQUE INDEX integration_outbox_aggregate_version ON integration_outbox(aggregate_id,aggregate_version);
CREATE TABLE integration_projection_versions(consumer varchar(80) NOT NULL,aggregate_id uuid NOT NULL,version bigint NOT NULL,PRIMARY KEY(consumer,aggregate_id));
