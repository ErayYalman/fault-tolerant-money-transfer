CREATE TABLE inbox_messages (
    event_id UUID PRIMARY KEY,

    event_type VARCHAR(100) NOT NULL,
    correlation_id UUID NOT NULL,

    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);