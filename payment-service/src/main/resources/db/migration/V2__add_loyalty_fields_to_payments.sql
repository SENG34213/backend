ALTER TABLE payments
    ADD COLUMN original_amount NUMERIC(10,2) NULL,
    ADD COLUMN loyalty_points_used INT NOT NULL DEFAULT 0,
    ADD COLUMN loyalty_discount NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN recorded_by UUID NULL;
