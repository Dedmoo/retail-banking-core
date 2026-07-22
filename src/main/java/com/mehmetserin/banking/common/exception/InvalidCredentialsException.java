package com.mehmetserin.banking.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends BankingException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNAUTHORIZED;
    }

    @Override
    public String code() {
        return "INVALID_CREDENTIALS";
    }
}
