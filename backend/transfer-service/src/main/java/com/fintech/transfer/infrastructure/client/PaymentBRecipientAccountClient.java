package com.fintech.transfer.infrastructure.client;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;
import static org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver.principal;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fintech.transfer.application.exception.RecipientLookupUnavailableException;
import com.fintech.transfer.domain.model.RecipientAccount;
import com.fintech.transfer.domain.port.out.RecipientAccountLookupPort;
import com.fintech.transfer.infrastructure.client.dto.PaymentBRecipientAccountResponse;

@Component
public class PaymentBRecipientAccountClient
        implements RecipientAccountLookupPort {

    private final RestClient restClient;

    public PaymentBRecipientAccountClient(
            RestClient.Builder builder,
            @Value("${transfer.clients.payment-b-base-url}") String baseUrl) {

        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public Optional<RecipientAccount> findByAccountNumber(
            String accountNumber) {

        try {
            PaymentBRecipientAccountResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(
                                    "internal",
                                    "recipient-accounts",
                                    accountNumber)
                            .build())
                    .attributes(clientRegistrationId("payment-b-lookup"))
                    .attributes(principal("transfer-service"))
                    .retrieve()
                    .body(PaymentBRecipientAccountResponse.class);

            if (response == null) {
                throw new RecipientLookupUnavailableException();
            }

            return Optional.of(response.toDomain());

        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();

        } catch (RestClientException | OAuth2AuthorizationException exception) {
            throw new RecipientLookupUnavailableException(exception);
        }
    }
}
