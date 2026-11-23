package com.bank.controller;

import com.bank.repository.dto.AccountResponseDTO;
import com.bank.repository.dto.UserResponseDTO;
import com.bank.service.AccountService;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

import java.math.BigDecimal;
import java.util.ArrayList;

import com.bank.repository.dto.TransferRecipientDTO;

public class CustomerController {

    private final AccountService accountService;

    public CustomerController(AccountService accountService) {
        this.accountService = accountService;
    }

    public void run(Scanner scanner, UserResponseDTO user) {
//        UserController owns the shared scanner; do not close it here.
        while (true) {
            System.out.println();
            System.out.println("Customer Menu");
            System.out.println("1. Apply for a bank account");
            System.out.println("2. View my accounts");
            System.out.println("3. Deposit");
            System.out.println("4. Withdraw");
            System.out.println("5. Transfer");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");

//            Input closure propagates to UserController.run(), which ends the session.
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1":
                        applyForAccount(user);
                        break;
                    case "2":
                        viewAccounts(user);
                        break;
                    case "3":
                        performMoneyOperation(scanner, user, "Deposit");
                        break;
                    case "4":
                        performMoneyOperation(scanner, user, "Withdrawal");
                        break;
                    case "5":
                        performMoneyOperation(scanner, user, "Transfer");
                        break;
                    case "0":
                        System.out.println("Logged out.");
                        return;
                    default:
                        System.out.println("Choose 1, 2, or 0.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            } catch (SQLException e){
//                Keep the customer Logged in if an account operation fails.
                System.out.println("The database operation failed. Please try again.");
            }
        }
    }

    private void applyForAccount(UserResponseDTO user)
            throws SQLException {
//        Ownership comes from the authenticated user, not a typed customer ID.
        AccountResponseDTO account = accountService.applyForAccount(user.getUserId());

        System.out.println();
        System.out.println("Application submitted!");
        System.out.println("Account ID: " + account.getAccountId());
        System.out.println("Balance: " + account.getBalance());
        System.out.println("Status: " + account.getStatus());
        System.out.println("Applied at: " + account.getAppliedAt());
        System.out.println("Your application is awaiting employee review.");
    }

    private void viewAccounts (UserResponseDTO user)
            throws SQLException {
//        Retrieve accounts for the authenticated customer.

        List<AccountResponseDTO> accounts = accountService.getCustomerAccounts(user.getUserId());

        if (accounts.isEmpty()) {
            System.out.println("You have no accounts yet.");
            return;
        }

        System.out.println();
        System.out.println("Your Accounts");

        for (AccountResponseDTO account : accounts) {
            System.out.println("___________________________________");
            System.out.println("Account ID: " + account.getAccountId());
            System.out.println("Balance: " + account.getBalance());
            System.out.println("Status: " + account.getStatus());
            System.out.println("Applied at: " + account.getAppliedAt());
        }

    }

    private AccountResponseDTO selectApprovedAccount(
            Scanner scanner,
            UserResponseDTO user
    ) throws SQLException {
        List<AccountResponseDTO> approvedAccounts = new ArrayList<>();

        for (AccountResponseDTO account :
                accountService.getCustomerAccounts(user.getUserId())) {
            if ("APPROVED".equals(account.getStatus())) {
                approvedAccounts.add(account);
            }
        }

        if (approvedAccounts.isEmpty()) {
            System.out.println(
                    "You need an approved account to perform this operation."
            );
            return null;
        }

        // Skip selection when there is only one eligible account.
        if (approvedAccounts.size() == 1) {
            AccountResponseDTO account = approvedAccounts.get(0);

            System.out.println(
                    "Selected account #" + account.getAccountId()
                            + " | Balance: "
                            + account.getBalance().toPlainString()
            );

            return account;
        }

        while (true) {
            System.out.println();
            System.out.println("Your Approved Accounts");

            for (int i = 0; i < approvedAccounts.size(); i++) {
                AccountResponseDTO account = approvedAccounts.get(i);

                System.out.println(
                        (i + 1) + ". Account #" + account.getAccountId()
                                + " | Balance: "
                                + account.getBalance().toPlainString()
                );
            }

            System.out.println("0. Cancel");
            System.out.print("Choose an account: ");

            int choice;

            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Enter a number from the list.");
                continue;
            }

            if (choice == 0) {
                return null;
            }

            if (choice < 1 || choice > approvedAccounts.size()) {
                System.out.println("Choose an account from the list.");
                continue;
            }

            // Menu positions are different from database account IDs.
            return approvedAccounts.get(choice - 1);
        }
    }

    private void performMoneyOperation(
            Scanner scanner,
            UserResponseDTO user,
            String operation
    ) throws SQLException {
        System.out.println();
        System.out.println(operation);

        AccountResponseDTO account = selectApprovedAccount(scanner, user);

        if (account == null) {
            return;
        }

        long destinationAccountId = 0;

        TransferRecipientDTO recipient = null;

        if ("Transfer".equals(operation)) {
            System.out.print("Destination account number (0 to cancel): ");

            try {
                destinationAccountId =
                        Long.parseLong(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid account number.");
                return;
            }

            if (destinationAccountId == 0) {
                return;
            }

            if (destinationAccountId < 0) {
                System.out.println("Account number must be greater than zero.");
                return;
            }

            if (destinationAccountId == account.getAccountId()) {
                System.out.println("Choose a different destination account.");
                return;
            }

            recipient = accountService.getTransferRecipient(
                    user.getUserId(),
                    destinationAccountId
            );

            System.out.println("Recipient: " + recipient.getOwnerLabel());
        }

        System.out.print("Amount (0 to cancel): ");

        BigDecimal amount;

        try {
            // Parse money directly from text, avoiding double precision errors.
            amount = new BigDecimal(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Enter a valid amount, such as 25.50.");
            return;
        }

        if (amount.signum() == 0) {
            return;
        }

        String customerLabel =
                user.getFirstName() + " " + user.getLastName()
                        + " (@" + user.getUsername() + ")";

        System.out.println();
        System.out.println("Customer: " + customerLabel);
        System.out.println("Operation: " + operation);
        System.out.println("Account: #" + account.getAccountId());

        if (recipient != null) {
            System.out.println("Recipient: " + recipient.getOwnerLabel());
            System.out.println(
                    "Destination account: #" + recipient.getAccountId()
            );
        }

        System.out.println("Amount entered: " + amount);
        System.out.print("Confirm (Y/YES): ");

        String confirmation = scanner.nextLine().trim();

        if (!"Y".equalsIgnoreCase(confirmation)
                && !"YES".equalsIgnoreCase(confirmation)) {
            System.out.println("Operation cancelled.");
            return;
        }

        BigDecimal updatedBalance;

        // The service validates the amount; the DAO rechecks locked account data.
        switch (operation) {
            case "Deposit":
                updatedBalance = accountService.deposit(
                        user.getUserId(),
                        account.getAccountId(),
                        amount
                );
                break;

            case "Withdrawal":
                updatedBalance = accountService.withdraw(
                        user.getUserId(),
                        account.getAccountId(),
                        amount
                );
                break;

            case "Transfer":
                updatedBalance = accountService.transfer(
                        user.getUserId(),
                        account.getAccountId(),
                        destinationAccountId,
                        amount
                );
                break;

            default:
                throw new IllegalArgumentException("Unknown account operation.");
        }

        System.out.println(operation + " successful!");
        System.out.println("Account: #" + account.getAccountId());
        System.out.println("New balance: " + updatedBalance.toPlainString());
    }
}
