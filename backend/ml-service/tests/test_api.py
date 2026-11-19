import time
from pathlib import Path
from types import SimpleNamespace

import jwt
import numpy as np
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient

from app import main, security
from app.modeling import (
    FEATURE_NAMES,
    build_pipeline,
    save_model,
)


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch, tmp_path: Path):
    rng = np.random.default_rng(42)
    sample_count = 500

    amounts = rng.lognormal(
        mean=np.log(250),
        sigma=0.9,
        size=sample_count,
    )

    debit_times = rng.lognormal(
        mean=np.log(350),
        sigma=0.65,
        size=sample_count,
    )

    credit_times = rng.lognormal(
        mean=np.log(450),
        sigma=0.65,
        size=sample_count,
    )

    network_delays = rng.lognormal(
        mean=np.log(300),
        sigma=0.7,
        size=sample_count,
    )

    transactions_last_10min = (
        rng.poisson(lam=1.2, size=sample_count) + 1
    )

    transactions_last_1h = (
        transactions_last_10min
        + rng.poisson(lam=3.0, size=sample_count)
    )

    features = np.column_stack(
        [
            amounts,
            debit_times + credit_times + network_delays,
            debit_times,
            credit_times,
            transactions_last_10min,
            transactions_last_1h,
        ]
    ).astype(np.float64)

    pipeline = build_pipeline(
        features,
        n_estimators=50,
    )

    bundle = {
        "pipeline": pipeline,
        "model_version": "iforest-test-v1",
        "feature_names": FEATURE_NAMES,
        "anomaly_score_threshold": 0.5,
    }

    model_path = tmp_path / "isolation_forest.joblib"
    save_model(model_path, bundle)

    monkeypatch.setattr(main, "MODEL_PATH", model_path)

    with TestClient(main.app) as test_client:
        yield test_client


def test_health_returns_loaded_model_version(client: TestClient):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.json()["model_version"] == "iforest-test-v1"


def test_score_returns_expected_contract(
    client: TestClient,
    token_factory,
):
    payload = {
        "amount": 250.00,
        "transaction_duration_ms": 1500,
        "debit_processing_time": 350,
        "credit_processing_time": 450,
        "transactions_last_10min": 2,
        "transactions_last_1h": 5,
    }

    response = client.post(
        "/score",
        json=payload,
        headers={
            "Authorization": f"Bearer {token_factory()}",
        },
    )

    assert response.status_code == 200

    result = response.json()
    assert 0.0 <= result["anomalyScore"] <= 1.0
    assert result["modelVersion"] == "iforest-test-v1"
    assert isinstance(result["isAnomaly"], bool)

    assert result["isAnomaly"] == (
        result["anomalyScore"] >= 0.5
    )


def test_score_rejects_missing_features(
    client: TestClient,
    token_factory,
):
    response = client.post(
        "/score",
        json={
            "amount": 100,
            "transaction_duration_ms": 1000,
        },
        headers={
            "Authorization": f"Bearer {token_factory()}",
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

@pytest.fixture
def token_factory(monkeypatch: pytest.MonkeyPatch):
    private_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
    )

    trusted_signing_key = SimpleNamespace(
        key=private_key.public_key()
    )

    monkeypatch.setattr(
        security.jwks_client,
        "get_signing_key_from_jwt",
        lambda _token: trusted_signing_key,
    )

    def issue_token(
        claim_overrides: dict | None = None,
        signing_private_key=None,
    ) -> str:
        now = int(time.time())

        claims = {
            "iss": security.KEYCLOAK_ISSUER,
            "aud": [security.ML_AUDIENCE],
            "azp": security.AUTHORIZED_PARTY,
            "iat": now,
            "exp": now + 300,
            "resource_access": {
                "ml-service": {
                    "roles": ["anomaly-score"],
                }
            },
        }

        if claim_overrides:
            claims.update(claim_overrides)

        key = signing_private_key or private_key

        return jwt.encode(
            claims,
            key,
            algorithm="RS256",
            headers={"kid": "test-key"},
        )

    return issue_token

def test_score_requires_bearer_token(client: TestClient):
    response = client.post(
        "/score",
        json={"amount": 250.00},
    )

    assert response.status_code == 401


def test_score_rejects_wrong_audience(
    client: TestClient,
    token_factory,
):
    token = token_factory({
        "aud": ["another-service"],
    })

    response = client.post(
        "/score",
        json={"amount": 250.00},
        headers={"Authorization": f"Bearer {token}"},
    )

    assert response.status_code == 401


def test_score_rejects_wrong_authorized_party(
    client: TestClient,
    token_factory,
):
    token = token_factory({
        "azp": "another-client",
    })

    response = client.post(
        "/score",
        json={"amount": 250.00},
        headers={"Authorization": f"Bearer {token}"},
    )

    assert response.status_code == 403


def test_score_rejects_missing_client_role(
    client: TestClient,
    token_factory,
):
    token = token_factory({
        "resource_access": {
            "ml-service": {
                "roles": [],
            }
        }
    })

    response = client.post(
        "/score",
        json={"amount": 250.00},
        headers={"Authorization": f"Bearer {token}"},
    )

    assert response.status_code == 403


def test_score_rejects_invalid_signature(
    client: TestClient,
    token_factory,
):
    untrusted_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
    )

    token = token_factory(
        signing_private_key=untrusted_key,
    )

    response = client.post(
        "/score",
        json={"amount": 250.00},
        headers={"Authorization": f"Bearer {token}"},
    )

    assert response.status_code == 401