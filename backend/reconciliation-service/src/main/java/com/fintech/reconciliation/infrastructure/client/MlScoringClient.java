package com.fintech.reconciliation.infrastructure.client;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fintech.reconciliation.domain.model.AnomalyFeatures;
import com.fintech.reconciliation.domain.model.AnomalyPrediction;
import com.fintech.reconciliation.domain.port.out.AnomalyScoringProvider;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
public class MlScoringClient implements AnomalyScoringProvider {

        private final RestClient restClient;

        public MlScoringClient(
                        RestClient.Builder builder,
                        @Value("${reconciliation.ml.base-url:http://localhost:8090}") String baseUrl) {

                // requestFactory ezilmediği için testlerdeki MockRestServiceServer sorunsuz
                // çalışır
                this.restClient = builder.clone()
                                .baseUrl(baseUrl)
                                .build();
        }

        @Override
        @CircuitBreaker(name = "mlService")
        public AnomalyPrediction score(AnomalyFeatures features) {
                ScoringRequest request = new ScoringRequest(
                                features.amount(),
                                features.transactionDurationMs(),
                                features.debitProcessingTime(),
                                features.creditProcessingTime(),
                                features.transactionsLast10min(),
                                features.transactionsLast1h());

                ScoringResponse response = restClient.post()
                                .uri("/score")
                                .body(request)
                                .retrieve()
                                .body(ScoringResponse.class);

                if (response == null) {
                        throw new IllegalStateException(
                                        "ML service returned an empty score response");
                }

                return new AnomalyPrediction(
                                response.anomalyScore(),
                                response.modelVersion(),
                                response.isAnomaly());
        }

        private record ScoringRequest(
                        BigDecimal amount,

                        @JsonProperty("transaction_duration_ms") long transactionDurationMs,

                        @JsonProperty("debit_processing_time") long debitProcessingTime,

                        @JsonProperty("credit_processing_time") long creditProcessingTime,

                        @JsonProperty("transactions_last_10min") int transactionsLast10min,

                        @JsonProperty("transactions_last_1h") int transactionsLast1h) {
        }

        private record ScoringResponse(
                        @JsonProperty("anomalyScore") double anomalyScore,

                        @JsonProperty("modelVersion") String modelVersion,

                        @JsonProperty("isAnomaly") boolean isAnomaly) {
        }
}