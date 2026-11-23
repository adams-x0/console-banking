package com.bank.service;

import com.bank.entity.AccountEntity;
import com.bank.entity.BankUserEntity;
import com.bank.repository.AccountDAO;
import com.bank.repository.UserDAO;
import com.bank.repository.dto.AccountResponseDTO;
import com.bank.repository.exception.AccountNotApprovedException;
import com.bank.repository.exception.AccountUnavailableException;
import com.bank.repository.exception.BalanceLimitExceededException;
import com.bank.repository.exception.PendingApplicationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.bank.repository.exception.InsufficientFundsException;
import com.bank.repository.exception.DestinationAccountUnavailableException;

import com.bank.repository.dto.TransferRecipientDTO;

public class AccountService {

    private final AccountDAO accountDAO;
    private final UserDAO userDAO;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    public AccountService(AccountDAO accountDAO, UserDAO userDAO) {
        this.accountDAO = accountDAO;
        this.userDAO = userDAO;
    }

    public AccountResponseDTO applyForAccount(long customerId)
            throws SQLException {
        if (customerId <= 0) {
            throw new IllegalArgumentException(
                    "Customer ID must be greater than zero."
            );
        }

//        Read the stored role rather than trusting a role supplied by the caller.
        BankUserEntity customer = userDAO.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found."
                ));

        if (!"CUSTOMER".equals(customer.getRole())) {
            throw new IllegalArgumentException(
                    "Only customers can apply for bank accounts."
            );
        }

//        This DAO creates the account and ownership together in one transaction.
        AccountEntity account;

        try {
            account = accountDAO.createApplication(customerId);
        } catch (PendingApplicationException e) {
            throw new IllegalArgumentException(
                    "You already have a pending account application. "
                            + "Please wait for an employee to review it.",

                    e
            );
        }

        return new AccountResponseDTO(
                account.getAccountId(),
                account.getBalance(),
                account.getStatus(),
                account.getAppliedAt()
        );

    }

    public List<AccountResponseDTO> getCustomerAccounts(long customerId)
            throws SQLException {

        if (customerId <= 0) {
            throw new IllegalArgumentException(
                    "Customer ID must be greater than zero."
            );
        }

        BankUserEntity customer = userDAO.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found."
                ));

        if (!"CUSTOMER".equals(customer.getRole())) {
            throw new IllegalArgumentException(
                    "Only customers can use this account view."
            );
        }

        List<AccountEntity> accounts = accountDAO.findByOwnerId(customerId);

        List<AccountResponseDTO> responses = new ArrayList<>();

//        Return display data rather than exposing database entities.
        for (AccountEntity account : accounts) {
            responses.add(toResponse(account));
        }

        return responses;
    }

    public List<AccountResponseDTO> getPendingApplications(long employeeId)
            throws SQLException {

        requireEmployee(employeeId);

        List<AccountEntity> applications = accountDAO.findPendingApplications();

        List<AccountResponseDTO> responses = new ArrayList<>();

        for (AccountEntity application : applications) {
            responses.add(new AccountResponseDTO(
                    application.getAccountId(),
                    application.getBalance(),
                    application.getStatus(),
                    application.getAppliedAt()
            ));
        }

        return responses;
    }

    public void reviewApplication(
            long employeeId,
            long accountId,
            boolean approved
    ) throws SQLException {

        requireEmployee(employeeId);

        if (accountId <= 0) {
            throw new IllegalArgumentException(
                    "Account ID must be greater than zero."
            );
        }

//        The DAO checks that the application is still pending during the update.
        boolean updated = accountDAO.reviewApplication(
                accountId,
                employeeId,
                approved
        );

        if (!updated) {
            throw new IllegalArgumentException(
                    "Application not found or already reviewed."
            );
        }
    }

    private void requireEmployee(long employeeId) throws SQLException {
        if (employeeId <= 0) {
            throw new IllegalArgumentException(
                    "Employee ID must be greater than zero."
            );
        }

        BankUserEntity employee = userDAO.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Employee not found."
                ));

