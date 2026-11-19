from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from pathlib import Path
from typing import Any

import numpy as np


FEATURE_NAMES = (
    "amount",
    "transaction_duration_ms",
    "debit_processing_time",
    "credit_processing_time",
    "transactions_last_10min",
    "transactions_last_1h",
)

TOTAL_SAMPLE_COUNT = 20_000

LABEL_COUNTS = {
    "NORMAL": 19_000,
    "AMOUNT": 350,
    "FREQUENCY": 350,
    "LATENCY": 300,
}

SPLIT_RATIOS = {
    "train": 0.60,
    "validation": 0.20,
    "test": 0.20,
}

DEFAULT_OUTPUT_PATH = (
    Path(__file__).resolve().parents[1]
    / "data"
    / "synthetic_transfers.csv"
)


def _build_normal_rows(
    count: int,
    rng: np.random.Generator,
) -> list[dict[str, Any]]:
    amounts = np.clip(
        rng.lognormal(
            mean=np.log(250),
            sigma=0.9,
            size=count,
        ),
        1,
        20_000,
    )

    debit_times = np.clip(
        rng.lognormal(
            mean=np.log(350),
            sigma=0.65,
            size=count,
        ),
        1,
        20_000,
    ).round().astype(np.int64)

    credit_times = np.clip(
        rng.lognormal(
            mean=np.log(450),
            sigma=0.65,
            size=count,
        ),
        1,
        20_000,
    ).round().astype(np.int64)

    network_delays = np.clip(
        rng.lognormal(
            mean=np.log(300),
            sigma=0.7,
            size=count,
        ),
        1,
        60_000,
    ).round().astype(np.int64)

    transaction_durations = (
        debit_times + credit_times + network_delays
    )

    transactions_last_10min = (
        rng.poisson(lam=1.2, size=count) + 1
    )

    transactions_last_1h = (
        transactions_last_10min
        + rng.poisson(lam=3.0, size=count)
    )

    rows: list[dict[str, Any]] = []

    for index in range(count):
        rows.append({
            "amount": round(float(amounts[index]), 2),
            "transaction_duration_ms": int(
                transaction_durations[index]
            ),
            "debit_processing_time": int(debit_times[index]),
            "credit_processing_time": int(credit_times[index]),
            "transactions_last_10min": int(
                transactions_last_10min[index]
            ),
            "transactions_last_1h": int(
                transactions_last_1h[index]
            ),
        })

    return rows


def _build_anomaly_rows(
    anomaly_type: str,
    count: int,
    rng: np.random.Generator,
) -> list[dict[str, Any]]:
    rows = _build_normal_rows(count, rng)

    for row in rows:
        if anomaly_type == "AMOUNT":
            row["amount"] = round(
                float(
                    np.clip(
                        rng.lognormal(
                            mean=np.log(8_000),
                            sigma=0.55,
                        ),
                        4_000,
                        50_000,
                    )
                ),
                2,
            )

        elif anomaly_type == "FREQUENCY":
            last_10min = int(rng.integers(8, 31))
            last_1h = last_10min + int(rng.integers(10, 71))

            row["transactions_last_10min"] = last_10min
            row["transactions_last_1h"] = last_1h

        elif anomaly_type == "LATENCY":
            previous_network_delay = (
                row["transaction_duration_ms"]
                - row["debit_processing_time"]
                - row["credit_processing_time"]
            )

            if rng.integers(0, 2) == 0:
                row["debit_processing_time"] = int(
                    rng.integers(5_000, 30_001)
                )
            else:
                row["credit_processing_time"] = int(
                    rng.integers(5_000, 30_001)
                )

            row["transaction_duration_ms"] = (
                row["debit_processing_time"]
                + row["credit_processing_time"]
                + previous_network_delay
            )

        else:
            raise ValueError(
                f"Unsupported anomaly type: {anomaly_type}"
            )

    return rows


def _assign_splits(
    rows: list[dict[str, Any]],
    rng: np.random.Generator,
) -> list[dict[str, Any]]:
    indices = rng.permutation(len(rows))

    train_end = int(len(rows) * SPLIT_RATIOS["train"])
    validation_end = train_end + int(
        len(rows) * SPLIT_RATIOS["validation"]
    )

    split_indices = {
        "train": indices[:train_end],
        "validation": indices[train_end:validation_end],
        "test": indices[validation_end:],
    }

    result: list[dict[str, Any]] = []

    for split_name, selected_indices in split_indices.items():
        for index in selected_indices:
            row = dict(rows[int(index)])
            row["split"] = split_name
            result.append(row)

    return result


def generate_dataset(
    random_seed: int = 42,
) -> list[dict[str, Any]]:
    """Generate a deterministic 20,000-row synthetic dataset."""
    rng = np.random.default_rng(random_seed)

    dataset: list[dict[str, Any]] = []

    for label, count in LABEL_COUNTS.items():
        if label == "NORMAL":
            rows = _build_normal_rows(count, rng)
        else:
            rows = _build_anomaly_rows(label, count, rng)

        for row in rows:
            row["label"] = label

        dataset.extend(_assign_splits(rows, rng))

    rng.shuffle(dataset)

    if len(dataset) != TOTAL_SAMPLE_COUNT:
        raise RuntimeError("Generated dataset has an invalid size")

    return dataset


def save_dataset(
    dataset: list[dict[str, Any]],
    output_path: Path,
    random_seed: int,
) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = [
        *FEATURE_NAMES,
        "label",
        "split",
    ]

    with output_path.open(
        "w",
        encoding="utf-8",
        newline="",
    ) as csv_file:
        writer = csv.DictWriter(
            csv_file,
            fieldnames=fieldnames,
        )
        writer.writeheader()
        writer.writerows(dataset)

    label_counts = Counter(
        row["label"] for row in dataset
    )

    split_counts = Counter(
        row["split"] for row in dataset
    )

    manifest = {
        "random_seed": random_seed,
        "total_samples": len(dataset),
        "features": list(FEATURE_NAMES),
        "label_counts": dict(label_counts),
        "split_counts": dict(split_counts),
        "model_training_policy": (
            "Isolation Forest is fitted only on NORMAL records "
            "from the train split. Labels are not used as model targets."
        ),
    }

    metadata_path = output_path.with_suffix(".metadata.json")

    metadata_path.write_text(
        json.dumps(
            manifest,
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate the synthetic money transfer dataset."
    )

    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT_PATH,
    )

    parser.add_argument(
        "--seed",
        type=int,
        default=42,
    )

    args = parser.parse_args()

    dataset = generate_dataset(args.seed)

    save_dataset(
        dataset,
        args.output,
        args.seed,
    )

    print(f"Dataset saved to: {args.output}")
    print(f"Total records: {len(dataset)}")
    print(
        f"Metadata saved to: "
        f"{args.output.with_suffix('.metadata.json')}"
    )


if __name__ == "__main__":
    main()