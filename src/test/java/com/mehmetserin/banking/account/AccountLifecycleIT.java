package com.mehmetserin.banking.account;

import com.mehmetserin.banking.account.dto.AccountResponse;
import com.mehmetserin.banking.account.dto.CreateAccountRequest;
import com.mehmetserin.banking.auth.dto.AuthResponse;
import com.mehmetserin.banking.auth.dto.RegisterRequest;
import com.mehmetserin.banking.common.exception.ApiError;
import com.mehmetserin.banking.support.PostgresIntegrationSupport;
import com.mehmetserin.banking.transfer.dto.TransferRequest;
import com.mehmetserin.banking.transfer.dto.TransferResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AccountLifecycleIT extends PostgresIntegrationSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void freezeBlocksTransferAndCloseRequiresZeroBalance() {
        String token = registerAndGetToken("life-" + java.util.UUID.randomUUID().toString().substring(0, 8));

        AccountResponse funded = createAccount(token, "USD", "100.00");
        AccountResponse empty = createAccount(token, "USD", "0.00");

        ResponseEntity<AccountResponse> frozen = restTemplate.exchange(
                "/api/accounts/" + funded.id() + "/freeze", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), AccountResponse.class);
        assertThat(frozen.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(frozen.getBody().status()).isEqualTo("FROZEN");

        TransferRequest blocked = new TransferRequest(funded.id(), empty.id(), new BigDecimal("10.00"), "USD");
        ResponseEntity<ApiError> transferWhileFrozen = postTransfer(token, "freeze-block-1", blocked, ApiError.class);
        assertThat(transferWhileFrozen.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transferWhileFrozen.getBody().code()).isEqualTo("INVALID_TRANSFER");

        ResponseEntity<ApiError> closeWithBalance = restTemplate.exchange(
                "/api/accounts/" + funded.id() + "/close", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), ApiError.class);
        assertThat(closeWithBalance.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(closeWithBalance.getBody().code()).isEqualTo("INVALID_ACCOUNT_STATE");

        ResponseEntity<AccountResponse> unfrozen = restTemplate.exchange(
                "/api/accounts/" + funded.id() + "/unfreeze", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), AccountResponse.class);
        assertThat(unfrozen.getBody().status()).isEqualTo("ACTIVE");

        ResponseEntity<TransferResponse> drain = postTransfer(token, "drain-to-zero",
                new TransferRequest(funded.id(), empty.id(), new BigDecimal("100.00"), "USD"), TransferResponse.class);
        assertThat(drain.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<AccountResponse> closed = restTemplate.exchange(
                "/api/accounts/" + funded.id() + "/close", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), AccountResponse.class);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closed.getBody().status()).isEqualTo("CLOSED");
        assertThat(closed.getBody().balance()).isEqualByComparingTo("0.00");
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

    private <T> ResponseEntity<T> postTransfer(String token, String idempotencyKey, TransferRequest request, Class<T> type) {
        HttpHeaders headers = authHeaders(token);
        headers.add("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange("/api/transfers", HttpMethod.POST, new HttpEntity<>(request, headers), type);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
