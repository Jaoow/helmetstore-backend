CREATE TABLE sale_item (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    product_variant_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    unit_profit DECIMAL(10,2) NOT NULL,
    total_item_price DECIMAL(10,2) NOT NULL,
    total_item_profit DECIMAL(10,2) NOT NULL,
    
    CONSTRAINT fk_sale_item_sale FOREIGN KEY (sale_id) REFERENCES sale(id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_item_product_variant FOREIGN KEY (product_variant_id) REFERENCES product_variant(id),
    
    CONSTRAINT chk_sale_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_sale_item_unit_price_positive CHECK (unit_price > 0)
);

ALTER TABLE sale 
ADD COLUMN total_amount DECIMAL(12,2),
ADD COLUMN total_profit_new DECIMAL(12,2);

INSERT INTO sale_item (sale_id, product_variant_id, quantity, unit_price, unit_profit, total_item_price, total_item_profit)
SELECT 
    s.id as sale_id,
    s.product_variant_id,
    s.quantity,
    s.unit_price,
    s.total_profit / s.quantity as unit_profit,
    s.unit_price * s.quantity as total_item_price,
    s.total_profit as total_item_profit
FROM sale s 
WHERE s.product_variant_id IS NOT NULL 
  AND s.quantity IS NOT NULL 
  AND s.unit_price IS NOT NULL 
  AND s.total_profit IS NOT NULL;

UPDATE sale s 
SET 
    total_amount = (
        SELECT COALESCE(SUM(si.total_item_price), 0)
        FROM sale_item si 
        WHERE si.sale_id = s.id
    ),
    total_profit_new = (
        SELECT COALESCE(SUM(si.total_item_profit), 0)
        FROM sale_item si 
        WHERE si.sale_id = s.id
    )
WHERE EXISTS (
    SELECT 1 
    FROM sale_item si 
    WHERE si.sale_id = s.id
);

UPDATE sale 
SET 
    total_amount = COALESCE(unit_price * quantity, 0),
    total_profit_new = COALESCE(total_profit, 0)
WHERE total_amount IS NULL;

ALTER TABLE sale 
ALTER COLUMN total_amount SET NOT NULL,
ALTER COLUMN total_profit_new SET NOT NULL;

ALTER TABLE sale 
DROP COLUMN total_profit;

ALTER TABLE sale 
RENAME COLUMN total_profit_new TO total_profit;

CREATE INDEX idx_sale_item_sale_id ON sale_item (sale_id);
CREATE INDEX idx_sale_item_product_variant_id ON sale_item (product_variant_id);

ALTER TABLE sale 
ADD CONSTRAINT chk_sale_total_amount_positive CHECK (total_amount >= 0),
ADD CONSTRAINT chk_sale_total_profit_positive CHECK (total_profit >= 0);
