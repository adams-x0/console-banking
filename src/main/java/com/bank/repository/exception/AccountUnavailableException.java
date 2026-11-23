package com.bank.repository.exception;

public class AccountUnavailableException extends RuntimeException{
    public AccountUnavailableException() {
        super("Account not found or not accessible to the customer.");
    }
}
