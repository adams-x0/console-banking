package com.bank.service;

import com.bank.support.BankTestData;

import com.bank.entity.BankUserEntity;
import com.bank.repository.UserDAO;
import com.bank.repository.dto.UserResponseDTO;
import com.bank.util.PasswordUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.*;

import static com.bank.support.BankTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.ValidationResult;

import org.passay.RuleResultDetail;
import org.passay.rule.HaveIBeenPwnedRule;


public class UserServiceTest {

    private static String passwordHash;

    private UserDAO userDAO;
    private UserService userService;

    private PasswordValidator passwordValidator;

//    Runs once before all tests.
    @BeforeAll
    static void preparePasswordHash() {
        // Generate one hash shared by these tests to avoid repeated PBKDF2 setup.
        char[] password = BankTestData.validPassword();

        try {
            passwordHash = PasswordUtil.hashPassword(password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

//    Runs before every test method.
    @BeforeEach
    void setUp() throws SQLException {
        BankUserEntity user = BankTestData.customer(passwordHash);

        userDAO = mock(UserDAO.class);

//        Configure database responses used by the Login tests.
        when(userDAO.findByUsername(BankTestData.USERNAME)).thenReturn(Optional.of(user));

        when(userDAO.findByUsername(BankTestData.UNKNOWN_USERNAME)).thenReturn(Optional.empty());

//        Supply a controllable validator for registration tests.
        passwordValidator = mock(PasswordValidator.class);

        userService = new UserService(userDAO, passwordValidator);

    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("loginFailureCases")
    void loginRejectsInvalidCredentials(
            String scenario,
            String username,
            char[] password,
            String expectedMessage
    ){
        try {
//            Act and assert: every case must produce the expected error.
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.login(username, password)
            );

            assertEquals(expectedMessage, error.getMessage());
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }


    static Stream<Arguments> loginFailureCases(){
        String requiredMessage = "Username and password are required.";
        String invalidMessage = "Invalid username or password.";

//        Each case owns its password array because the test clears it afterward.
        return Stream.of(
                Arguments.of(
                        "Null username",
                        null,
                        BankTestData.validPassword(),
                        requiredMessage
                ),
                Arguments.of(
                        "Empty username",
                        "",
                        BankTestData.validPassword(),
                        requiredMessage
                ),
                Arguments.of(
                        "Unknown username",
                        BankTestData.UNKNOWN_USERNAME,
                        BankTestData.validPassword(),
                        invalidMessage
                ),
                Arguments.of(
                        "Username containing one space",
                        " ",
                        BankTestData.validPassword(),
                        requiredMessage
                ),
                Arguments.of(
                        "Username containing multiple space",
                        "  ",
                        BankTestData.validPassword(),
                        requiredMessage
                ),
                Arguments.of(
                        "Null password",
                        BankTestData.USERNAME,
                        null,
                        requiredMessage
                ),
                Arguments.of(
                        "Empty password",
                        BankTestData.USERNAME,
                        new char[0],
                        requiredMessage
                ),
                Arguments.of(
                        "Incorrect password",
                        BankTestData.USERNAME,
                        BankTestData.incorrectPassword(),
                        invalidMessage
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRegistrationFields")
    void registerRejectsMissingProfileFields(
            String scenario,
            String username,
            String firstName,
            String lastName,
            String email,
            String expectedMessage
    ) {
        char[] password = BankTestData.validPassword();

        try {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.register(
                            username,
                            password,
                            firstName,
                            lastName,
                            email
                    )
            );

            assertEquals(expectedMessage, error.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    static Stream<Arguments> missingRegistrationFields() {
        return Stream.of(
                Arguments.of(
                        "Null username",
                        null, FIRST_NAME, LAST_NAME, EMAIL,
                        "Username is required."
                ),
                Arguments.of(
                        "Blank username",
                        " ", FIRST_NAME, LAST_NAME, EMAIL,
                        "Username is required."
                ),
                Arguments.of(
                        "Null first name",
                        USERNAME, null, LAST_NAME, EMAIL,
                        "First name is required."
                ),
                Arguments.of(
                        "Blank first name",
                        USERNAME, " ", LAST_NAME, EMAIL,
                        "First name is required."
                ),
                Arguments.of(
                        "Null last name",
                        USERNAME, FIRST_NAME, null, EMAIL,
                        "Last name is required."
                ),
                Arguments.of(
                        "Blank last name",
                        USERNAME, FIRST_NAME, " ", EMAIL,
                        "Last name is required."
                ),
                Arguments.of(
                        "Null email",
                        USERNAME, FIRST_NAME, LAST_NAME, null,
                        "Email is required."
                ),
                Arguments.of(
                        "Blank email",
                        USERNAME, FIRST_NAME, LAST_NAME, " ",
                        "Email is required."
                )
        );
    }

//    This runs with null, an empty password, and a whitespace-only password.

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void registerRejectsMissingOrBlankPassword(String passwordValue) {
//        These synthetic inputs become fresh arrays for the service call.
        char[] password = passwordValue == null ? null : passwordValue.toCharArray();

        try {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.register(
                            USERNAME,
                            password,
                            FIRST_NAME,
                            LAST_NAME,
                            EMAIL
                    )

            );

            assertEquals("Password is required.", error.getMessage());
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

//    Invalid email formats
    @ParameterizedTest
    @ValueSource(strings = {
            "invalid-email",
            "@example.com",
            "test@",
            "test@@example.com"
    })
    void registerRejectsInvalidEmail(String email) {
        char[] password = BankTestData.validPassword();

        try {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.register(
                            USERNAME,
                            password,
                            FIRST_NAME,
                            LAST_NAME,
                            email
                    )
            );

            assertEquals("Enter a valid email address.", error.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }




//    Fields exceeding their maximum lengths
    @ParameterizedTest(name = "{0}")
    @MethodSource("oversizedRegistrationFields")
    void registerRejectsOversizedFields(
            String scenario,
            String username,
            String firstName,
            String lastName,
            String email,
            String expectedMessage
    ) {
        char[] password = BankTestData.validPassword();
        try {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.register(
                            username,
                            password,
                            firstName,
                            lastName,
                            email
                    )
            );

            assertEquals(expectedMessage, error.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }


    static Stream<Arguments> oversizedRegistrationFields() {
//        Exercise the first invalid length beyond each column's limit.

        return Stream.of(
                Arguments.of(
                        "Username exceeds 50 characters",
                        "a".repeat(51), FIRST_NAME, LAST_NAME, EMAIL,
                        "Username must contain at most 50 characters."
                ),
                Arguments.of(
                        "First name exceeds 100 characters",
                        USERNAME, "a".repeat(101), LAST_NAME, EMAIL,
                        "First name must contain at most 100 characters."
                ),Arguments.of(
                        "Last name exceeds 100 characters",
                        USERNAME, FIRST_NAME, "a".repeat(101), EMAIL,
                        "Last name must contain at most 100 characters."
                ),Arguments.of(
                        "Email exceeds 254 characters",
                        USERNAME, FIRST_NAME, LAST_NAME, "a".repeat(255),
                        "Email must contain at most 254 characters."
                )
        );
    }

//    Verifies successful login.
    @Test
    void loginReturnsUserDetailsForCorrectPassword() throws SQLException {

//        Arrange
        char[] password = BankTestData.validPassword();

        try {
//            Act
            UserResponseDTO result = userService.login(BankTestData.USERNAME, password);

//            Assert
            assertEquals(BankTestData.USER_ID, result.getUserId());
            assertEquals(BankTestData.USERNAME, result.getUsername());
            assertEquals(BankTestData.FIRST_NAME, result.getFirstName());
            assertEquals(BankTestData.LAST_NAME, result.getLastName());
            assertEquals(BankTestData.EMAIL, result.getEmail());
            assertEquals(BankTestData.ROLE, result.getRole());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

//    successful-registration test
    @Test
    void registerSavesCustomerAndReturnsUserDetails() throws SQLException {
        char[] password = BankTestData.validPassword();

        try {
            // Arrange
            acceptPasswordValidation();

            when(userDAO.findByUsername(BankTestData.NEW_USERNAME)).thenReturn(Optional.empty());

            when(userDAO.createCustomer(any(BankUserEntity.class)))
                    .thenAnswer(invocation -> {
                        BankUserEntity user = invocation.getArgument(0);

//                        Simulate the generated ID returned by the database.
                        user.setUserId(20L);
                        return user;
                    });

            // Act
            UserResponseDTO result = userService.register(
                    BankTestData.NEW_USERNAME,
                    password,
                    FIRST_NAME,
                    LAST_NAME,
                    EMAIL
            );

//            Capture the entity supplied to the DAO for insertion.
            ArgumentCaptor<BankUserEntity> captor = ArgumentCaptor.forClass(BankUserEntity.class);

            verify(userDAO).createCustomer(captor.capture());
            BankUserEntity savedUser = captor.getValue();

            assertEquals(BankTestData.NEW_USERNAME, savedUser.getUsername());
            assertEquals(FIRST_NAME, savedUser.getFirstName());
            assertEquals(LAST_NAME, savedUser.getLastName());
            assertEquals(EMAIL, savedUser.getEmail());
            assertEquals(ROLE, savedUser.getRole());

            assertNotNull(savedUser.getPasswordHash());
            assertTrue(savedUser.getPasswordHash().startsWith("pbkdf2-sha256$"));
            assertTrue(
                    PasswordUtil.verifyPassword(
                            password,
                            savedUser.getPasswordHash()
                    )
            );

            assertEquals(20L, result.getUserId());
            assertEquals(BankTestData.NEW_USERNAME, result.getUsername());
            assertEquals(FIRST_NAME, result.getFirstName());
            assertEquals(LAST_NAME, result.getLastName());
            assertEquals(EMAIL, result.getEmail());
            assertEquals(ROLE, result.getRole());

        } finally {
            Arrays.fill(password, '\0');
        }
    }


//    duplicate-username cases
    @ParameterizedTest(name = "Duplicate detected during insert: {0}")
    @ValueSource(booleans = {false, true})
    void registerRejectsDuplicateUsername(boolean duplicateDuringInsert)
        throws SQLException {
        char[] password = BankTestData.validPassword();

        try {
            acceptPasswordValidation();

            if (duplicateDuringInsert) {
//                Simulate another registration taking the username after lookup.
                when(userDAO.findByUsername(USERNAME))
                        .thenReturn(Optional.empty());
                when(userDAO.createCustomer(any(BankUserEntity.class)))
                        .thenThrow(new SQLException(
                                "Duplicate username",
                                "23505"
                        ));
            }
//            Otherwise, setup() already returns the existing user.
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.register(
                            USERNAME,
                            password,
                            FIRST_NAME,
                            LAST_NAME,
                            EMAIL
                    )
            );

            assertEquals("Username is already taken.", error.getMessage());

            if (duplicateDuringInsert) {
                verify(userDAO).createCustomer(any(BankUserEntity.class));
            } else {
                verify(userDAO, never()).createCustomer(any(BankUserEntity.class));
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    //    Password validation
    @ParameterizedTest(name = "{0}")
    @MethodSource("passwordValidationFailures")
    void registerRejectsPasswordValidationFailure(
            String scenario,
            String errorCode,
            String libraryMessage,
            String expectedMessage
    ) {
        char [] password = BankTestData.validPassword();

        try {
//            Arrange a specific failure returned by the password validator.
//            This data object needs no extra parameters for these tests.
            RuleResultDetail detail = new RuleResultDetail(
                    errorCode,
                    Collections.emptyMap()
            );

            ValidationResult validationResult = mock(ValidationResult.class);
            when(validationResult.isValid()).thenReturn(false);
            when(validationResult.getDetails()).thenReturn(Collections.singletonList(detail));
            when(validationResult.getMessages()).thenReturn(Collections.singletonList(libraryMessage));
            when(passwordValidator.validate(any(PasswordData.class))).thenReturn(validationResult);

//            Act and assert.
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.register(
                            NEW_USERNAME,
                            password,
                            FIRST_NAME,
                            LAST_NAME,
                            EMAIL
                    )
            );

            assertEquals(expectedMessage, error.getMessage());

//            Failed password validation must prevent customer creation.
            verify(userDAO, never()).createCustomer(any(BankUserEntity.class));

        } catch (SQLException e) {
            throw new AssertionError(e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    static Stream<Arguments> passwordValidationFailures() {
        return Stream.of(
                Arguments.of(
                        "Exposed password",
                        HaveIBeenPwnedRule.ERROR_CODE,
                        "",
                        "This password has appeared in a data breach. Choose a different password."
                ),
                Arguments.of(
                        "Readable validation message",
                        "TOO_SHORT",
                        "Password is too short.",
                        "Password is too short. Rule codes: [TOO_SHORT]"
                ),
                Arguments.of(
                        "Blank validation message",
                        "INSUFFICIENT_DIGIT",
                        "",
                        "Password validation failed. Rule codes: [INSUFFICIENT_DIGIT]"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databaseFailureCases")
    void propagatesUnexpectedDatabaseErrors(
            String scenario,
            boolean registration,
            boolean failureDuringInsert
    ) throws SQLException {
        char[] password = BankTestData.validPassword();

        try {
//            Use a non-duplicate error to exercise database-error propagation
            SQLException databaseError = new SQLException(
                    "Database connection lost.",
                    "08006"
            );

            if (registration) {
                acceptPasswordValidation();
            }

            if (failureDuringInsert) {
                when(userDAO.findByUsername(USERNAME)).thenReturn(Optional.empty());

                when(userDAO.createCustomer(any(BankUserEntity.class))).thenThrow(databaseError);
            } else {
                when(userDAO.findByUsername(USERNAME)).thenThrow(databaseError);
            }

            SQLException actualError = assertThrows(
                    SQLException.class,
                    () -> {
                        if (registration) {
                            userService.register(
                                    USERNAME,
                                    password,
                                    FIRST_NAME,
                                    LAST_NAME,
                                    EMAIL
                            );
                        } else {
                            userService.login(USERNAME, password);
                        }
                    }
            );

//            Preserve the original exception, including its SQL state and cause.
            assertSame(databaseError, actualError);

            if (failureDuringInsert) {
                verify(userDAO).createCustomer(any(BankUserEntity.class));
            } else {
//                A failed lookup must stop the operation before insertion.
                verify(userDAO, never()).createCustomer(any(BankUserEntity.class));
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    static Stream<Arguments> databaseFailureCases() {
        return Stream.of(
                Arguments.of("Login lookup fails", false, false),
                Arguments.of("Registration lookup fails", true, false),
                Arguments.of("Registration insert fails", true, true)
        );
    }

    @ParameterizedTest(name = "Employee views {0} customers")
    @ValueSource(ints = {0, 1, 3})
    void getAllCustomersReturnsCustomerDetails(int customerCount)
            throws SQLException {

        long employeeId = 99L;
        allowEmployee(employeeId);

        List<BankUserEntity> customers = new ArrayList<>();

        // Distinct values help detect incorrect mapping between customers.
        for (int i = 0; i < customerCount; i++) {
            customers.add(new BankUserEntity(
                    20L + i,
                    "customer" + i,
                    "unused-hash",
                    "First" + i,
                    "Last" + i,
                    "customer" + i + "@example.com",
                    "CUSTOMER",
                    null
            ));
        }

        when(userDAO.findAllCustomers()).thenReturn(customers);

        List<UserResponseDTO> results =
                userService.getAllCustomers(employeeId);

        assertEquals(customerCount, results.size());

        for (int i = 0; i < customerCount; i++) {
            BankUserEntity expected = customers.get(i);
            UserResponseDTO actual = results.get(i);

            assertEquals(expected.getUserId(), actual.getUserId());
            assertEquals(expected.getUsername(), actual.getUsername());
            assertEquals(expected.getFirstName(), actual.getFirstName());
            assertEquals(expected.getLastName(), actual.getLastName());
            assertEquals(expected.getEmail(), actual.getEmail());
            assertEquals(expected.getRole(), actual.getRole());
        }

        verify(userDAO).findById(employeeId);
        verify(userDAO).findAllCustomers();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("customerListAccessFailureCases")
    void getAllCustomersRejectsUnauthorizedUsers(
            String scenario,
            long employeeId,
            boolean userExists,
            String role,
            String expectedMessage
    ) throws SQLException {

        if (employeeId > 0) {
            if (userExists) {
                BankUserEntity user = BankTestData.customer("unused-hash");
                user.setUserId(employeeId);
                user.setRole(role);

                when(userDAO.findById(employeeId))
                        .thenReturn(Optional.of(user));
            } else {
                when(userDAO.findById(employeeId))
                        .thenReturn(Optional.empty());
            }
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> userService.getAllCustomers(employeeId)
        );

        assertEquals(expectedMessage, error.getMessage());

        // Customer information must not be fetched after failed authorization.
        verify(userDAO, never()).findAllCustomers();

        if (employeeId <= 0) {
            verify(userDAO, never()).findById(employeeId);
        } else {
            verify(userDAO).findById(employeeId);
        }
    }

    static Stream<Arguments> customerListAccessFailureCases() {
        return Stream.of(
                Arguments.of(
                        "Zero employee ID",
                        0L,
                        false,
                        null,
                        "Employee ID must be greater than zero."
                ),
                Arguments.of(
                        "Negative employee ID",
                        -1L,
                        false,
                        null,
                        "Employee ID must be greater than zero."
                ),
                Arguments.of(
                        "Employee not found",
                        99L,
                        false,
                        null,
                        "Employee not found."
                ),
                Arguments.of(
                        "Customer cannot list all customers",
                        BankTestData.USER_ID,
                        true,
                        "CUSTOMER",
                        "Only employees can view customer information."
                )
        );
    }

//    Next, cover database failures when viewing customers. There are two places a query can fail: looking up the employee or retrieving the customer list.

    @ParameterizedTest(name = "Database failure during customer listing: {0}")
    @ValueSource(booleans = {false, true})
    void getAllCustomersPropagatesDatabaseErrors(
            boolean failureDuringCustomerListing
    ) throws SQLException {

        long employeeId = 99L;

        SQLException databaseError = new SQLException(
                "Database connection lost.",
                "08006"
        );

        if (failureDuringCustomerListing) {
            allowEmployee(employeeId);

            when(userDAO.findAllCustomers())
                    .thenThrow(databaseError);
        } else {
            when(userDAO.findById(employeeId))
                    .thenThrow(databaseError);
        }

        SQLException actualError = assertThrows(
                SQLException.class,
                () -> userService.getAllCustomers(employeeId)
        );

        assertSame(databaseError, actualError);

        if (failureDuringCustomerListing) {
            verify(userDAO).findAllCustomers();
        } else {
            // Stop before fetching customers if employee lookup fails.
            verify(userDAO, never()).findAllCustomers();
        }
    }

    private void acceptPasswordValidation() {
        ValidationResult result = mock(ValidationResult.class);

        when(result.isValid()).thenReturn(true);
        when(passwordValidator.validate(any(PasswordData.class))).thenReturn(result);
    }

    private void allowEmployee(long employeeId) throws SQLException {
        BankUserEntity employee = BankTestData.customer("unused-hash");
        employee.setUserId(employeeId);
        employee.setRole("EMPLOYEE");

        when(userDAO.findById(employeeId))
                .thenReturn(Optional.of(employee));
    }
}
