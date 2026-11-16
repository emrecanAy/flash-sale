-- Loaded only with the `local` profile.
INSERT INTO products (sku, name, price, available_stock, created_at)
VALUES ('KEYB-75-LE',  'Mechanical Keyboard 75% - Limited Edition', 4299.00,  50, now()),
       ('SNKR-RETRO',  'Retro Runner Sneakers',                     3499.90, 120, now()),
       ('CONSOLE-PRO', 'Game Console Pro Bundle',                  27999.00,   5, now())
ON CONFLICT (sku) DO NOTHING;
