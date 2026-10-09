INSERT INTO loyalty_rules (
    amount_per_point,
    point_value_lkr,
    min_redeem_points,
    redeem_step,
    max_discount_percent,
    reservation_timeout_minutes,
    silver_threshold,
    silver_multiplier,
    gold_threshold,
    gold_multiplier,
    updated_by,
    created_at,
    updated_at
) VALUES (
    100.00,
    0.50,
    100,
    10,
    50,
    15,
    1000,
    1.25,
    3000,
    1.50,
    'system',
    now(),
    now()
)
ON CONFLICT DO NOTHING;
