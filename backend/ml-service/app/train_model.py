from __future__ import annotations

import argparse
from pathlib import Path

from app.modeling import default_model_path
from app.synthetic_data import DEFAULT_OUTPUT_PATH
from app.training import train_and_evaluate


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Train and evaluate the Isolation Forest model."
    )

    parser.add_argument(
        "--dataset",
        type=Path,
        default=DEFAULT_OUTPUT_PATH,
    )

    parser.add_argument(
        "--model-path",
        type=Path,
        default=default_model_path(),
    )

    parser.add_argument(
        "--report-path",
        type=Path,
        default=None,
    )

    parser.add_argument(
        "--model-version",
        type=str,
        default="iforest-synthetic-v2",
    )

    parser.add_argument(
        "--seed",
        type=int,
        default=42,
    )

    parser.add_argument(
        "--n-estimators",
        type=int,
        default=300,
    )

    args = parser.parse_args()

    report_path = (
        args.report_path
        if args.report_path is not None
        else args.model_path.parent / "evaluation_report.json"
    )

    report = train_and_evaluate(
        dataset_path=args.dataset,
        model_path=args.model_path,
        report_path=report_path,
        model_version=args.model_version,
        random_seed=args.seed,
        n_estimators=args.n_estimators,
    )

    threshold = report["threshold_selection"][
        "anomaly_score_threshold"
    ]

    test_metrics = report["test_metrics"]

    print(f"Model saved to: {args.model_path}")
    print(f"Evaluation report saved to: {report_path}")
    print(f"Model version: {args.model_version}")
    print(f"Anomaly threshold: {threshold}")

    print("\nTest metrics:")
    for metric in (
        "precision",
        "recall",
        "f1",
        "false_positive_rate",
    ):
        print(f"{metric}: {test_metrics[metric]}")


if __name__ == "__main__":
    main()