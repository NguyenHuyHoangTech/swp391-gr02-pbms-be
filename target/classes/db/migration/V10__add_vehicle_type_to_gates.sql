ALTER TABLE dbo.gates ADD vehicle_type_id BIGINT;
ALTER TABLE dbo.gates ADD CONSTRAINT FK_gates_vehicle_types FOREIGN KEY (vehicle_type_id) REFERENCES dbo.vehicle_types(id);
