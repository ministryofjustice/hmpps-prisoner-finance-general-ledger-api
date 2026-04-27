CREATE TEMP TABLE test_txs AS
SELECT DISTINCT p.transaction_id
FROM accounts a
         JOIN sub_accounts sa ON a.account_id = sa.account_id
         JOIN postings p ON sa.sub_account_id = p.sub_account_id
WHERE a.reference ILIKE '%TEST%';

CREATE TEMP TABLE test_postings AS
SELECT posting_id
FROM postings
WHERE transaction_id IN (SELECT transaction_id FROM test_txs);

CREATE TEMP TABLE test_accounts AS
SELECT account_id FROM accounts WHERE reference ILIKE '%TEST%';

CREATE TEMP TABLE test_subaccounts AS
SELECT sub_account_id FROM sub_accounts
WHERE account_id IN (SELECT account_id FROM test_accounts);

UPDATE postings SET posting_balance_id = NULL
WHERE posting_id IN (SELECT posting_id FROM test_postings);

DELETE FROM idempotency_keys
WHERE transaction_id IN (SELECT transaction_id FROM test_txs);

DELETE FROM posting_balance
WHERE posting_id IN (SELECT posting_id FROM test_postings);;

DELETE FROM postings
WHERE posting_id IN (SELECT posting_id FROM test_postings);;

DELETE FROM transactions
WHERE transaction_id IN (SELECT transaction_id FROM test_txs);

DELETE FROM statement_balances
WHERE sub_account_id IN (SELECT sub_account_id FROM test_subaccounts);

DELETE FROM sub_accounts
WHERE sub_account_id IN (SELECT sub_account_id FROM test_subaccounts);

DELETE FROM accounts
WHERE account_id IN (SELECT account_id FROM test_accounts);

DROP TABLE test_txs;
DROP TABLE test_postings;
DROP TABLE test_subaccounts;
DROP TABLE test_accounts;