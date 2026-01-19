CREATE TABLE transactions
(
    transaction_id UUID         NOT NULL,
    created_by     VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    reference      VARCHAR(255) NOT NULL,
    description    VARCHAR(255),
    timestamp      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    amount         DECIMAL      NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (transaction_id)
);

ALTER TABLE transactions
    ADD CONSTRAINT uc_transactions_amount UNIQUE (amount);