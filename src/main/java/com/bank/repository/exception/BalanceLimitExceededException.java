package com.bank.repository.exception;

public class BalanceLimitExceededException extends RuntimeException {
        public BalanceLimitExceededException() {
            super("The maximum account balance would be exceeded");
        }
}

