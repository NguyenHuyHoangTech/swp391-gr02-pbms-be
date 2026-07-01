ALTER TABLE vehicle_types
ADD requires_plate BIT DEFAULT 1;

ALTER TABLE vehicle_types
ADD default_plate VARCHAR(50) NULL;
