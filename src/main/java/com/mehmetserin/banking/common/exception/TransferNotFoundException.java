package com.mehmetserin.banking.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TransferNotFoundException extends BankingException {

    public TransferNotFoundException(UUID transferId) {
        super("Transfer not found: " + transferId);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String code() {
        return "TRANSFER_NOT_FOUND";
    }
}
