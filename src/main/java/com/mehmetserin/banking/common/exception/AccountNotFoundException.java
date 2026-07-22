package com.mehmetserin.banking.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String code() {
        return "ACCOUNT_NOT_FOUND";
    }
}
