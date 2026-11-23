package com.bank.repository.dto;

// Contains only the information needed to confirm a transfer destination.
public class TransferRecipientDTO {

    private final long accountId;
    private final String ownerLabel;

    public TransferRecipientDTO(long accountId, String ownerLabel) {
        this.accountId = accountId;
        this.ownerLabel = ownerLabel;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getOwnerLabel() {
        return ownerLabel;
    }
}