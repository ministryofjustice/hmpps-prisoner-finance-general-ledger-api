CREATE TABLE sub_accounts
(
    sub_account_id UUID         NOT NULL,
    account_id     UUID         NOT NULL,
    reference      VARCHAR(255) NOT NULL,
    created_by     VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_sub_accounts PRIMARY KEY (sub_account_id)
);

ALTER TABLE sub_accounts
    ADD CONSTRAINT sub_account_unique_within_account UNIQUE (account_id, reference);

ALTER TABLE sub_accounts
    ADD CONSTRAINT FK_SUB_ACCOUNTS_ON_ACCOUNT FOREIGN KEY (account_id) REFERENCES accounts (account_id);