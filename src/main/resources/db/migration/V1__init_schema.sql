-- 1. Users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Product Catalog
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(100),
    scale_type VARCHAR(50),
    release_date DATE,
    full_price DECIMAL(12, 2) NOT NULL,
    dp_price DECIMAL(12, 2) NOT NULL,
    stock_slot INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    CONSTRAINT chk_price CHECK (dp_price > 0 AND full_price >= dp_price)
);

-- 3. Orders Header
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    total_amount DECIMAL(12, 2) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    fulfillment_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Order Items
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(12, 2) NOT NULL,
    dp_unit_price DECIMAL(12, 2) NOT NULL
);

-- 5. Payments Ledger
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_number VARCHAR(50) UNIQUE NOT NULL,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    payment_type VARCHAR(20) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    snap_token VARCHAR(255),
    midtrans_transaction_id VARCHAR(100),
    paid_at TIMESTAMP,
    expired_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Audit trail status order
CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    note TEXT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
