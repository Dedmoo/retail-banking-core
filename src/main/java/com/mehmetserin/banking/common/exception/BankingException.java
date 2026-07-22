package com.mehmetserin.banking.common.exception;

import org.springframework.http.HttpStatus;

public abstract class BankingException extends RuntimeException {

    protected BankingException(String message) {
        super(message);
    }

    public abstract HttpStatus status();

    public abstract String code();
}
