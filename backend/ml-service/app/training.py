from __future__ import annotations

import csv
import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from numpy.typing import NDArray

from app.modeling import (
    FEATURE_NAMES,
    build_pipeline,
    decision_scores_to_anomaly_scores,
    save_model,
)
from app.synthetic_data import (
    LABEL_COUNTS,
    SPLIT_RATIOS,
    TOTAL_SAMPLE_COUNT,
)


ANOMALY_LABELS = (
    "AMOUNT",
    "FREQUENCY",
    "LATENCY",
)


@dataclass(frozen=True)
class DatasetPartition:
    features: NDArray[np.float64]
    labels: NDArray[np.str_]


def _expected_split_label_counts() -> dict[tuple[str, str], int]:
    expected: dict[tuple[str, str], int] = {}

    for label, total_count in LABEL_COUNTS.items():
        train_count = int(
            total_count * SPLIT_RATIOS["train"]
        )

        validation_count = int(
            total_count * SPLIT_RATIOS["validation"]
        )

        test_count = (
            total_count
            - train_count
            - validation_count
        )

        expected[("train", label)] = train_count
        expected[("validation", label)] = validation_count
        expected[("test", label)] = test_count

    return expected


def load_dataset(
    dataset_path: Path,
) -> dict[str, DatasetPartition]:
    if not dataset_path.exists():
        raise FileNotFoundError(
            f"Dataset not found: {dataset_path}"
        )

    required_columns = {
        *FEATURE_NAMES,
        "label",
        "split",
    }

    with dataset_path.open(
        "r",
        encoding="utf-8",
        newline="",
    ) as csv_file:
        reader = csv.DictReader(csv_file)

        if reader.fieldnames is None:
            raise ValueError("Dataset CSV has no header")

        missing_columns = required_columns - set(reader.fieldnames)

        if missing_columns:
            raise ValueError(
                f"Dataset is missing columns: {sorted(missing_columns)}"
            )

        rows = list(reader)

    if len(rows) != TOTAL_SAMPLE_COUNT:
        raise ValueError(
            f"Expected {TOTAL_SAMPLE_COUNT} rows, found {len(rows)}"
        )

    actual_counts: Counter[tuple[str, str]] = Counter()

    partition_rows: dict[str, list[tuple[list[float], str]]] = {
        "train": [],
        "validation": [],
        "test": [],
    }

    for row_number, row in enumerate(rows, start=2):
        label = row["label"]
        split = row["split"]

        if label not in LABEL_COUNTS:
            raise ValueError(
                f"Unknown label '{label}' at CSV row {row_number}"
            )

        if split not in partition_rows:
            raise ValueError(
                f"Unknown split '{split}' at CSV row {row_number}"
            )

        try:
            values = [
                float(row[feature])
                for feature in FEATURE_NAMES
            ]
        except (TypeError, ValueError) as exception:
            raise ValueError(
                f"Invalid feature value at CSV row {row_number}"
            ) from exception

        features = np.asarray(values, dtype=np.float64)

        if not np.isfinite(features).all():
            raise ValueError(
                f"Non-finite feature value at CSV row {row_number}"
            )

        amount, duration, debit_time, credit_time, last_10min, last_1h = (
            features
        )

        if amount <= 0 or duration <= 0:
            raise ValueError(
                f"Invalid amount or duration at CSV row {row_number}"
            )

        if debit_time < 0 or credit_time < 0:
            raise ValueError(
                f"Negative processing time at CSV row {row_number}"
            )

        if last_10min < 1 or last_1h < last_10min:
            raise ValueError(
                f"Invalid transaction frequency at CSV row {row_number}"
            )

        if last_10min % 1 != 0 or last_1h % 1 != 0:
            raise ValueError(
                f"Transaction frequencies must be integers at CSV row {row_number}"
            )

        if duration < debit_time + credit_time:
            raise ValueError(
                f"Duration is inconsistent with processing times at CSV row {row_number}"
            )

        actual_counts[(split, label)] += 1
        partition_rows[split].append((values, label))

    expected_counts = _expected_split_label_counts()

    if actual_counts != Counter(expected_counts):
        raise ValueError(
            "Dataset label distribution does not match the expected "
            "stratified train/validation/test distribution"
        )

    partitions: dict[str, DatasetPartition] = {}

    for split_name, split_rows in partition_rows.items():
        features = np.asarray(
            [row[0] for row in split_rows],
            dtype=np.float64,
        )

        labels = np.asarray(
            [row[1] for row in split_rows],
            dtype=np.str_,
        )

        partitions[split_name] = DatasetPartition(
            features=features,
            labels=labels,
        )

    return partitions


def _binary_metrics(
    y_true: NDArray[np.bool_],
    y_pred: NDArray[np.bool_],
) -> dict[str, object]:
    true_positive = int(np.sum(y_true & y_pred))
    true_negative = int(np.sum(~y_true & ~y_pred))
    false_positive = int(np.sum(~y_true & y_pred))
    false_negative = int(np.sum(y_true & ~y_pred))

    precision_denominator = true_positive + false_positive
    recall_denominator = true_positive + false_negative
    fpr_denominator = false_positive + true_negative

    precision = (
        true_positive / precision_denominator
        if precision_denominator
        else 0.0
    )

    recall = (
        true_positive / recall_denominator
        if recall_denominator
        else 0.0
    )

    fpr = (
        false_positive / fpr_denominator
        if fpr_denominator
        else 0.0
    )

    f1 = (
        2 * precision * recall / (precision + recall)
        if precision + recall
        else 0.0
    )

    return {
        "sample_count": int(len(y_true)),
        "normal_count": int(np.sum(~y_true)),
        "anomaly_count": int(np.sum(y_true)),
        "precision": round(precision, 6),
        "recall": round(recall, 6),
        "f1": round(f1, 6),
        "false_positive_rate": round(fpr, 6),
        "confusion_matrix": {
            "labels": ["NORMAL", "ANOMALY"],
            "matrix": [
                [true_negative, false_positive],
                [false_negative, true_positive],
            ],
            "true_negative": true_negative,
            "false_positive": false_positive,
            "false_negative": false_negative,
            "true_positive": true_positive,
        },
    }