//        Check the stored role before allowing employee operations.
        if (!"EMPLOYEE".equals(employee.getRole())) {
            throw new IllegalArgumentException(
                    "Only employees can review account applications."
            );
        }
    }

    public List<AccountResponseDTO> getCustomerAccountsForEmployee(
            long employeeId,
            long customerId
    ) throws SQLException {
//        Authorize the acting employee before looking up customer information.
        requireEmployee(employeeId);

        return getCustomerAccounts(customerId);
    }

    private AccountResponseDTO toResponse(AccountEntity account)
            throws SQLException {
        String reviewerLabel = null;

//        Pending applications have no reviewer, so skip the user lookup.
        if (account.getReviewedBy() != null) {
            BankUserEntity reviewer = userDAO
                    .findById(account.getReviewedBy())
                    .orElseThrow(() -> new SQLException(
                            "The account reviewer could not be found."
                    ));

            reviewerLabel =
                    reviewer.getFirstName() + " "
                            + reviewer.getLastName()
                            + " (@" + reviewer.getUsername() + ")";
        }

        return new AccountResponseDTO(
                account.getAccountId(),
                account.getBalance(),
                account.getStatus(),
                account.getAppliedAt(),
                reviewerLabel,
                account.getReviewedAt()
        );
    }

    public BigDecimal deposit(
            long customerId,
            long accountId,
            BigDecimal amount
    ) throws SQLException {
        if (customerId <= 0) {
            throw new IllegalArgumentException(
                    "Customer ID must be greater than zero."
            );
        }

        if (accountId <= 0) {
            throw new IllegalArgumentException(
                    "Account ID must be greater than zero."
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Deposit amount must be greater than zero."
            );
        }

        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(
                    "Deposit amount exceeds the supported limit."
            );
        }

        BigDecimal depositAmount;

        try {
            depositAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Deposit amount must not contain fractions of a cent.",
                    e
            );
        }

        BankUserEntity customer = userDAO.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found."
                ));

        if (!"CUSTOMER".equals(customer.getRole())) {
            throw new IllegalArgumentException(
                    "Only customers can deposit into their accounts."
            );
        }

        try {
            return accountDAO.deposit(accountId, customerId, depositAmount);
        } catch (BalanceLimitExceededException e) {
            throw new IllegalArgumentException(
                    "This deposit would exceed the maximum account balance.",
                    e
            );
        } catch (AccountUnavailableException e) {
            throw new IllegalArgumentException(
                    "The selected account is unavailable or does not belong to you.",
                    e
            );
        } catch (AccountNotApprovedException e) {
            throw new IllegalArgumentException(
                    "You can deposit only into an approved account.",
                    e
            );
        }
    }

    public BigDecimal withdraw(
            long customerId,
            long accountId,
            BigDecimal amount
    ) throws SQLException {
        if (accountId <= 0) {
            throw new IllegalArgumentException(
                    "Account ID must be greater than zero."
            );
        }

        BigDecimal withdrawalAmount = validateMoneyAmount(
                amount,
                "Withdrawal"
        );

        requireCustomerForMoneyOperation(customerId);

        try {
            return accountDAO.withdraw(
                    accountId,
                    customerId,
                    withdrawalAmount
            );
        } catch (AccountUnavailableException e) {
            throw new IllegalArgumentException(
                    "The selected account is unavailable or does not belong to you.",
                    e
            );
        } catch (AccountNotApprovedException e) {
            throw new IllegalArgumentException(
                    "You can withdraw only from an approved account.",
                    e
            );
        } catch (InsufficientFundsException e) {
            throw new IllegalArgumentException(
                    "Your account does not have enough money for this withdrawal.",
                    e
            );
        }
    }

    public BigDecimal transfer(
            long customerId,
            long sourceAccountId,
            long destinationAccountId,
            BigDecimal amount
    ) throws SQLException {
        if (sourceAccountId <= 0 || destinationAccountId <= 0) {
            throw new IllegalArgumentException(
                    "Source and destination account IDs must be greater than zero."
            );
        }

        if (sourceAccountId == destinationAccountId) {
            throw new IllegalArgumentException(
                    "Choose a different destination account."
            );
        }

        BigDecimal transferAmount = validateMoneyAmount(
                amount,
                "Transfer"
        );

        requireCustomerForMoneyOperation(customerId);

        try {
            return accountDAO.transfer(
                    sourceAccountId,
                    destinationAccountId,
                    customerId,
                    transferAmount
            );
        } catch (AccountUnavailableException e) {
            throw new IllegalArgumentException(
                    "The source account is unavailable or does not belong to you.",
                    e
            );
        } catch (DestinationAccountUnavailableException e) {
            throw new IllegalArgumentException(
                    "The destination account could not be found.",
                    e
            );
        } catch (AccountNotApprovedException e) {
            throw new IllegalArgumentException(
                    "Both accounts must be approved before you can transfer money.",
                    e
            );
        } catch (InsufficientFundsException e) {
            throw new IllegalArgumentException(
                    "Your source account does not have enough money for this transfer.",
                    e
            );
        } catch (BalanceLimitExceededException e) {
            throw new IllegalArgumentException(
                    "This transfer would exceed the destination account's maximum balance.",
                    e
            );
        }
    }

    private BigDecimal validateMoneyAmount(
            BigDecimal amount,
            String operation
    ) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    operation + " amount must be greater than zero."
            );
        }

        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(
                    operation + " amount exceeds the supported limit."
            );
        }

        try {
            // Reject fractions of a cent rather than silently rounding money.
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    operation + " amount must not contain fractions of a cent.",
                    e
            );
        }
    }

    private void requireCustomerForMoneyOperation(long customerId)
            throws SQLException {
        if (customerId <= 0) {
            throw new IllegalArgumentException(
                    "Customer ID must be greater than zero."
            );
        }

        BankUserEntity customer = userDAO.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found."
                ));

        if (!"CUSTOMER".equals(customer.getRole())) {
            throw new IllegalArgumentException(
                    "Only customers can perform these account transactions."
            );
        }
    }

    public TransferRecipientDTO getTransferRecipient(
            long customerId,
            long destinationAccountId
    ) throws SQLException {
        requireCustomerForMoneyOperation(customerId);

        if (destinationAccountId <= 0) {
            throw new IllegalArgumentException(
                    "Destination account number must be greater than zero."
            );
        }

        return accountDAO.findTransferRecipient(destinationAccountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "The destination account is unavailable or is not approved."
                ));
    }
}
