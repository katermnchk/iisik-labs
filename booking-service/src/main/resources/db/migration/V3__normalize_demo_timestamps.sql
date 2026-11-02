UPDATE bookings SET updated_at = GREATEST(created_at, updated_at);
