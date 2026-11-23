package com.bank.repository.exception;

public class DestinationAccountUnavailableException extends RuntimeException {
    public DestinationAccountUnavailableException() {
        super("The destination account does not exist.");
    }
}
