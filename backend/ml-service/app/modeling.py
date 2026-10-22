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

    return Path(__file__).resolve().parent.parent / "model" / "isolation_forest.joblib"


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


def build_synthetic_baseline(
    sample_count: int = 6000,
    random_seed: int = 42,
) -> NDArray[np.float64]:
    """Create a deterministic synthetic baseline for this prototype."""
    if sample_count < 100:
        raise ValueError("sample_count must be at least 100")

    rng = np.random.default_rng(random_seed)

    amounts = np.clip(
        rng.lognormal(mean=np.log(250), sigma=0.9, size=sample_count),
        1,
        20_000,
    )

    debit_processing = np.clip(
        rng.lognormal(mean=np.log(350), sigma=0.65, size=sample_count),
        1,
        20_000,
    )

    credit_processing = np.clip(
        rng.lognormal(mean=np.log(450), sigma=0.65, size=sample_count),
        1,
        20_000,
    )

    network_and_queue_delay = np.clip(
        rng.lognormal(mean=np.log(300), sigma=0.7, size=sample_count),
        1,
        60_000,
    )

    transaction_duration = (
        debit_processing + credit_processing + network_and_queue_delay
    )

    transactions_last_10min = rng.poisson(
        lam=1.2,
        size=sample_count,
    ) + 1

    transactions_last_1h = (
        transactions_last_10min
        + rng.poisson(lam=3.0, size=sample_count)
    )

    return np.column_stack(
        [
            amounts,
            transaction_duration,
            debit_processing,
            credit_processing,
            transactions_last_10min,
            transactions_last_1h,
        ]
    ).astype(np.float64)


def build_pipeline(
    features: NDArray[np.float64],
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

    pipeline = Pipeline(
        [
            ("scaler", StandardScaler()),
            (
                "isolation_forest",
                IsolationForest(
                    n_estimators=200,
                    contamination=0.03,
                    random_state=42,
                    n_jobs=-1,
                ),
            ),
        ]
    )

    pipeline.fit(features)
    return pipeline


def create_model_bundle(
    model_version: str = "iforest-synthetic-v1",
) -> dict[str, Any]:
    features = build_synthetic_baseline()
    pipeline = build_pipeline(features)

    return {
        "pipeline": pipeline,
        "model_version": model_version,
        "feature_names": FEATURE_NAMES,
    }


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

    return bundle


def score_sample(
    bundle: dict[str, Any],
    sample: AnomalyScoringRequest,
) -> tuple[float, str, bool]:
    features = to_feature_vector(sample).reshape(1, -1)

    if not np.isfinite(features).all():
        raise ValueError("Scoring features must be finite")

    pipeline: Pipeline = bundle["pipeline"]

    decision_score = float(pipeline.decision_function(features)[0])
    prediction = int(pipeline.predict(features)[0])

    # Higher value means a stronger anomaly signal.
    probability_like_score = 1.0 / (
        1.0 + np.exp(np.clip(8.0 * decision_score, -60, 60))
    )

    anomaly_score = float(
        np.clip(probability_like_score, 0.0, 1.0)
    )

    return (
        anomaly_score,
        str(bundle["model_version"]),
        prediction == -1,
    )


def features_from_samples(
    samples: list[AnomalyScoringRequest],
) -> NDArray[np.float64]:
    if len(samples) < 100:
        raise ValueError("At least 100 training examples are required")

    return np.vstack([to_feature_vector(sample) for sample in samples])