ALTER TABLE accounts
    ADD COLUMN owner_subject VARCHAR(255);

-- Existing accounts are deliberately not assigned
-- to an arbitrary Keycloak user.
UPDATE accounts
SET owner_subject = '__legacy_unowned__'
WHERE owner_subject IS NULL;

ALTER TABLE accounts
    ALTER COLUMN owner_subject SET NOT NULL;

CREATE INDEX idx_accounts_owner_subject
    ON accounts(owner_subject);
