package com.mehmetserin.banking.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends BankingException {

    public InsufficientFundsException(String accountNumber) {
        super("Account " + accountNumber + " has insufficient funds for this transfer");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    @Override
    public String code() {
        return "INSUFFICIENT_FUNDS";
    }
}
