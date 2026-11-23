package com.bank.repository.dto;

// Contains the user details returned after registration or login
public class UserResponseDTO {
    private final long userId;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String role;

    public UserResponseDTO(long userId,
                           String username,
                           String firstName,
                           String lastName,
                           String email,
                           String role) {
        this.userId = userId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
