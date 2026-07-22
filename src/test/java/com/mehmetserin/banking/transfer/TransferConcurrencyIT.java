package com.mehmetserin.banking.transfer;

import com.mehmetserin.banking.account.dto.AccountResponse;
import com.mehmetserin.banking.account.dto.CreateAccountRequest;
import com.mehmetserin.banking.auth.dto.AuthResponse;
import com.mehmetserin.banking.auth.dto.RegisterRequest;
import com.mehmetserin.banking.support.PostgresIntegrationSupport;
import com.mehmetserin.banking.transfer.dto.TransferRequest;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that two transfers racing to debit the same source account cannot
 * together overdraw it: the account row is locked with SELECT ... FOR UPDATE,
 * so the second transfer only proceeds after the first commits, and then sees
 * the reduced balance and correctly fails with insufficient funds.
 */
class TransferConcurrencyIT extends PostgresIntegrationSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void concurrentTransfersFromTheSameAccountNeverOverdrawIt() throws Exception {
        String token = registerAndGetToken("dave");

        AccountResponse source = createAccount(token, "USD", "100.00");
        AccountResponse destinationOne = createAccount(token, "USD", "0.00");
        AccountResponse destinationTwo = createAccount(token, "USD", "0.00");

        TransferRequest transferToOne = new TransferRequest(source.id(), destinationOne.id(), new BigDecimal("80.00"), "USD");
        TransferRequest transferToTwo = new TransferRequest(source.id(), destinationTwo.id(), new BigDecimal("80.00"), "USD");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Future<ResponseEntity<String>> first = executor.submit(
                () -> raceTransfer(token, "concurrent-key-1", transferToOne, bothReady, go));
        Future<ResponseEntity<String>> second = executor.submit(
                () -> raceTransfer(token, "concurrent-key-2", transferToTwo, bothReady, go));

        bothReady.await(5, TimeUnit.SECONDS);
        go.countDown();

        ResponseEntity<String> firstResult = first.get(20, TimeUnit.SECONDS);
        ResponseEntity<String> secondResult = second.get(20, TimeUnit.SECONDS);
        executor.shutdown();

        List<HttpStatus> statuses = List.of(
                HttpStatus.valueOf(firstResult.getStatusCode().value()),
                HttpStatus.valueOf(secondResult.getStatusCode().value()));

        long succeeded = statuses.stream().filter(status -> status == HttpStatus.CREATED).count();
        long rejected = statuses.stream().filter(status -> status == HttpStatus.UNPROCESSABLE_ENTITY).count();

        assertThat(succeeded).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(getAccount(token, source.id()).balance()).isEqualByComparingTo("20.00");
    }

    private ResponseEntity<String> raceTransfer(String token, String idempotencyKey, TransferRequest request,
                                                 CountDownLatch bothReady, CountDownLatch go) throws Exception {
        bothReady.countDown();
        go.await(5, TimeUnit.SECONDS);
        return postTransfer(token, idempotencyKey, request);
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

    private ResponseEntity<String> postTransfer(String token, String idempotencyKey, TransferRequest request) {
        HttpHeaders headers = authHeaders(token);
        headers.add("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange("/api/transfers", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
