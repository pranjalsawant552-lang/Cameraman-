-- Seed a starter product catalog so the billing "Browse Catalog" picker isn't
-- empty on first use — covers exactly what was asked for: photo copies by size
-- (priced per copy, so quantity in the invoice line item naturally handles "how
-- many copies"), frames, and a few other common studio counter-sale items. Admin
-- can edit prices/add more from the Products page at any time.
--
-- Guarded with NOT EXISTS throughout: V2__seed_data.sql already seeds a
-- 'Frames' category (for inventory, not billing), and product_categories.name
-- has no unique constraint — inserting 'Frames' again here unconditionally
-- created a second row with the same name, which then made every category
-- lookup below match two rows instead of one, silently doubling every insert
-- under it and failing on the first repeated SKU. The NOT EXISTS guards below
-- reuse an existing same-named category instead of duplicating it, and also
-- make this migration safe to reason about even if it's ever adapted/rerun.

INSERT INTO product_categories (id, name, description, active)
SELECT gen_random_uuid()::text, v.name, v.description, TRUE
FROM (VALUES
  ('Photo Prints', 'Printed photo copies, priced per copy'),
  ('Frames', 'Photo frames in various sizes'),
  ('Albums & USB', 'Physical delivery add-ons')
) AS v(name, description)
WHERE NOT EXISTS (SELECT 1 FROM product_categories pc WHERE pc.name = v.name);

-- Photo Prints (priced per single copy — invoice quantity = number of copies)
INSERT INTO products (id, category_id, name, sku, price, gst_rate, stock_quantity, active)
SELECT gen_random_uuid()::text,
       (SELECT id FROM product_categories WHERE name = 'Photo Prints' ORDER BY created_at LIMIT 1),
       x.name, x.sku, x.price, 18.00, 0, TRUE
FROM (VALUES
  ('Passport Size Photo (per copy)', 'PRINT-PASSPORT', 10.00),
  ('4x6 Photo Print (per copy)', 'PRINT-4X6', 15.00),
  ('5x7 Photo Print (per copy)', 'PRINT-5X7', 25.00),
  ('8x10 Photo Print (per copy)', 'PRINT-8X10', 60.00),
  ('12x18 Photo Print (per copy)', 'PRINT-12X18', 150.00),
  ('A4 Photo Print (per copy)', 'PRINT-A4', 100.00)
) AS x(name, sku, price)
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.sku = x.sku);

-- Frames
INSERT INTO products (id, category_id, name, sku, price, gst_rate, stock_quantity, active)
SELECT gen_random_uuid()::text,
       (SELECT id FROM product_categories WHERE name = 'Frames' ORDER BY created_at LIMIT 1),
       x.name, x.sku, x.price, 18.00, 0, TRUE
FROM (VALUES
  ('4x6 Photo Frame', 'FRAME-4X6', 120.00),
  ('5x7 Photo Frame', 'FRAME-5X7', 180.00),
  ('8x10 Photo Frame', 'FRAME-8X10', 350.00),
  ('12x18 Photo Frame', 'FRAME-12X18', 650.00),
  ('Table-Top Frame (Small)', 'FRAME-TABLE-S', 90.00)
) AS x(name, sku, price)
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.sku = x.sku);

-- Albums & USB
INSERT INTO products (id, category_id, name, sku, price, gst_rate, stock_quantity, active)
SELECT gen_random_uuid()::text,
       (SELECT id FROM product_categories WHERE name = 'Albums & USB' ORDER BY created_at LIMIT 1),
       x.name, x.sku, x.price, 18.00, 0, TRUE
FROM (VALUES
  ('USB Drive (16GB, with all edited photos)', 'USB-16GB', 300.00),
  ('Photo CD/DVD', 'DISC-PHOTO', 150.00)
) AS x(name, sku, price)
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.sku = x.sku);
