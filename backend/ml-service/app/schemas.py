from pydantic import BaseModel, ConfigDict, Field, model_validator


class AnomalyScoringRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    amount: float = Field(gt=0)

    transaction_duration_ms: float = Field(gt=0)
    debit_processing_time: float = Field(ge=0)
    credit_processing_time: float = Field(ge=0)

    transactions_last_10min: int = Field(ge=1)
    transactions_last_1h: int = Field(ge=1)

    @model_validator(mode="after")
    def validate_frequency_window(self):
        if self.transactions_last_1h < self.transactions_last_10min:
            raise ValueError(
                "transactions_last_1h must be >= transactions_last_10min"
            )
        return self


class AnomalyScoringResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    anomaly_score: float = Field(
        serialization_alias="anomalyScore",
        ge=0,
        le=1,
    )
    model_version: str = Field(
        serialization_alias="modelVersion",
        min_length=1,
        max_length=100,
    )
    is_anomaly: bool = Field(
        serialization_alias="isAnomaly",
    )


class TrainingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    samples: list[AnomalyScoringRequest] = Field(
        max_length=100_000,
    )
    model_version: str = Field(
        default="iforest-experiment",
        alias="modelVersion", 
        min_length=1,
        max_length=100,
    )