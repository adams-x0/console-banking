package com.bank.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import java.security.MessageDigest;


public final class PasswordUtil {

    // Number of PBKDF2 iterations.
    // Higher values increase security by making brute-force attacks slower.
    private static final int ITERATIONS = 600_000;

    // Desired length of the generated hash in bits.
    private static final int KEY_LENGTH = 256;

    // Cryptographically secure random number generator.
    private static final SecureRandom RANDOM = new SecureRandom();

    // Private constructor prevents instantiation.
    // This class is intended to be used only through static methods.
    private PasswordUtil() {
    }

    /**
     * Hashes a password using PBKDF2 with SHA-256 and a random salt.
     *
     * @param password Plain-text password
     * @return Formatted string containing:
     *         algorithm$iterations$salt$hash
     */

    public static String hashPassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException(
                    "Password must not be empty."
            );
        }

        // Generate a random 16-byte salt.
        // A fresh random salt makes identical passwords produce
        // different stored hashes with overwhelming probability.
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);

        // Create PBKDF2 specification:
        // - password characters
        // - random salt
        // - iteration count
        // - resulting key size
        PBEKeySpec specification = new PBEKeySpec(
                password,
                salt,
                ITERATIONS,
                KEY_LENGTH
        );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            // Generate the password hash.
            byte[] hash = factory.generateSecret(specification).getEncoded();

            // Store algorithm, iteration count, salt, and hash.
            // Salt and hash are encoded as Base64 strings.
            return "pbkdf2-sha256$" + ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException e) {

            // Wrap checked exception into unchecked exception.
            throw new IllegalStateException(
                    "Password hashing failed.",
                    e
            );
        } finally {
           specification.clearPassword();
        }
    }

    public static boolean verifyPassword(
            char[] password,
            String storedHash
    ){
        if (password == null || storedHash == null) {
            return false;
        }

        // Stored format: algorithm$iteration$salt$hash.
        // Escape the regex separator and preserve entry components.
        String[] parts = storedHash.split("\\$", -1);

        if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) {
            return false;
        }

        int iterations;
        byte[] salt;
        byte[] expectedHash;

        try {
            iterations = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            expectedHash = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            // Malformed stored values cannot be used to verify a password.
            return false;
        }

        // Our hashPassword() method produces a 16-byte salt and 32-byte hash.
        if (iterations <= 0 || salt.length != 16 || expectedHash.length != 32) {
            return false;
        }

        // Reuse the saved settings so the same password produces the same hash.
        PBEKeySpec specification = new PBEKeySpec(
                password,
                salt,
                iterations,
                expectedHash.length * 8 // PBKeySpec expects the length in bits.
        );

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            byte[] actualHash = factory.generateSecret(specification).getEncoded();

            // Use a comparison intended for cryptographic hash values.
            return MessageDigest.isEqual(expectedHash, actualHash);

        } catch (GeneralSecurityException e) {
            // A cryptographic failure is an application error.
            throw new IllegalStateException(
                    "Password verification failed.",
                    e
            );
        } finally {
            // Clear the password copy held by the specification.
            specification.clearPassword();
        }
    }

}

