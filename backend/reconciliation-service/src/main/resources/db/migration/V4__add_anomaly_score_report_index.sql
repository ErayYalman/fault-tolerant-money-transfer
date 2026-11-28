CREATE INDEX idx_anomaly_scores_run_scored_at
    ON anomaly_scores (
        run_id,
        scored_at DESC,
        transfer_id,
        id
    );
