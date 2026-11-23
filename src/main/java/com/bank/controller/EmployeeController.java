package com.bank.controller;

import com.bank.repository.dto.AccountResponseDTO;
import com.bank.repository.dto.UserResponseDTO;
import com.bank.service.AccountService;
import com.bank.service.UserService;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public class EmployeeController {

    private final UserService userService;
    private final AccountService accountService;

    public EmployeeController(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    private void viewCustomers(Scanner scanner, UserResponseDTO employee)
            throws SQLException {

//        The service verifies employee access before retrieving customers.
        List<UserResponseDTO> customers = userService.getAllCustomers(employee.getUserId());

        if (customers.isEmpty()) {
            System.out.println("There are no customers yet.");
            return;
        }

        while (true) {
            System.out.println();
            System.out.println("Customers");

            for (int i = 0; i < customers.size(); i++) {
                UserResponseDTO customer = customers.get(i);

                System.out.printf(
                        "%d %s %s (@%s)%n",
                        i + 1,
                        customer.getFirstName(),
                        customer.getLastName(),
                        customer.getUsername()
                );
            }

            System.out.println("0. Back");
            System.out.print("Choose a customer: ");

            int choice;

            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Enter a number from the list.");
                continue;
            }

            if (choice == 0) {
                return;
            }

            if (choice < 1 || choice > customers.size()) {
                System.out.println(
                        "Choose a number from 1 to "
                                + customers.size() + ", or 0 to go back."
                );
                continue;
            }

            //            The displayed position starts at 1; the list index starts at 0.
            UserResponseDTO selected = customers.get(choice - 1);

            showCustomerDetails(scanner, employee, selected);
        }

    }

    public void run(Scanner scanner, UserResponseDTO employee) {
//        UserController owns the shared scanner; do not close it here.
        while (true) {
            System.out.println();
            System.out.println("Employee Menu");
            System.out.println("1. View customers");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1":
                        viewCustomers(scanner, employee);
                        break;
                    case "0":
                        System.out.println("Logged out.");
                        return;
                    default:
                        System.out.println("Choose 1 or 0.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            } catch (SQLException e){
//               A failed query should not end the employee's session.
                System.out.println("The database operation failed. Please try again.");
            }
        }
    }

    private void showCustomerDetails(
            Scanner scanner,
            UserResponseDTO employee,
            UserResponseDTO customer
    ) throws SQLException {

//        Authorize the employee before retrieving the selected customer's accounts
        List<AccountResponseDTO> accounts =
                accountService.getCustomerAccountsForEmployee(
                        employee.getUserId(),
                        customer.getUserId()
                );

        System.out.println();
        System.out.println("Customer Details");
        System.out.println("___________________________");
        System.out.println("User ID: " + customer.getUserId());
        System.out.println("Username: " + customer.getUsername());
        System.out.println("First name: " + customer.getFirstName());
        System.out.println("Last name: " + customer.getLastName());
        System.out.println("Email: " + customer.getEmail());
        System.out.println("Role: " + customer.getRole());

        if (accounts.isEmpty()) {
            System.out.println();
            System.out.println("This customer has no accounts yet.");
            System.out.println("Press Enter to return to the customer list.");
            scanner.nextLine();
            return;
        }

        if (accounts.size() == 1) {
//            Skip selection when there is only one account.
            showAccountDetails(
                    scanner,
                    employee,
                    customer,
                    accounts.get(0)
            );
            return;
        }

        while (true) {
            System.out.println();
            System.out.println("Accounts");


// Show selection numbers separately from database accountIDs
            for (int i = 0; i < accounts.size(); i++) {
                AccountResponseDTO account = accounts.get(i);

                System.out.printf(
                        "%d. Account #%d | %s | Balance: %s%n",
                        i + 1,
                        account.getAccountId(),
                        account.getStatus(),
                        account.getBalance().toPlainString()
                );
            }

            System.out.println("0. Back to customers");
            System.out.println("Choose an account: ");

            int choice;

            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Enter a number from the list.");
                continue;
            }

            if (choice == 0){
                return;
            }

            if (choice < 1 || choice > accounts.size()) {
                System.out.println(
                        "Choose a number from 1 to "
                                + accounts.size() + ", or 0 to go back."
                );
                continue;
            }

//            Translate the displayed position into the selected account.
            AccountResponseDTO selected = accounts.get(choice - 1);

            showAccountDetails(scanner, employee, customer, selected);

//            Reload after returning so a completed review appears immediately.
            accounts = accountService.getCustomerAccountsForEmployee(
                    employee.getUserId(),
                    customer.getUserId()
            );
        }
    }

    private void showAccountDetails(
            Scanner scanner,
            UserResponseDTO employee,
            UserResponseDTO customer,
            AccountResponseDTO account
    ) throws SQLException {

        String customerLabel =
                customer.getFirstName() + " "
                        + customer.getLastName()
                        + " (@" + customer.getUsername() + ")";

        System.out.println();
        System.out.println("Customer: " + customerLabel);
        System.out.println("Account ID: " + account.getAccountId());
        System.out.println("____________________________________");
        System.out.println("Status: " + account.getStatus());
        System.out.println("Balance: " + account.getBalance().toPlainString());
        System.out.println("Applied at: " + account.getAppliedAt());



        if (!"PENDING".equals(account.getStatus())) {
            System.out.println();

//            Older callers may return a DTO without reviewer information
            if (account.getReviewerLabel() != null){
                System.out.println("Reviewed by: " + account.getReviewerLabel());
            } else {
                System.out.println("Reviewed by: unavailable");
            }

            if (account.getReviewedAt() != null) {
                System.out.println("Reviewed at: " + account.getReviewedAt());
            }

            System.out.println("This application has already been reviewed.");
            System.out.println("Press Enter to go back.");
            scanner.nextLine();
            return;
        }

        while (true) {
            System.out.println();
            System.out.println("1. Approve application");
            System.out.println("2. Deny application");
            System.out.println("0. Back");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();

            if ("0".equals(choice)){
                return;
            }

            if (!"1".equals(choice) && !"2".equals(choice)) {
                System.out.println("Choose 1, 2, or 0.");
                continue;
            }

            boolean approved = "1".equals(choice);
            String action = approved ? "approve" : "deny";

            System.out.print(
                    "Confirm: " + action
                            + " account #" + account.getAccountId()
                            + " for " + customerLabel + "? (Y/YES to confirm): "
            );

            String confirmation = scanner.nextLine().trim();

            if (!"Y".equalsIgnoreCase(confirmation)
                    && !"YES".equalsIgnoreCase(confirmation)) {
                System.out.println("Review cancelled.");
                continue;
            }

            try {
//                The service authorizes the employee; the DAO requires PENDING.
                accountService.reviewApplication(
                        employee.getUserId(),
                        account.getAccountId(),
                        approved
                );
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());

//                Return so the account list can refresh after a rejected review.
                return;
            }

            System.out.println(
                    "Account #" + account.getAccountId()
                            + " for " + customerLabel
                            + (approved ? " was approved." : " was denied.")
            );

            return;
        }
    }
}
