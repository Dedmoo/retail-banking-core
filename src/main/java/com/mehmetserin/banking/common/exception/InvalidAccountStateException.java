package com.mehmetserin.banking.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidAccountStateException extends BankingException {

    public InvalidAccountStateException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String code() {
        return "INVALID_ACCOUNT_STATE";
    }
}
