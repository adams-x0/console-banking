package com.bank.repository.exception;

public class AccountNotApprovedException  extends  RuntimeException{
    public AccountNotApprovedException() {
        super("Account is not approved.");
    }
}
