CREATE TABLE log_sqs_calculated_balances
(
    id         UUID                                    NOT NULL,
    posting_id UUID                                    NOT NULL,
    account_id UUID                                    NOT NULL,
    status     VARCHAR,
    timestamp  TIMESTAMP WITHOUT TIME ZONE             NOT NULL,
    CONSTRAINT pk_log_sqs_calculated_balances PRIMARY KEY (id)
);
