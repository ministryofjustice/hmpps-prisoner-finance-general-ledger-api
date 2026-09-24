CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- noinspection SqlResolve
CREATE INDEX idx_transaction_description_trgm
    ON transactions
        USING GIN (LOWER(description) gin_trgm_ops);