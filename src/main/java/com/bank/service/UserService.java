package com.bank.service;

import com.bank.entity.BankUserEntity;
import com.bank.repository.dto.UserResponseDTO;

import com.bank.repository.UserDAO;
import com.bank.util.PasswordUtil;

import java.sql.SQLException;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.passay.DefaultPasswordValidator;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.ValidationResult;

import org.passay.rule.CharacterRule;
import org.passay.data.EnglishCharacterData;
import org.passay.rule.LengthRule;
import org.passay.rule.HaveIBeenPwnedRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.passay.UnicodeString;

public class UserService {
    private final UserDAO userDAO;
    private final PasswordValidator passwordValidator;

    // DAO dependency injected through the constructor.
    // UserService uses it for database operations but does
    // not create it itself.
    public UserService(UserDAO userDAO) {
//        Application startup uses the full password policy.
        this(
                userDAO,
                new DefaultPasswordValidator(
                        new LengthRule(8, 128),
                        new CharacterRule(EnglishCharacterData.UpperCase, 1),
                        new CharacterRule(EnglishCharacterData.Digit, 1),
                        new CharacterRule(EnglishCharacterData.LowerCase, 1),
                        new HaveIBeenPwnedRule(
                                "console-banking",
                                "https://api.pwnedpasswords.com/range/",
                                false, // Reject exposed passwords.
                                false  // Require the online check to succeed.
                        )
                )
        );
    }

    public UserService(
            UserDAO userDAO,
            PasswordValidator passwordValidator
    ) {
        this.userDAO = userDAO;
        this.passwordValidator = passwordValidator;
    }

    public UserResponseDTO register(
            String username,
            char[] password,
            String firstName,
            String lastName,
            String email
    ) throws SQLException {
        username = validateText(username, "Username", 50);
        firstName = validateText(firstName, "First name", 100);
        lastName = validateText(lastName, "Last name", 100);
        email = validateText(email, "Email", 254);

        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();

            if (address.isGroup() || !email.equals(address.getAddress())) {
                throw new IllegalArgumentException(
                        "Enter a single email address, such as maya@example.com"
                );
            }
        } catch (AddressException e) {
            throw new IllegalArgumentException(
                    "Enter a valid email address.",
                    e
            );
        }

        if (isBlankPassword(password)) {
            throw new IllegalArgumentException(
                    "Password is required."
            );
        }

        // Isolate validation cleanup from the password needed for hashing afterward.
        char[] validationCharacters = Arrays.copyOf(password, password.length);
        PasswordData passwordData = new PasswordData(new UnicodeString(validationCharacters));

        try {
            ValidationResult result = passwordValidator.validate(passwordData);

            if (!result.isValid()) {

                boolean exposedPassword = result.getDetails().stream()
                        .anyMatch(detail -> HaveIBeenPwnedRule.ERROR_CODE.equals(detail.getErrorCode()
                        )
                );

                if (exposedPassword) {
                    throw new IllegalArgumentException(
                            "This password has appeared in a data breach. Choose a different password."
                    );
                }

                String errorCodes = result.getDetails().stream()
                        .map(detail -> detail.getErrorCode())
                        .collect(java.util.stream.Collectors.joining(", "));

                String message = String.join(" ", result.getMessages());

                if (message.trim().isEmpty()) {
                    message = "Password validation failed.";
                }

                throw new IllegalArgumentException(
                        message + " Rule codes: [" + errorCodes + "]"
                );
            }
        } finally {
            passwordData.clear();
            Arrays.fill(validationCharacters, '\0');
        }

        if (userDAO.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException(
                    "Username is already taken."
            );
        }

        String passwordHash = PasswordUtil.hashPassword(password);

        BankUserEntity user = new BankUserEntity(
                username,
                passwordHash,
                firstName,
                lastName,
                email
        );

        try {
            BankUserEntity savedUser = userDAO.createCustomer(user);
            return toResponse(savedUser);
        } catch (SQLException e) {
            // Another registration may claim the username after our lookup.
            if("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException(
                        "Username is already taken.",
                        e
                );
            }

            throw e;
        }
    }

    private String validateText(
            String value,
            String fieldName,
            int maximumLength
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " is required."
            );
        }

        String cleanedValue = value.trim();

        if (cleanedValue.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " must contain at most "
                        + maximumLength + " characters."
            );
        }
        return cleanedValue;
    }

    public UserResponseDTO login(String username, char[] password)
          throws SQLException {

        if (username == null
            || username.trim().isEmpty()
            || password == null
            || password.length == 0
        ) {
            throw new IllegalArgumentException(
                    "Username and password are required."
            );
        }

        // Use the same failure message for unknown users and wrong passwords.
        BankUserEntity user = userDAO.findByUsername(username.trim())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid username or password."
                        )
                );

        // Preserve password whitespace because it is part of the credential.
        if (!PasswordUtil.verifyPassword(
                password,
                user.getPasswordHash()
        )) {
            throw new IllegalArgumentException(
                    "Invalid username or password."
            );
        }

        return toResponse(user);
    }

    private boolean isBlankPassword(char[] password) {
        if (password == null || password.length == 0) {
            return true;
        }

        // Match the previous trim-based blank check without creating a String.
        for (char character : password) {
            if (character > ' ') {
                return false;
            }
        }

        return true;
    }


    private UserResponseDTO toResponse(BankUserEntity user) {
        // Select the fields exposed to the controller.
        return new UserResponseDTO(
                user.getUserId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole()
        );
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

//        Use the stored role to authorize employee-only operations.
        if (!"EMPLOYEE".equals(employee.getRole())) {
            throw new IllegalArgumentException(
                    "Only employees can view customer information."
            );
        }
    }

    public List<UserResponseDTO> getAllCustomers(long employeeId)
        throws SQLException {

        requireEmployee(employeeId);

        List<BankUserEntity> customers = userDAO.findAllCustomers();
        List<UserResponseDTO> responses = new ArrayList<>();

        for (BankUserEntity customer : customers) {
            responses.add(new UserResponseDTO(
                    customer.getUserId(),
                    customer.getUsername(),
                    customer.getFirstName(),
                    customer.getLastName(),
                    customer.getEmail(),
                    customer.getRole()
            ));
        }

        return responses;
    }

}
