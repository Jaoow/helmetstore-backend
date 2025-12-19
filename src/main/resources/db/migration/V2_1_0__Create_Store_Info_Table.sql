CREATE TABLE IF NOT EXISTS store_info (
    id BIGSERIAL PRIMARY KEY,
    inventory_id BIGINT NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    cnpj VARCHAR(18),
    email VARCHAR(100),
    website VARCHAR(100),
    CONSTRAINT fk_store_info_inventory FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE
);
