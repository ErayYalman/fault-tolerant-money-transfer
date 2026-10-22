from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app import main
from app.modeling import create_model_bundle, save_model


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch, tmp_path: Path):
    model_path = tmp_path / "isolation_forest.joblib"
    save_model(model_path, create_model_bundle())

    monkeypatch.setattr(main, "MODEL_PATH", model_path)
    monkeypatch.setattr(main, "TRAINING_ENDPOINT_ENABLED", False)

    with TestClient(main.app) as test_client:
        yield test_client


def test_health_returns_loaded_model_version(client: TestClient):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.json()["model_version"] == "iforest-synthetic-v1"


def test_score_returns_expected_contract(client: TestClient):
    payload = {
        "amount": 250.00,
        "transaction_duration_ms": 1500,
        "debit_processing_time": 350,
        "credit_processing_time": 450,
        "transactions_last_10min": 2,
        "transactions_last_1h": 5,
    }

    response = client.post("/score", json=payload)

    assert response.status_code == 200

    result = response.json()
    assert 0.0 <= result["anomalyScore"] <= 1.0
    assert result["modelVersion"] == "iforest-synthetic-v1"
    assert isinstance(result["isAnomaly"], bool)


def test_score_rejects_missing_features(client: TestClient):
    response = client.post(
        "/score",
        json={
            "amount": 100,
            "transaction_duration_ms": 1000,
        },
    )

    assert response.status_code == 422


def test_training_endpoint_is_disabled_by_default(client: TestClient):
    sample = {
        "amount": 250.0,
        "transaction_duration_ms": 1500,
        "debit_processing_time": 350,
        "credit_processing_time": 450,
        "transactions_last_10min": 2,
        "transactions_last_1h": 5,
    }

    response = client.post(
        "/train",
        json={
            "model_version": "experiment",
            "samples": [sample] * 100,
        },
    )

    assert response.status_code == 404