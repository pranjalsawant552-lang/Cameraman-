-- Every inventory item and product gets a scannable/printable code. The SKU is unique and fits a
-- Code 128 label, so it is used as the barcode wherever none was entered.
UPDATE inventory_items SET barcode = sku WHERE barcode IS NULL OR TRIM(barcode) = '';

UPDATE products
SET sku = 'PRD-' || UPPER(SUBSTRING(REPLACE(id, '-', '') FROM 1 FOR 8))
WHERE sku IS NULL OR TRIM(sku) = '';
