package com.mehmetserin.banking.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransferException extends BankingException {

    public InvalidTransferException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String code() {
        return "INVALID_TRANSFER";
    }
}
