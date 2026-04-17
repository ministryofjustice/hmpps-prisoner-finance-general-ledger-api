CREATE TABLE posting_balance
(
    posting_balance_id       UUID NOT NULL,
    posting_id                          UUID NOT NULL,
    total_sub_account_balance           BIGINT NOT NULL DEFAULT 0,
    total_account_balance               BIGINT NOT NULL DEFAULT 0,
    created_at                          TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_posting_balance
    PRIMARY KEY (posting_balance_id),

    CONSTRAINT uq_posting_balance_posting_id
    UNIQUE (posting_id),

    CONSTRAINT fk_posting_balance_on_posting
    FOREIGN KEY (posting_id)
    REFERENCES postings (posting_id)
);

CREATE INDEX idx_posting_balance_posting_id
    ON posting_balance(posting_id);