package com.bank.controller;

import com.bank.repository.dto.UserResponseDTO;
import com.bank.service.UserService;

import java.sql.SQLException;
import java.util.Scanner;

import java.io.Console;
import java.util.Arrays;

import java.util.NoSuchElementException;




public class UserController {

    private final UserService userService;
    private final CustomerController customerController;
    private final EmployeeController employeeController;

    public UserController(UserService userService,
                          CustomerController customerController,
                          EmployeeController employeeController) {
        this.userService = userService;
        this.customerController = customerController;
        this.employeeController = employeeController;
    }
    public void run() {

        Console console = System.console();

        if (console == null) {
            System.out.println("Run this application from a terminal to enter passwords securely.");
            return;
        }
        // Use the console's reader for ordinary input alongside hidden password input.
        try (Scanner scanner = new Scanner(console.reader())) {
            boolean running = true;

            while (running) {
                System.out.println();
                System.out.println("Console Banking");
                System.out.println("1. Register");
                System.out.println("2. Login");
                System.out.println("0. Exit");
                System.out.println("Choose an option: ");

                if (!scanner.hasNextLine()){
                    return;
                }

                String choice = scanner.nextLine().trim();

                try {
                    switch (choice) {
                        case "1":
                            register(scanner, console);
                            break;
                        case "2":
                            login(scanner, console);
                            break;
                        case "0":
                            running = false;
                            System.out.println("Goodbye");
                            break;
                        default:
                            System.out.println("Choose 1, 2, or 0.");
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println(e.getMessage());
                } catch (SQLException e) {
                    System.out.println("The database operation failed. Please try again.");
                } catch (NoSuchElementException e) {
                    // End the session cleanly if console input closes a form.
                    System.out.println("Input closed. Goodbye.");
                    return;
                }
            }
        }
    }

    private void register(Scanner scanner, Console console)
            throws SQLException{

        System.out.println();
        System.out.println("Customer Registration");

        System.out.print("Username: ");
        String username = scanner.nextLine();

        System.out.print("First name: ");
        String firstName = scanner.nextLine();

        System.out.print("Last name: ");
        String lastName = scanner.nextLine();

        System.out.print("Email: ");
        String email = scanner.nextLine();

        System.out.println(
                "Password must have 8-128 characters, "
                        + "an uppercase letter, a lowercase letter, "
                        + "and a digit."
        );

        char[] password = null;
        char[] confirmation = null;

        try {
            password = readPassword(console, "Password: ");
            confirmation = readPassword(console, "Confirm password: ");

            if (password == null || confirmation == null) {
                System.out.println("Registration cancelled.");
                return;
            }

            if (!Arrays.equals(password, confirmation)) {
                System.out.println("Passwords do not match.");
                return;
            }

            UserResponseDTO user = userService.register(
                username,
                password,
                firstName,
                lastName,
                email
            );

            System.out.println("Registration successful!");
            System.out.println("User ID: " + user.getUserId());
            System.out.println("Username: " + user.getUsername());

        } finally {
            // Clear input on success, validation failure, cancellation or DB failure.
            clearPassword(password);
            clearPassword(confirmation);
        }

    }

    private void login(Scanner scanner, Console console)
            throws SQLException{

        System.out.println();
        System.out.println("Login");

        System.out.print("Username: ");
        String username = scanner.nextLine();

        char[] password = null;
        UserResponseDTO user;

        try {
            password = readPassword(console, "Password: ");

            if (password == null) {
                System.out.println("Login cancelled.");
                return;
            }

            user = userService.login(username, password);

        } finally {
//            Clear the password before entering customer menu.
            clearPassword(password);
        }

        System.out.println("Login successful!");
        System.out.println("Welcome, " + user.getFirstName() + "!");
        System.out.println("Role: " + user.getRole());

        if ("CUSTOMER".equals(user.getRole())) {
            customerController.run(scanner, user);
        } else if ("EMPLOYEE".equals(user.getRole())) {
            employeeController.run(scanner, user);
        } else {
            System.out.println("No menu is available for this user role.");
        }
    }

    private char[] readPassword(Console console, String prompt) {
        return console.readPassword("%s", prompt);
    }

    private void clearPassword(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
