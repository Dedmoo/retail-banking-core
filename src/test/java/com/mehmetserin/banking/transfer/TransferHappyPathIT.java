package com.mehmetserin.banking.transfer;

import com.mehmetserin.banking.account.dto.AccountResponse;
import com.mehmetserin.banking.account.dto.CreateAccountRequest;
import com.mehmetserin.banking.auth.dto.AuthResponse;
import com.mehmetserin.banking.auth.dto.RegisterRequest;
import com.mehmetserin.banking.support.PostgresIntegrationSupport;
import com.mehmetserin.banking.transfer.dto.TransferRequest;
import com.mehmetserin.banking.transfer.dto.TransferResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransferHappyPathIT extends PostgresIntegrationSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void transferMovesMoneyBetweenTwoOwnAccountsAndIsIdempotentOnRetry() {
        String token = registerAndGetToken("carol");

        AccountResponse source = createAccount(token, "USD", "500.00");
        AccountResponse destination = createAccount(token, "USD", "0.00");

        TransferRequest transferRequest = new TransferRequest(source.id(), destination.id(),
                new BigDecimal("150.00"), "USD");

        ResponseEntity<TransferResponse> firstAttempt = postTransfer(token, "happy-path-key", transferRequest);
        assertThat(firstAttempt.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstAttempt.getBody().amount()).isEqualByComparingTo("150.00");

        assertThat(getAccount(token, source.id()).balance()).isEqualByComparingTo("350.00");
        assertThat(getAccount(token, destination.id()).balance()).isEqualByComparingTo("150.00");

        ResponseEntity<TransferResponse> retryWithSameKey = postTransfer(token, "happy-path-key", transferRequest);
        assertThat(retryWithSameKey.getBody().id()).isEqualTo(firstAttempt.getBody().id());

        assertThat(getAccount(token, source.id()).balance()).isEqualByComparingTo("350.00");
        assertThat(getAccount(token, destination.id()).balance()).isEqualByComparingTo("150.00");
    }

    private String registerAndGetToken(String username) {
        RegisterRequest request = new RegisterRequest(username, username + "@example.com", "correct-horse-battery");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().accessToken();
    }

    private AccountResponse createAccount(String token, String currency, String openingBalance) {
        CreateAccountRequest request = new CreateAccountRequest(currency, new BigDecimal(openingBalance));
        ResponseEntity<AccountResponse> response = restTemplate.exchange("/api/accounts", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(token)), AccountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private AccountResponse getAccount(String token, java.util.UUID accountId) {
        ResponseEntity<AccountResponse> response = restTemplate.exchange("/api/accounts/" + accountId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), AccountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<TransferResponse> postTransfer(String token, String idempotencyKey, TransferRequest request) {
        HttpHeaders headers = authHeaders(token);
        headers.add("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange("/api/transfers", HttpMethod.POST, new HttpEntity<>(request, headers), TransferResponse.class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