def select_anomaly_threshold(
    y_true: NDArray[np.bool_],
    anomaly_scores: NDArray[np.float64],
) -> tuple[float, dict[str, object]]:
    if len(y_true) != len(anomaly_scores):
        raise ValueError("Labels and scores must have the same length")

    if len(y_true) == 0:
        raise ValueError("Validation data cannot be empty")

    if not np.isfinite(anomaly_scores).all():
        raise ValueError("Anomaly scores must be finite")

    if not np.any(y_true) or np.all(y_true):
        raise ValueError(
            "Validation data must contain both normal and anomalous samples"
        )

    best_threshold: float | None = None
    best_metrics: dict[str, object] | None = None
    best_key: tuple[float, float, float] | None = None

    for threshold in np.unique(anomaly_scores):
        predictions = anomaly_scores >= threshold
        metrics = _binary_metrics(y_true, predictions)

        f1 = float(metrics["f1"])
        precision = float(metrics["precision"])

        # Primary objective: highest F1.
        # Tie-breakers: higher precision, then higher threshold.
        candidate_key = (
            f1,
            precision,
            float(threshold),
        )

        if best_key is None or candidate_key > best_key:
            best_key = candidate_key
            best_threshold = float(threshold)
            best_metrics = metrics

    if (
        best_threshold is None
        or best_metrics is None
        or float(best_metrics["f1"]) <= 0
    ):
        raise RuntimeError(
            "Could not find a useful anomaly threshold on validation data"
        )

    return best_threshold, best_metrics


def evaluate_test_partition(
    labels: NDArray[np.str_],
    anomaly_scores: NDArray[np.float64],
    threshold: float,
) -> dict[str, object]:
    y_true = labels != "NORMAL"
    y_pred = anomaly_scores >= threshold

    metrics = _binary_metrics(y_true, y_pred)

    by_anomaly_type: dict[str, object] = {}

    for anomaly_label in ANOMALY_LABELS:
        mask = labels == anomaly_label

        sample_count = int(np.sum(mask))
        detected_count = int(np.sum(y_pred & mask))

        recall = (
            detected_count / sample_count
            if sample_count
            else 0.0
        )

        by_anomaly_type[anomaly_label] = {
            "sample_count": sample_count,
            "detected_count": detected_count,
            "recall": round(recall, 6),
        }

    metrics["by_anomaly_type"] = by_anomaly_type
    metrics["anomaly_score_threshold"] = threshold

    return metrics


def train_and_evaluate(
    dataset_path: Path,
    model_path: Path,
    report_path: Path,
    model_version: str = "iforest-synthetic-v2",
    random_seed: int = 42,
    n_estimators: int = 300,
) -> dict[str, object]:
    partitions = load_dataset(dataset_path)

    train_partition = partitions["train"]
    validation_partition = partitions["validation"]
    test_partition = partitions["test"]

    # Only NORMAL records from the training split are used for fitting.
    normal_training_mask = (
        train_partition.labels == "NORMAL"
    )

    training_features = train_partition.features[
        normal_training_mask
    ]

    if len(training_features) != 11_400:
        raise ValueError(
            "Expected exactly 11,400 normal training samples"
        )

    pipeline = build_pipeline(
        training_features,
        random_seed=random_seed,
        n_estimators=n_estimators,
    )

    validation_scores = decision_scores_to_anomaly_scores(
        pipeline.decision_function(
            validation_partition.features
        )
    )

    validation_labels = (
        validation_partition.labels != "NORMAL"
    )

    threshold, validation_metrics = select_anomaly_threshold(
        validation_labels,
        validation_scores,
    )

    # The test partition is evaluated only after the threshold is fixed.
    test_scores = decision_scores_to_anomaly_scores(
        pipeline.decision_function(
            test_partition.features
        )
    )

    test_metrics = evaluate_test_partition(
        test_partition.labels,
        test_scores,
        threshold,
    )

    bundle = {
        "pipeline": pipeline,
        "model_version": model_version,
        "feature_names": FEATURE_NAMES,
        "anomaly_score_threshold": threshold,
        "training_metadata": {
            "training_split": "train",
            "training_samples": int(len(training_features)),
            "training_anomalies_excluded": int(
                len(train_partition.labels) - len(training_features)
            ),
            "labels_used_as_training_targets": False,
            "n_estimators": n_estimators,
            "contamination": "auto",
            "random_seed": random_seed,
            "threshold_selection": "validation_max_f1",
        },
    }

    report: dict[str, object] = {
        "model_version": model_version,
        "features": list(FEATURE_NAMES),
        "training": bundle["training_metadata"],
        "threshold_selection": {
            "method": "maximum_validation_f1",
            "tie_breaker": [
                "higher_precision",
                "higher_threshold",
            ],
            "anomaly_score_threshold": threshold,
            "validation_metrics": validation_metrics,
        },
        "test_metrics": test_metrics,
        "score_interpretation": (
            "Anomaly scores are monotonic signals in [0, 1], "
            "not calibrated probabilities."
        ),
    }

    save_model(model_path, bundle)

    report_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    report_path.write_text(
        json.dumps(
            report,
            indent=2,
            ensure_ascii=False,
            allow_nan=False,
        ),
        encoding="utf-8",
    )

    return report