ALTER TABLE notification_deliveries DROP CONSTRAINT notification_deliveries_channel_check;
ALTER TABLE notification_deliveries ADD CHECK(channel IN ('EMAIL','PUSH','IN_APP'));
