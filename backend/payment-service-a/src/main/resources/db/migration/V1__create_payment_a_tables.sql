CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    account_number VARCHAR(50) NOT NULL UNIQUE,

    balance DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_accounts_balance_non_negative
        CHECK (balance >= 0),

    CONSTRAINT chk_accounts_currency_format
        CHECK (currency ~ '^[A-Z]{3}$')
);


CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    transfer_id UUID NOT NULL,
    correlation_id UUID NOT NULL,

    account_id UUID NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,

    balance_before DECIMAL(19, 4) NOT NULL,
    balance_after DECIMAL(19, 4) NOT NULL,

    entry_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ledger_entries_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(id),

    CONSTRAINT chk_ledger_entries_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_ledger_entries_entry_type
        CHECK (entry_type IN ('DEBIT', 'REVERSAL')),

    CONSTRAINT uq_ledger_correlation_entry_type
        UNIQUE (correlation_id, entry_type)
);


CREATE INDEX idx_ledger_entries_created_at
    ON ledger_entries(created_at);


CREATE INDEX idx_ledger_entries_account_created_at
    ON ledger_entries(account_id, created_at);


CREATE TABLE inbox_messages (
    message_id UUID PRIMARY KEY,

    idempotency_key VARCHAR(100) NOT NULL UNIQUE,

    transfer_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    command_type VARCHAR(100) NOT NULL,

    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_inbox_correlation_command
        UNIQUE (correlation_id, command_type)
);


CREATE INDEX idx_inbox_messages_correlation_id
    ON inbox_messages(correlation_id);


CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,

    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);


CREATE INDEX idx_outbox_events_pending
    ON outbox_events(status, created_at)
    WHERE status = 'PENDING';