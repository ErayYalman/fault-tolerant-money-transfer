CREATE INDEX idx_reconciliation_runs_history
    ON reconciliation_runs (
        business_date DESC,
        started_at DESC,
        id DESC
    );
