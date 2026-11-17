ALTER TABLE transfers
    ADD COLUMN owner_subject VARCHAR(255);

-- Existing records must not become accessible to arbitrary users.
UPDATE transfers
SET owner_subject = '__legacy_unowned__'
WHERE owner_subject IS NULL;

ALTER TABLE transfers
    ALTER COLUMN owner_subject SET NOT NULL;

-- Idempotency must be scoped to the authenticated user.
ALTER TABLE transfers
    DROP CONSTRAINT IF EXISTS transfers_idempotency_key_key;

ALTER TABLE transfers
    ADD CONSTRAINT uq_transfers_owner_idempotency_key
    UNIQUE (owner_subject, idempotency_key);