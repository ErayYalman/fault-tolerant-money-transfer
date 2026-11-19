from __future__ import annotations

from contextlib import asynccontextmanager
from typing import Any

from fastapi import Depends, FastAPI, HTTPException, Request
from pydantic import BaseModel, Field

from app.modeling import (
    default_model_path,
    load_model,
    score_sample,
)
from app.schemas import (
    AnomalyScoringRequest,
    AnomalyScoringResponse,
)
from app.security import require_anomaly_scoring_access


MODEL_PATH = default_model_path()


class HealthResponse(BaseModel):
    status: str
    model_version: str


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
    _: dict[str, Any] = Depends(require_anomaly_scoring_access),
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


@app.post("/train")
def train():
    raise HTTPException(
        status_code=404,
        detail=(
            "Online model training is disabled. "
            "Use `python -m app.train_model` for offline training."
        ),
    )