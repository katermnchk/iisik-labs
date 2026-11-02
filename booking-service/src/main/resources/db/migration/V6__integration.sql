CREATE TABLE integration_outbox(id uuid PRIMARY KEY,aggregate_id varchar(64) NOT NULL,event_type varchar(80) NOT NULL,payload text NOT NULL,correlation_id varchar(64) NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),published_at timestamptz);
CREATE INDEX integration_outbox_pending ON integration_outbox(created_at) WHERE published_at IS NULL;
CREATE TABLE integration_inbox(consumer varchar(80) NOT NULL,event_id uuid NOT NULL,processed_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(consumer,event_id));
