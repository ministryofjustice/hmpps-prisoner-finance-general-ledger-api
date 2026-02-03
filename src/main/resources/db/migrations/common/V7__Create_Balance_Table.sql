CREATE TABLE statement_balances
(
    statement_balance_id        UUID   NOT NULL,
    sub_account_id    UUID   NOT NULL,
    balance_date_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    amount            BIGINT NOT NULL,
    CONSTRAINT pk_statement_balances PRIMARY KEY (statement_balance_id)
);

ALTER TABLE statement_balances
    ADD CONSTRAINT FK_BALANCES_ON_SUB_ACCOUNT FOREIGN KEY (sub_account_id) REFERENCES sub_accounts (sub_account_id);

CREATE INDEX idx_statement_balance_sub_account_id ON statement_balances (sub_account_id);