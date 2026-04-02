INSERT INTO tier_configs (id, name, spend_threshold_min, spend_threshold_max, rank, created_at)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'Bronze', 0.00, 499.99, 1, NOW()),
    ('00000000-0000-0000-0000-000000000102', 'Silver', 500.00, 1499.99, 2, NOW()),
    ('00000000-0000-0000-0000-000000000103', 'Gold', 1500.00, NULL, 3, NOW());

INSERT INTO users (
    id,
    email,
    password_hash,
    phone,
    display_name,
    role,
    status,
    failed_login_attempts,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000201',
    'admin@heritage.local',
    '$2a$12$C6UzMDM.H6dfI/f/IKcEeO7j5B7sCv0xO0jzAm8h1Hn0KD7RyhokW',
    '5551231234',
    'Heritage Administrator',
    'ADMIN',
    'ACTIVE',
    0,
    NOW(),
    NOW()
);

INSERT INTO warehouses (id, name, address, latitude, longitude, status)
VALUES
    (
        '00000000-0000-0000-0000-000000000301',
        'Central Heritage Fulfillment Hub',
        '125 Market Hall Ave, Old Town District',
        40.7127760,
        -74.0059740,
        'ACTIVE'
    ),
    (
        '00000000-0000-0000-0000-000000000302',
        'Riverside Artisan Storage',
        '88 Riverbend Lane, Heritage Wharf',
        40.7061920,
        -74.0091600,
        'ACTIVE'
    );

INSERT INTO benefit_packages (
    id,
    tier_id,
    name,
    type,
    value,
    scope_category,
    scope_seller_id,
    scope_date_start,
    scope_date_end,
    stackable,
    mutual_exclusion_group,
    priority
)
VALUES
    (
        '00000000-0000-0000-0000-000000000401',
        '00000000-0000-0000-0000-000000000101',
        'Bronze Free Shipping',
        'FREE_SHIPPING',
        0.00,
        NULL,
        NULL,
        NULL,
        NULL,
        TRUE,
        'SHIPPING_RULE',
        10
    ),
    (
        '00000000-0000-0000-0000-000000000402',
        '00000000-0000-0000-0000-000000000102',
        'Silver 10 Percent Discount',
        'PERCENTAGE_DISCOUNT',
        10.00,
        NULL,
        NULL,
        NULL,
        NULL,
        TRUE,
        'PRICE_RULE',
        100
    ),
    (
        '00000000-0000-0000-0000-000000000403',
        '00000000-0000-0000-0000-000000000102',
        'Silver Free Shipping',
        'FREE_SHIPPING',
        0.00,
        NULL,
        NULL,
        NULL,
        NULL,
        TRUE,
        'SHIPPING_RULE',
        20
    ),
    (
        '00000000-0000-0000-0000-000000000404',
        '00000000-0000-0000-0000-000000000103',
        'Gold Exclusive Pricing',
        'EXCLUSIVE_PRICE',
        85.00,
        NULL,
        NULL,
        NULL,
        NULL,
        FALSE,
        'PRICE_RULE',
        300
    ),
    (
        '00000000-0000-0000-0000-000000000405',
        '00000000-0000-0000-0000-000000000103',
        'Gold 15 Percent Discount',
        'PERCENTAGE_DISCOUNT',
        15.00,
        NULL,
        NULL,
        NULL,
        NULL,
        TRUE,
        'PRICE_RULE',
        200
    ),
    (
        '00000000-0000-0000-0000-000000000406',
        '00000000-0000-0000-0000-000000000103',
        'Gold Free Shipping',
        'FREE_SHIPPING',
        0.00,
        NULL,
        NULL,
        NULL,
        NULL,
        TRUE,
        'SHIPPING_RULE',
        30
    );
