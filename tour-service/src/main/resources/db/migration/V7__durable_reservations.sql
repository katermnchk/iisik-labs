CREATE TABLE rpc_reservations(booking_id uuid PRIMARY KEY,payload text NOT NULL,status varchar(20) NOT NULL CHECK(status IN ('HELD','RELEASED')),created_at timestamptz NOT NULL DEFAULT now());
