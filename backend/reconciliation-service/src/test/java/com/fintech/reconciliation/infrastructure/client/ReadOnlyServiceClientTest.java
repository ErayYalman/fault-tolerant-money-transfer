package com.fintech.reconciliation.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fintech.reconciliation.infrastructure.client.dto.LedgerEntrySnapshot;
import com.fintech.reconciliation.infrastructure.client.dto.TransferSnapshot;

class ReadOnlyServiceClientTest {

    private static final Instant FROM = Instant.parse("2026-10-20T00:00:00Z");

    private static final Instant TO = Instant.parse("2026-10-21T00:00:00Z");

    @Test
    void transferClientShouldFetchEveryPage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        TransferServiceClient client = new TransferServiceClient(builder, "http://transfer.test", 2);

        String firstPage = """
                [
                  {
                    "transferId": "11111111-1111-1111-1111-111111111111",
                    "correlationId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "amount": 10.00,
                    "currency": "TRY",
                    "status": "COMPLETED",
                    "startedAt": "2026-10-20T01:00:00Z",
                    "debitRequestedAt": "2026-10-20T01:00:01Z",
                    "debitCompletedAt": "2026-10-20T01:00:02Z",
                    "creditRequestedAt": "2026-10-20T01:00:03Z",
                    "creditCompletedAt": "2026-10-20T01:00:04Z",
                    "completedAt": "2026-10-20T01:00:04Z"
                  },
                  {
                    "transferId": "22222222-2222-2222-2222-222222222222",
                    "correlationId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "amount": 20.00,
                    "currency": "TRY",
                    "status": "COMPLETED",
                    "startedAt": "2026-10-20T02:00:00Z",
                    "debitRequestedAt": "2026-10-20T02:00:01Z",
                    "debitCompletedAt": "2026-10-20T02:00:02Z",
                    "creditRequestedAt": "2026-10-20T02:00:03Z",
                    "creditCompletedAt": "2026-10-20T02:00:04Z",
                    "completedAt": "2026-10-20T02:00:04Z"
                  }
                ]
                """;

        String secondPage = """
                [
                  {
                    "transferId": "33333333-3333-3333-3333-333333333333",
                    "correlationId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    "amount": 30.00,
                    "currency": "TRY",
                    "status": "DEBIT_FAILED",
                    "startedAt": "2026-10-20T03:00:00Z",
                    "debitRequestedAt": "2026-10-20T03:00:01Z",
                    "debitCompletedAt": null,
                    "creditRequestedAt": null,
                    "creditCompletedAt": null,
                    "completedAt": null
                  }
                ]
                """;

        server.expect(requestTo(startsWith(
                "http://transfer.test/internal/transfers")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("from", FROM.toString()))
                .andExpect(queryParam("to", TO.toString()))
                .andExpect(queryParam("page", "0"))
                .andExpect(queryParam("size", "2"))
                .andRespond(withSuccess(
                        firstPage, MediaType.APPLICATION_JSON));

        server.expect(requestTo(startsWith(
                "http://transfer.test/internal/transfers")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("page", "1"))
                .andExpect(queryParam("size", "2"))
                .andRespond(withSuccess(
                        secondPage, MediaType.APPLICATION_JSON));

        List<TransferSnapshot> transfers = client.findTransfers(FROM, TO);

        assertThat(transfers).hasSize(3);
        assertThat(transfers.get(0).amount())
                .isEqualByComparingTo("10.00");
        assertThat(transfers.get(2).status())
                .isEqualTo("DEBIT_FAILED");

        server.verify();
    }

    @Test
    void paymentAClientShouldPreserveDebitAndReversalEntries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        PaymentServiceAClient client = new PaymentServiceAClient(builder, "http://payment-a.test", 2);

        String responseBody = """
                [
                  {
                    "transferId": "11111111-1111-1111-1111-111111111111",
                    "correlationId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "accountId": "aaaaaaaa-0000-0000-0000-000000000001",
                    "amount": 10.00,
                    "balanceBefore": 100.00,
                    "balanceAfter": 90.00,
                    "entryType": "DEBIT",
                    "status": "COMPLETED",
                    "createdAt": "2026-10-20T01:00:00Z"
                  },
                  {
                    "transferId": "11111111-1111-1111-1111-111111111111",
                    "correlationId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "accountId": "aaaaaaaa-0000-0000-0000-000000000001",
                    "amount": 10.00,
                    "balanceBefore": 90.00,
                    "balanceAfter": 100.00,
                    "entryType": "REVERSAL",
                    "status": "COMPLETED",
                    "createdAt": "2026-10-20T01:01:00Z"
                  }
                ]
                """;

        server.expect(requestTo(startsWith(
                "http://payment-a.test/internal/ledger")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("from", FROM.toString()))
                .andExpect(queryParam("to", TO.toString()))
                .andExpect(queryParam("page", "0"))
                .andExpect(queryParam("size", "2"))
                .andRespond(withSuccess(
                        responseBody, MediaType.APPLICATION_JSON));

        // İlk sayfa tam dolduğu için sayfalama bir sonraki sayfayı ister.
        server.expect(requestTo(startsWith(
                "http://payment-a.test/internal/ledger")))
                .andExpect(queryParam("page", "1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<LedgerEntrySnapshot> entries = client.findLedgerEntries(FROM, TO);

        assertThat(entries).hasSize(2);
        assertThat(entries)
                .extracting(LedgerEntrySnapshot::entryType)
                .containsExactly("DEBIT", "REVERSAL");
        assertThat(entries.get(0).correlationId())
                .isEqualTo(entries.get(1).correlationId());

        server.verify();
    }

    @Test
    void paymentBClientShouldReadCreditEntries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        PaymentServiceBClient client = new PaymentServiceBClient(builder, "http://payment-b.test", 2);

        String responseBody = """
                [
                  {
                    "transferId": "11111111-1111-1111-1111-111111111111",
                    "correlationId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "accountId": "bbbbbbbb-0000-0000-0000-000000000001",
                    "amount": 10.00,
                    "balanceBefore": 50.00,
                    "balanceAfter": 60.00,
                    "entryType": "CREDIT",
                    "status": "COMPLETED",
                    "createdAt": "2026-10-20T01:00:04Z"
                  }
                ]
                """;

        server.expect(requestTo(startsWith(
                "http://payment-b.test/internal/ledger")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("from", FROM.toString()))
                .andExpect(queryParam("to", TO.toString()))
                .andExpect(queryParam("page", "0"))
                .andExpect(queryParam("size", "2"))
                .andRespond(withSuccess(
                        responseBody, MediaType.APPLICATION_JSON));

        List<LedgerEntrySnapshot> entries = client.findLedgerEntries(FROM, TO);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).entryType()).isEqualTo("CREDIT");
        assertThat(entries.get(0).balanceAfter())
                .isEqualByComparingTo("60.00");

        server.verify();
    }
}