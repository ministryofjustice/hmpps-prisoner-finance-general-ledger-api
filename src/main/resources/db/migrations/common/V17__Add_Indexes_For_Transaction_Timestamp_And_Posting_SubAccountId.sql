CREATE INDEX
    index_transactions_timestamp ON transactions (timestamp);

CREATE INDEX
    index_postings_sub_account_id ON postings (sub_account_id);