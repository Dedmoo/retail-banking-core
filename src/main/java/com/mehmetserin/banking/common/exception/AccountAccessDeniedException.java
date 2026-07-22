package com.mehmetserin.banking.common.exception;

import org.springframework.http.HttpStatus;

public class AccountAccessDeniedException extends BankingException {

    public AccountAccessDeniedException() {
        super("Account does not belong to the authenticated user");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }

    @Override
    public String code() {
        return "ACCOUNT_ACCESS_DENIED";
    }
}
