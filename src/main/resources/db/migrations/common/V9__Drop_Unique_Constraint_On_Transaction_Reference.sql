ALTER TABLE transactions
    DROP CONSTRAINT uc_transactions_reference;

DROP INDEX index_transaction_ref;

CREATE INDEX index_transaction_ref ON transactions (reference);