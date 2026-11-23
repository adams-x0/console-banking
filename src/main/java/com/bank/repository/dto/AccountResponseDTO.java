package com.bank.repository.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// Account details returned to the controller, including optional review information.

public class AccountResponseDTO {
    private final long accountId;
    private final BigDecimal balance;
    private final String status;
    private final OffsetDateTime appliedAt;
    private  final String reviewerLabel;
    private final OffsetDateTime reviewedAt;

    public String getReviewerLabel() {
        return reviewerLabel;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public AccountResponseDTO(long accountId,
                              BigDecimal balance,
                              String status,
                              OffsetDateTime appliedAt) {
        // Delegate initialization, with no reviewer information supplied.
        this(
                accountId,
                balance,
                status,
                appliedAt,
                null,
                null);
    }

    public AccountResponseDTO(long accountId,
                              BigDecimal balance,
                              String status,
                              OffsetDateTime appliedAt,
                              String reviewerLabel,
                              OffsetDateTime reviewedAt) {
        this.accountId = accountId;
        this.balance = balance;
        this.status = status;
        this.appliedAt = appliedAt;
        this.reviewerLabel = reviewerLabel;
        this.reviewedAt = reviewedAt;
    }

    public long getAccountId() {
        return accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public OffsetDateTime getAppliedAt() {
        return appliedAt;
    }

    public String getStatus() {
        return status;
    }
}
