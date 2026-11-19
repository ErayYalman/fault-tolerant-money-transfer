from __future__ import annotations

import os
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from numpy.typing import NDArray
from sklearn.ensemble import IsolationForest
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from app.schemas import AnomalyScoringRequest


FEATURE_NAMES = (
    "amount",
    "transaction_duration_ms",
    "debit_processing_time",
    "credit_processing_time",
    "transactions_last_10min",
    "transactions_last_1h",
)


def default_model_path() -> Path:
    configured_path = os.getenv("ML_MODEL_PATH")

    if configured_path:
        return Path(configured_path)

    return (
        Path(__file__).resolve().parent.parent
        / "model"
        / "isolation_forest.joblib"
    )


def to_feature_vector(
    sample: AnomalyScoringRequest,
) -> NDArray[np.float64]:
    return np.asarray(
        [
            sample.amount,
            sample.transaction_duration_ms,
            sample.debit_processing_time,
            sample.credit_processing_time,
            sample.transactions_last_10min,
            sample.transactions_last_1h,
        ],
        dtype=np.float64,
    )


def build_pipeline(
    features: NDArray[np.float64],
    random_seed: int = 42,
    n_estimators: int = 300,
) -> Pipeline:
    if features.ndim != 2 or features.shape[1] != len(FEATURE_NAMES):
        raise ValueError(
            f"Expected a 2D array with {len(FEATURE_NAMES)} features"
        )

    if features.shape[0] < 100:
        raise ValueError("At least 100 training examples are required")

    if not np.isfinite(features).all():
        raise ValueError("Training features must be finite")

    if np.any(features[:, 0] <= 0):
        raise ValueError("Amounts must be positive")

    if np.any(features[:, 1:4] < 0):
        raise ValueError("Timing features cannot be negative")

    if np.any(features[:, 4:] < 1):
        raise ValueError("Transaction frequency features must be positive")

    if np.any(features[:, 5] < features[:, 4]):
        raise ValueError("Hourly frequency cannot be lower than 10-minute frequency")

    if n_estimators < 1:
        raise ValueError("n_estimators must be positive")

    pipeline = Pipeline(
        [
            ("scaler", StandardScaler()),
            (
                "isolation_forest",
                IsolationForest(
                    n_estimators=n_estimators,
                    contamination="auto",
                    random_state=random_seed,
                    n_jobs=-1,
                ),
            ),
        ]
    )

    pipeline.fit(features)
    return pipeline


def decision_scores_to_anomaly_scores(
    decision_scores: NDArray[np.float64],
) -> NDArray[np.float64]:
    """
    Convert Isolation Forest decision scores into a monotonic
    anomaly signal in the [0, 1] range.

    Higher values indicate stronger anomaly signals.
    These values are not calibrated probabilities.
    """
    if not np.isfinite(decision_scores).all():
        raise ValueError("Decision scores must be finite")

    return (
        1.0
        / (
            1.0
            + np.exp(
                np.clip(8.0 * decision_scores, -60, 60)
            )
        )
    )


def save_model(
    model_path: Path,
    bundle: dict[str, Any],
) -> None:
    model_path.parent.mkdir(parents=True, exist_ok=True)

    temporary_path = model_path.with_suffix(
        model_path.suffix + ".tmp"
    )

    joblib.dump(bundle, temporary_path)
    os.replace(temporary_path, model_path)


def load_model(model_path: Path) -> dict[str, Any]:
    if not model_path.exists():
        raise FileNotFoundError(
            f"Model file not found at {model_path}. "
            "Run `python -m app.train_model` before starting the API."
        )

    bundle = joblib.load(model_path)

    if not isinstance(bundle, dict):
        raise RuntimeError("Invalid model bundle")

    if bundle.get("feature_names") != FEATURE_NAMES:
        raise RuntimeError("Model feature schema does not match this API")

    if "pipeline" not in bundle or "model_version" not in bundle:
        raise RuntimeError("Model bundle is missing required fields")

    pipeline = bundle["pipeline"]

    if not hasattr(pipeline, "decision_function"):
        raise RuntimeError("Model bundle contains an invalid pipeline")

    threshold = bundle.get("anomaly_score_threshold")

    if (
        not isinstance(threshold, (int, float))
        or not np.isfinite(threshold)
        or not 0 <= threshold <= 1
    ):
        raise RuntimeError("Model bundle contains an invalid anomaly threshold")

    return bundle


def score_sample(
    bundle: dict[str, Any],
    sample: AnomalyScoringRequest,
) -> tuple[float, str, bool]:
    features = to_feature_vector(sample).reshape(1, -1)

    if not np.isfinite(features).all():
        raise ValueError("Scoring features must be finite")

    pipeline: Pipeline = bundle["pipeline"]

    decision_score = pipeline.decision_function(features)
    anomaly_score = float(
        decision_scores_to_anomaly_scores(
            np.asarray(decision_score, dtype=np.float64)
        )[0]
    )

    threshold = float(bundle["anomaly_score_threshold"])
    is_anomaly = anomaly_score >= threshold

    return (
        anomaly_score,
        str(bundle["model_version"]),
        is_anomaly,
    )