ALTER TABLE reconciliation_runs
    ADD COLUMN anomaly_scoring_status VARCHAR(20) NOT NULL DEFAULT 'NOT_RUN',
    ADD COLUMN anomaly_scoring_attempted_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN anomaly_scoring_failed_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN anomaly_scoring_error_message TEXT;

ALTER TABLE reconciliation_runs
    ADD CONSTRAINT chk_reconciliation_run_anomaly_scoring_status
        CHECK (
            anomaly_scoring_status IN (
                'NOT_RUN',
                'NOT_REQUIRED',
                'COMPLETED',
                'PARTIAL',
                'UNAVAILABLE'
            )
        );

ALTER TABLE reconciliation_runs
    ADD CONSTRAINT chk_reconciliation_run_anomaly_scoring_counts
        CHECK (
            anomaly_scoring_attempted_count >= 0
            AND anomaly_scoring_failed_count >= 0
            AND anomaly_scoring_failed_count
                <= anomaly_scoring_attempted_count
        );