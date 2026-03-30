ALTER TABLE postings
    ADD COLUMN entry_sequence integer NOT NULL DEFAULT 0;

ALTER TABLE transactions
    ADD COLUMN entry_sequence integer NOT NULL DEFAULT 0;
