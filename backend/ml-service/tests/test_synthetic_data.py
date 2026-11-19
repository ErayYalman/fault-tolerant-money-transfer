import math
from collections import Counter

import pytest

from app.synthetic_data import (
    FEATURE_NAMES,
    generate_dataset,
)


@pytest.fixture(scope="module")
def dataset():
    return generate_dataset(random_seed=42)


def test_dataset_contains_exactly_20000_records(dataset):
    assert len(dataset) == 20_000


def test_dataset_has_expected_label_distribution(dataset):
    label_counts = Counter(
        row["label"] for row in dataset
    )

    assert label_counts == {
        "NORMAL": 19_000,
        "AMOUNT": 350,
        "FREQUENCY": 350,
        "LATENCY": 300,
    }


def test_dataset_has_expected_split_distribution(dataset):
    split_counts = Counter(
        row["split"] for row in dataset
    )

    assert split_counts == {
        "train": 12_000,
        "validation": 4_000,
        "test": 4_000,
    }


def test_each_label_is_stratified_across_splits(dataset):
    expected = {
        "NORMAL": {
            "train": 11_400,
            "validation": 3_800,
            "test": 3_800,
        },
        "AMOUNT": {
            "train": 210,
            "validation": 70,
            "test": 70,
        },
        "FREQUENCY": {
            "train": 210,
            "validation": 70,
            "test": 70,
        },
        "LATENCY": {
            "train": 180,
            "validation": 60,
            "test": 60,
        },
    }

    actual = {}

    for label in expected:
        actual[label] = {}

        for split in ("train", "validation", "test"):
            actual[label][split] = sum(
                1
                for row in dataset
                if row["label"] == label
                and row["split"] == split
            )

    assert actual == expected


def test_every_record_has_the_expected_features(dataset):
    for row in dataset:
        for feature in FEATURE_NAMES:
            assert feature in row


def test_all_feature_values_are_finite(dataset):
    for row in dataset:
        for feature in FEATURE_NAMES:
            assert math.isfinite(float(row[feature]))


def test_feature_values_respect_scoring_contract(dataset):
    for row in dataset:
        assert row["amount"] > 0
        assert row["transaction_duration_ms"] > 0

        assert row["debit_processing_time"] >= 0
        assert row["credit_processing_time"] >= 0

        assert row["transactions_last_10min"] >= 1
        assert row["transactions_last_1h"] >= 1

        assert (
            row["transactions_last_1h"]
            >= row["transactions_last_10min"]
        )

        assert (
            row["transaction_duration_ms"]
            >= row["debit_processing_time"]
            + row["credit_processing_time"]
        )


def test_generation_is_reproducible():
    first = generate_dataset(random_seed=42)
    second = generate_dataset(random_seed=42)

    assert first == second


def test_different_seeds_produce_different_datasets():
    first = generate_dataset(random_seed=42)
    second = generate_dataset(random_seed=123)

    assert first != second