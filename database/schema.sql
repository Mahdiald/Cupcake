-- Kør efter CREATE DATABASE cupcakes; i pgAdmin.
CREATE TABLE IF NOT EXISTS users (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 email VARCHAR(255) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(12) NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER','ADMIN')),
 balance NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (balance >= 0)
);
CREATE TABLE IF NOT EXISTS bottoms (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 name VARCHAR(80) NOT NULL UNIQUE,
 price NUMERIC(10,2) NOT NULL CHECK(price >= 0)
);
CREATE TABLE IF NOT EXISTS toppings (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 name VARCHAR(80) NOT NULL UNIQUE,
 price NUMERIC(10,2) NOT NULL CHECK(price >= 0)
);
CREATE TABLE IF NOT EXISTS orders (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 total NUMERIC(12,2) NOT NULL CHECK(total >= 0)
);
CREATE TABLE IF NOT EXISTS order_lines (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
 bottom_id BIGINT NOT NULL REFERENCES bottoms(id),
 topping_id BIGINT NOT NULL REFERENCES toppings(id),
 quantity INTEGER NOT NULL CHECK(quantity > 0),
 unit_price NUMERIC(10,2) NOT NULL CHECK(unit_price >= 0)
);
INSERT INTO bottoms(name,price) VALUES ('Chokolade',5),('Vanilje',5),('Muskatnød',5),('Pistacie',6),('Mandel',7) ON CONFLICT(name) DO NOTHING;
INSERT INTO toppings(name,price) VALUES ('Chokolade',5),('Blåbær',5),('Hindbær',5),('Crispy',6),('Jordbær',6),('Rom/rosin',7),('Appelsin',8),('Citron',8),('Blåskimmelost',9) ON CONFLICT(name) DO NOTHING;
-- Giv test-kunde penge via pgAdmin (som opgaven kræver):
-- UPDATE users SET balance=200 WHERE email='kunde@example.dk';
-- Giv test-bruger administratorrolle (kun til lokal demo):
-- UPDATE users SET role='ADMIN' WHERE email='admin@example.dk';
