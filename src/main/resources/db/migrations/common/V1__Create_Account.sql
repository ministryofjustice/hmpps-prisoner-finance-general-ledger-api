CREATE TABLE accounts
(
    account_id UUID                        NOT NULL,
    created_by VARCHAR(255)                NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    reference  VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (account_id)
);

ALTER TABLE accounts
    ADD CONSTRAINT uc_accounts_reference UNIQUE (reference);