package com.bank.repository.exception;

// Signals that the DAO could not create another pending application.
public class PendingApplicationException extends RuntimeException {
    public PendingApplicationException() {
        super("A pending application already exists.");
    }
}
