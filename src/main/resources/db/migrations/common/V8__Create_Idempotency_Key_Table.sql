CREATE TABLE idempotency_keys
(
    id             UUID NOT NULL,
    transaction_id UUID NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (id)
);

ALTER TABLE idempotency_keys
    ADD CONSTRAINT uc_idempotency_keys_transaction UNIQUE (transaction_id);

ALTER TABLE idempotency_keys
    ADD CONSTRAINT FK_IDEMPOTENCY_KEYS_ON_TRANSACTION FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id);