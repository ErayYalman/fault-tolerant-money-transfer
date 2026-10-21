CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    business_date DATE NOT NULL,

    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,

    run_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',

    transfers_checked INTEGER NOT NULL DEFAULT 0,
    discrepancies_found INTEGER NOT NULL DEFAULT 0,
    anomalies_found INTEGER NOT NULL DEFAULT 0,

    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,

    error_message TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_reconciliation_run_period
        CHECK (period_start < period_end),

    CONSTRAINT chk_reconciliation_run_type
        CHECK (run_type IN ('SCHEDULED', 'MANUAL')),

    CONSTRAINT chk_reconciliation_run_status
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),

    CONSTRAINT chk_reconciliation_run_counts
        CHECK (
            transfers_checked >= 0
            AND discrepancies_found >= 0
            AND anomalies_found >= 0
        )
);


CREATE INDEX idx_reconciliation_runs_business_date
    ON reconciliation_runs(business_date, run_type);

CREATE INDEX idx_reconciliation_runs_started_at
    ON reconciliation_runs(started_at DESC);


CREATE TABLE reconciliation_discrepancies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    run_id UUID NOT NULL,
    transfer_id UUID NOT NULL,
    correlation_id UUID NOT NULL,

    rule_code VARCHAR(40) NOT NULL,

    details JSONB NOT NULL DEFAULT '{}'::jsonb,

    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_discrepancies_run
        FOREIGN KEY (run_id)
        REFERENCES reconciliation_runs(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_discrepancy_rule_code
        CHECK (
            rule_code IN (
                'MISSING_CREDIT',
                'AMOUNT_MISMATCH',
                'STATUS_MISMATCH'
            )
        ),

    CONSTRAINT uq_discrepancy_run_correlation_rule
        UNIQUE (run_id, correlation_id, rule_code)
);


CREATE INDEX idx_discrepancies_run_id
    ON reconciliation_discrepancies(run_id);

CREATE INDEX idx_discrepancies_correlation_id
    ON reconciliation_discrepancies(correlation_id);


CREATE TABLE anomaly_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    run_id UUID NOT NULL,
    transfer_id UUID NOT NULL,
    correlation_id UUID NOT NULL,

    anomaly_score DOUBLE PRECISION NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    is_anomaly BOOLEAN NOT NULL,

    scored_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_anomaly_scores_run
        FOREIGN KEY (run_id)
        REFERENCES reconciliation_runs(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_anomaly_score_run_correlation
        UNIQUE (run_id, correlation_id)
);


CREATE INDEX idx_anomaly_scores_run_id
    ON anomaly_scores(run_id);

CREATE INDEX idx_anomaly_scores_correlation_id
    ON anomaly_scores(correlation_id);