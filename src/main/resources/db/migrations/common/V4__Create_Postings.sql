CREATE TABLE postings
(
    posting_id     UUID                        NOT NULL,
    created_by     VARCHAR(255)                NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    type           VARCHAR(255)                NOT NULL,
    amount         DECIMAL                     NOT NULL,
    sub_account_id UUID                        NOT NULL,
    transaction_id UUID                        NOT NULL,
    CONSTRAINT pk_postings PRIMARY KEY (posting_id)
);

ALTER TABLE postings
    ADD CONSTRAINT uc_postings_amount UNIQUE (amount);

ALTER TABLE postings
    ADD CONSTRAINT FK_POSTINGS_ON_SUB_ACCOUNT FOREIGN KEY (sub_account_id) REFERENCES sub_accounts (sub_account_id);

ALTER TABLE postings
    ADD CONSTRAINT FK_POSTINGS_ON_TRANSACTION FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id);

CREATE INDEX index_postings_transactions_id ON postings (transaction_id);