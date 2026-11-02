ALTER TABLE schedule_load_facts ADD COLUMN included boolean NOT NULL DEFAULT true;

CREATE TABLE analytics_projection_state(id boolean PRIMARY KEY DEFAULT true CHECK(id),snapshot_at timestamptz NOT NULL);
INSERT INTO analytics_projection_state(snapshot_at) VALUES(clock_timestamp());
