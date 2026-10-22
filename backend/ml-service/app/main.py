from __future__ import annotations

import os
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field

from app.modeling import (
    FEATURE_NAMES,
    build_pipeline,
    default_model_path,
    features_from_samples,
    load_model,
    save_model,
    score_sample,
)
from app.schemas import (
    AnomalyScoringRequest,
    AnomalyScoringResponse,
    TrainingRequest,
)


MODEL_PATH = default_model_path()
TRAINING_ENDPOINT_ENABLED = (
    os.getenv("ML_TRAINING_ENDPOINT_ENABLED", "false").lower()
    == "true"
)


class HealthResponse(BaseModel):
    status: str
    model_version: str


class TrainingResponse(BaseModel):
    status: str
    model_version: str = Field(serialization_alias="modelVersion")
    trained_samples: int = Field(serialization_alias="trainedSamples")


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.model_bundle = load_model(MODEL_PATH)
    yield


app = FastAPI(
    title="Money Transfer Anomaly Scoring API",
    version="1.0.0",
    lifespan=lifespan,
)


@app.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    bundle: dict[str, Any] = request.app.state.model_bundle

    return HealthResponse(
        status="UP",
        model_version=bundle["model_version"],
    )


@app.post("/score", response_model=AnomalyScoringResponse)
def score(
    payload: AnomalyScoringRequest,
    request: Request,
) -> AnomalyScoringResponse:
    bundle: dict[str, Any] = request.app.state.model_bundle

    try:
        anomaly_score, model_version, is_anomaly = score_sample(
            bundle,
            payload,
        )
    except (ValueError, ArithmeticError) as exception:
        raise HTTPException(
            status_code=422,
            detail="Could not score the supplied features",
        ) from exception

    return AnomalyScoringResponse(
        anomaly_score=anomaly_score,
        model_version=model_version,
        is_anomaly=is_anomaly,
    )


@app.post("/train", response_model=TrainingResponse)
def train(
    payload: TrainingRequest,
    request: Request,
) -> TrainingResponse:
    if not TRAINING_ENDPOINT_ENABLED:
        raise HTTPException(
            status_code=404,
            detail="The training endpoint is disabled",
        )

    try:
        features = features_from_samples(payload.samples)
        pipeline = build_pipeline(features)

        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        model_version = f"{payload.model_version}-{timestamp}"

        new_bundle = {
            "pipeline": pipeline,
            "model_version": model_version,
            "feature_names": FEATURE_NAMES,
        }

        # Keep the exact ordered schema expected by this scoring API.
        from app.modeling import FEATURE_NAMES

        new_bundle["feature_names"] = FEATURE_NAMES
        save_model(MODEL_PATH, new_bundle)

        # Replace the active model only after the new model is saved.
        request.app.state.model_bundle = load_model(MODEL_PATH)

    except (ValueError, OSError) as exception:
        raise HTTPException(
            status_code=422,
            detail="Training failed for the supplied examples",
        ) from exception

    return TrainingResponse(
        status="TRAINED",
        model_version=model_version,
        trained_samples=len(payload.samples),
    )