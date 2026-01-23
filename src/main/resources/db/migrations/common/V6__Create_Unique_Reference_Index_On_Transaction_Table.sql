ALTER TABLE transactions
    ADD CONSTRAINT uc_transactions_reference UNIQUE (reference);

CREATE UNIQUE INDEX index_transaction_ref ON transactions (reference);