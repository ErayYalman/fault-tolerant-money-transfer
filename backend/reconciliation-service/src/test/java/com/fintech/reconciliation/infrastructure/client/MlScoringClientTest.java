package com.fintech.reconciliation.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.fintech.reconciliation.domain.model.AnomalyFeatures;
import com.fintech.reconciliation.domain.model.AnomalyPrediction;

class MlScoringClientTest {

        private static final AnomalyFeatures FEATURES = new AnomalyFeatures(
                        new BigDecimal("250.00"),
                        500,
                        80,
                        390,
                        2,
                        3);

        @Test
        void shouldSendExpectedRequestAndMapScoringResponse() {
                RestClient.Builder builder = RestClient.builder();
                MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

                // Prod kodundaki constructor'ı mock builder ve test URL'i ile çağırıyoruz
                MlScoringClient client = new MlScoringClient(builder, "http://ml.test");

                server.expect(requestTo("http://ml.test/score"))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(content().json("""
                                                {
                                                  "amount": 250.00,
                                                  "transaction_duration_ms": 500,
                                                  "debit_processing_time": 80,
                                                  "credit_processing_time": 390,
                                                  "transactions_last_10min": 2,
                                                  "transactions_last_1h": 3
                                                }
                                                """))
                                .andRespond(withSuccess("""
                                                {
                                                  "anomalyScore": 0.91,
                                                  "modelVersion": "iforest-test-v1",
                                                  "isAnomaly": true
                                                }
                                                """, MediaType.APPLICATION_JSON));

                AnomalyPrediction prediction = client.score(FEATURES);

                assertThat(prediction.anomalyScore()).isEqualTo(0.91);
                assertThat(prediction.modelVersion()).isEqualTo("iforest-test-v1");
                assertThat(prediction.isAnomaly()).isTrue();

                server.verify();
        }

        @Test
        void shouldPropagateServiceUnavailableWhenMlServiceReturns503() {
                RestClient.Builder builder = RestClient.builder();
                MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

                MlScoringClient client = new MlScoringClient(builder, "http://ml.test");

                server.expect(requestTo("http://ml.test/score"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

                assertThatThrownBy(() -> client.score(FEATURES))
                                .isInstanceOf(
                                                HttpServerErrorException.ServiceUnavailable.class);

                server.verify();
        }

        @Test
        void shouldRejectEmptyScoringResponse() {
                RestClient.Builder builder = RestClient.builder();
                MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

                MlScoringClient client = new MlScoringClient(builder, "http://ml.test");

                server.expect(requestTo("http://ml.test/score"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

                assertThatThrownBy(() -> client.score(FEATURES))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessage("ML service returned an empty score response");

                server.verify();
        }
}