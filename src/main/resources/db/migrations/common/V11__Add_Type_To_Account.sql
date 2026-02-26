ALTER TABLE accounts
    ADD type VARCHAR(50);

UPDATE accounts
SET type = CASE
    WHEN LENGTH(reference) > 3 THEN 'PRISONER'
    ELSE 'PRISON'
END;

ALTER TABLE accounts
    ALTER COLUMN type SET NOT NULL;