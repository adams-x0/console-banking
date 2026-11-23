package com.bank.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AccountEntity {

    private long accountId;
    private BigDecimal balance;
    private String status;
    private OffsetDateTime appliedAt;

    // Pending applications have no reviewer or reiew timestamp.
    private Long reviewedBy;
    private OffsetDateTime reviewedAt;

    public AccountEntity() {
        this.balance = BigDecimal.ZERO;
        this.status = "PENDING";
    }

    public AccountEntity(long accountId,
                         BigDecimal balance,
                         String status,
                         OffsetDateTime appliedAt,
                         Long reviewedBy,
                         OffsetDateTime reviewedAt) {
        this.accountId = accountId;
        this.balance = balance;
        this.status = status;
        this.appliedAt = appliedAt;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(OffsetDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
