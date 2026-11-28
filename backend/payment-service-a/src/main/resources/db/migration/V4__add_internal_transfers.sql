ALTER TABLE ledger_entries
    DROP CONSTRAINT chk_ledger_entries_entry_type;

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_entries_entry_type
    CHECK (entry_type IN ('DEBIT', 'CREDIT', 'REVERSAL'));


CREATE TABLE internal_transfers (
    id UUID PRIMARY KEY,

    correlation_id UUID NOT NULL UNIQUE,

    owner_subject VARCHAR(255) NOT NULL,

    from_account_id UUID NOT NULL,
    to_account_id UUID NOT NULL,

    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_internal_transfers_from_account
        FOREIGN KEY (from_account_id)
        REFERENCES accounts(id),

    CONSTRAINT fk_internal_transfers_to_account
        FOREIGN KEY (to_account_id)
        REFERENCES accounts(id),

    CONSTRAINT chk_internal_transfers_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_internal_transfers_distinct_accounts
        CHECK (from_account_id <> to_account_id),

    CONSTRAINT chk_internal_transfers_currency_format
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT uq_internal_transfers_owner_idempotency
        UNIQUE (owner_subject, idempotency_key)
);


CREATE INDEX idx_internal_transfers_created_at
    ON internal_transfers(created_at DESC, id DESC);


CREATE INDEX idx_internal_transfers_from_account
    ON internal_transfers(from_account_id, created_at DESC);


CREATE INDEX idx_internal_transfers_to_account
    ON internal_transfers(to_account_id, created_at DESC);
