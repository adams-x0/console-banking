package com.bank.support;

import com.bank.entity.BankUserEntity;

public class BankTestData {
    public static final long USER_ID = 10L;
    public static final String USERNAME = "testuser";
    public static final String UNKNOWN_USERNAME = "missinguser";
    public static final String FIRST_NAME = "Test";
    public static final String LAST_NAME = "customer";
    public static final String EMAIL = "test@example.com";
    public static final String ROLE = "CUSTOMER";
    public static final String NEW_USERNAME = "newcustomer";

    private BankTestData() {
    }

    public static char[] validPassword() {
        // Synthetic test data. Return a fresh array because callers clear it
        return "LoginTestPassword123!".toCharArray();
    }

    public static char[] incorrectPassword() {
        // Synthetic test data. Return a fresh array because callers clear it
        return "IncorrectPassword123!".toCharArray();
    }

    public static BankUserEntity customer(String passwordHash) {
        // Each test receives its own mutable entity
        BankUserEntity user = new BankUserEntity(
                USERNAME,
                passwordHash,
                FIRST_NAME,
                LAST_NAME,
                EMAIL
        );

        user.setUserId(USER_ID);
        return user;
    }

}
