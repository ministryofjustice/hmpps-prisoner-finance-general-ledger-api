CREATE TEMP TABLE tmp_del_accounts ON COMMIT DROP AS
SELECT account_id FROM accounts WHERE reference ILIKE '%TEST%';

CREATE TEMP TABLE tmp_del_sub_accounts ON COMMIT DROP AS
SELECT sub_account_id FROM sub_accounts
WHERE account_id IN (SELECT account_id FROM tmp_del_accounts);

CREATE TEMP TABLE tmp_del_txs ON COMMIT DROP AS
SELECT DISTINCT transaction_id FROM postings
WHERE sub_account_id IN (SELECT sub_account_id FROM tmp_del_sub_accounts);

DELETE FROM idempotency_keys ik
    USING tmp_del_txs tt
WHERE ik.transaction_id = tt.transaction_id;

DELETE FROM postings p
    USING tmp_del_txs tt
WHERE p.transaction_id = tt.transaction_id;

DELETE FROM transactions t
    USING tmp_del_txs tt
WHERE t.transaction_id = tt.transaction_id;

DELETE FROM statement_balances sb
    USING tmp_del_sub_accounts ts
WHERE sb.sub_account_id = ts.sub_account_id;

DELETE FROM sub_accounts sa
    USING tmp_del_sub_accounts ts
WHERE sa.sub_account_id = ts.sub_account_id;

DELETE FROM accounts a
    USING tmp_del_accounts ta
WHERE a.account_id = ta.account_id;