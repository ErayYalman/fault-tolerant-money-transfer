import numpy as np
import pytest

from app.training import select_anomaly_threshold


def test_threshold_selection_maximizes_validation_f1():
    labels = np.asarray(
        [False, False, True, True],
        dtype=np.bool_,
    )

    scores = np.asarray(
        [0.1, 0.4, 0.6, 0.9],
        dtype=np.float64,
    )

    threshold, metrics = select_anomaly_threshold(
        labels,
        scores,
    )

    assert threshold == pytest.approx(0.6)
    assert metrics["precision"] == 1.0
    assert metrics["recall"] == 1.0
    assert metrics["f1"] == 1.0
    assert metrics["false_positive_rate"] == 0.0


def test_threshold_selection_rejects_validation_without_anomalies():
    labels = np.asarray(
        [False, False, False],
        dtype=np.bool_,
    )

    scores = np.asarray(
        [0.1, 0.2, 0.3],
        dtype=np.float64,
    )

    with pytest.raises(ValueError, match="both normal and anomalous"):
        select_anomaly_threshold(labels, scores)


def test_threshold_selection_rejects_validation_without_normal_samples():
    labels = np.asarray(
        [True, True, True],
        dtype=np.bool_,
    )

    scores = np.asarray(
        [0.7, 0.8, 0.9],
        dtype=np.float64,
    )

    with pytest.raises(ValueError, match="both normal and anomalous"):
        select_anomaly_threshold(labels, scores)