CREATE TABLE transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    correlation_id UUID NOT NULL UNIQUE,

    from_account_id UUID NOT NULL,
    to_account_id UUID NOT NULL,

    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY',

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,

    idempotency_key VARCHAR(100) NOT NULL UNIQUE,

    started_at TIMESTAMPTZ,
    debit_requested_at TIMESTAMPTZ,
    debit_completed_at TIMESTAMPTZ,
    credit_requested_at TIMESTAMPTZ,
    credit_completed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_transfers_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_transfers_different_accounts
        CHECK (from_account_id <> to_account_id)
);


CREATE TABLE saga_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    transfer_id UUID NOT NULL UNIQUE,

    current_step VARCHAR(50) NOT NULL,

    payload JSONB,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_saga_state_transfer
        FOREIGN KEY (transfer_id)
        REFERENCES transfers(id)
);


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


CREATE INDEX idx_transfers_correlation_id
    ON transfers(correlation_id);


CREATE INDEX idx_transfers_created_at
    ON transfers(created_at);