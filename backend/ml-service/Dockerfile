# syntax=docker/dockerfile:1.7

# ---------- Build stage ----------
FROM python:3.12-slim AS build

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    VIRTUAL_ENV=/opt/venv \
    PATH="/opt/venv/bin:$PATH"

WORKDIR /app

RUN python -m venv "$VIRTUAL_ENV"

COPY requirements.txt ./

RUN --mount=type=cache,id=money-transfer-pip,target=/root/.cache/pip,sharing=locked \
    pip install -r requirements.txt

COPY app ./app

# Generate deterministic synthetic data and train the model.
RUN python -m app.synthetic_data \
        --output /tmp/synthetic_transfers.csv \
    && python -m app.train_model \
        --dataset /tmp/synthetic_transfers.csv \
        --model-path /artifacts/isolation_forest.joblib \
        --report-path /artifacts/evaluation_report.json \
    && rm -f /tmp/synthetic_transfers.csv \
              /tmp/synthetic_transfers.metadata.json


# ---------- Runtime stage ----------
FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    VIRTUAL_ENV=/opt/venv \
    PATH="/opt/venv/bin:$PATH" \
    ML_MODEL_PATH=/app/model/isolation_forest.joblib

WORKDIR /app

RUN mkdir -p /app/model \
    && chown -R 10001:10001 /app

COPY --from=build /opt/venv /opt/venv

COPY --from=build --chown=10001:10001 \
    /app/app /app/app

COPY --from=build --chown=10001:10001 \
    /artifacts/isolation_forest.joblib \
    /app/model/isolation_forest.joblib

USER 10001:10001

EXPOSE 8090

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]