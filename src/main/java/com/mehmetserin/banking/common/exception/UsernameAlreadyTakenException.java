package com.mehmetserin.banking.common.exception;

import org.springframework.http.HttpStatus;

public class UsernameAlreadyTakenException extends BankingException {

    public UsernameAlreadyTakenException(String usernameOrEmail) {
        super("Username or email already registered: " + usernameOrEmail);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String code() {
        return "USERNAME_TAKEN";
    }
}
