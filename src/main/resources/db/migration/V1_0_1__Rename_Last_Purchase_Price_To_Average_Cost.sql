-- Rename last_purchase_price to average_cost in inventory_item table
ALTER TABLE inventory_item RENAME COLUMN last_purchase_price TO average_cost;