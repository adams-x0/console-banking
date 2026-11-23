package com.bank.service;

import com.bank.entity.AccountEntity;
import com.bank.entity.BankUserEntity;
import com.bank.repository.AccountDAO;
import com.bank.repository.UserDAO;
import com.bank.repository.dto.AccountResponseDTO;
import com.bank.repository.exception.PendingApplicationException;
import com.bank.support.BankTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class AccountServiceTest {

    private AccountDAO accountDAO;
    private UserDAO userDAO;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountDAO = mock(AccountDAO.class);
        userDAO = mock(UserDAO.class);
        accountService = new AccountService(accountDAO, userDAO);
    }

    @Test
    void applyForAccountReturnsPendingApplication() throws SQLException{
//        Account application does not inspect the customer's password hash.
        BankUserEntity customer = BankTestData.customer("unused-hash");

        OffsetDateTime appliedAt = OffsetDateTime.parse("2026-11-19T12:00:00Z");

        AccountEntity savedAccount = new AccountEntity(
                10L,
                new BigDecimal("0.00"),
                "PENDING",
                appliedAt,
                null,
                null
        );
        when(userDAO.findById(BankTestData.USER_ID))
                .thenReturn(Optional.of(customer));
        when(accountDAO.createApplication(BankTestData.USER_ID))
                .thenReturn(savedAccount);

        AccountResponseDTO result = accountService.applyForAccount(BankTestData.USER_ID);

        assertEquals(10L, result.getAccountId());
        assertEquals(new BigDecimal("0.00"), result.getBalance());
        assertEquals("PENDING", result.getStatus());
        assertEquals(appliedAt, result.getAppliedAt());

        verify(accountDAO).createApplication(BankTestData.USER_ID);
    }

    @ParameterizedTest(name = "Reject customer ID: {0}")
    @ValueSource(longs = {0L, -1L})
    void applyForAccountRejectsInvalidId(long customerId) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.applyForAccount(customerId)
        );

        assertEquals(
                "Customer ID must be greater than zero.",
                error.getMessage()
        );

//        Invalid input should be rejected before any database operation
        verifyNoInteractions(userDAO, accountDAO);

    }

    @Test
    void applyForAccountRejectsMissingCustomer() throws SQLException {
        when(userDAO.findById(BankTestData.USER_ID))
                .thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.applyForAccount(BankTestData.USER_ID)
        );

        assertEquals("Customer not found.", error.getMessage());
        verifyNoInteractions(accountDAO);
    }

    @Test
    void applyForAccountsRejectsEmployee() throws SQLException {
        BankUserEntity employee = BankTestData.customer("unused-hash");
        employee.setRole("EMPLOYEE");

        when(userDAO.findById(BankTestData.USER_ID))
                .thenReturn(Optional.of(employee));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.applyForAccount(BankTestData.USER_ID)
        );


        assertEquals(
                "Only customers can apply for bank accounts.",
                error.getMessage()
        );

        verifyNoInteractions(accountDAO);
    }

    @ParameterizedTest(name = "Database failure during insert: {0}")
    @ValueSource(booleans = {false, true})

    void applyForAccountPropagatesDatabaseErrors(
            boolean failureDuringInsert
    ) throws SQLException {
        SQLException databaseError = new SQLException(
                "Database connection lost.",
                "08006"
        );

        if (failureDuringInsert) {
            BankUserEntity customer = BankTestData.customer("unused-hash");

            when(userDAO.findById(BankTestData.USER_ID))
                    .thenReturn(Optional.of(customer));
            when(accountDAO.createApplication(BankTestData.USER_ID))
                    .thenThrow(databaseError);
        } else {
            when(userDAO.findById(BankTestData.USER_ID))
                    .thenThrow(databaseError);
        }

        SQLException actualError = assertThrows(
                SQLException.class,
                () -> accountService.applyForAccount(BankTestData.USER_ID)
        );

//        Keep the original database exception available to the caller.
        assertSame(databaseError, actualError);

        if (failureDuringInsert) {
            verify(accountDAO).createApplication(BankTestData.USER_ID);
        } else {
            verifyNoInteractions(accountDAO);
        }
    }

    @Test
    void applyForAccountRejectsExistingPendingApplication()
        throws SQLException {
        BankUserEntity customer = BankTestData.customer("unused-hash");
        PendingApplicationException conflict = new PendingApplicationException();

        when(userDAO.findById(BankTestData.USER_ID))
                .thenReturn(Optional.of(customer));

        when(accountDAO.createApplication(BankTestData.USER_ID))
                .thenThrow(conflict);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.applyForAccount(BankTestData.USER_ID)
        );

        assertEquals(
                "You already have a pending account application. "
                + "Please wait for an employee to review it.",
                error.getMessage()
        );

        assertSame(conflict, error.getCause());
    }

//    . Group the empty, single-account, and multiple-account cases

    @ParameterizedTest(name = "Customer has {0} accounts")
    @ValueSource(ints = {0, 1, 3})

    void getCustomerAccountsReturnsAccountDetails(int accountCount)
            throws SQLException {

        BankUserEntity customer = BankTestData.customer("unused-hash");
        OffsetDateTime appliedAt =
                OffsetDateTime.parse("2026-11-19T12:00:00Z");

        List<AccountEntity> accounts = new ArrayList<>();
        String[] statuses = {"PENDING", "APPROVED", "DENIED"};

        for (int i = 0; i < accountCount; i++) {
            accounts.add(new AccountEntity(
                    10L + i,
                    new BigDecimal("0.00"),
                    statuses[i],
                    appliedAt.plusDays(i),
                    i == 0 ? null : 99L,
                    i == 0 ? null : appliedAt.plusDays(i).plusHours(1)
            ));
        }

        when(userDAO.findById(BankTestData.USER_ID))
                .thenReturn(Optional.of(customer));
        when(accountDAO.findByOwnerId(BankTestData.USER_ID))
                .thenReturn(accounts);


//        Only the three-account case includes reviewed accounts.
        if (accountCount > 1) {
            allowEmployee(99L);
        }
        List<AccountResponseDTO> results =
                accountService.getCustomerAccounts(BankTestData.USER_ID);

        assertEquals(accountCount, results.size());

        // Verify every returned account is mapped, including its status.
        for (int i = 0; i < accountCount; i++) {
            AccountEntity expected = accounts.get(i);
            AccountResponseDTO actual = results.get(i);

            assertEquals(expected.getAccountId(), actual.getAccountId());
            assertEquals(expected.getBalance(), actual.getBalance());
            assertEquals(expected.getStatus(), actual.getStatus());
            assertEquals(expected.getAppliedAt(), actual.getAppliedAt());
            assertEquals(expected.getReviewedAt(), actual.getReviewedAt());

            if (expected.getReviewedBy() == null){
                assertNull(actual.getReviewerLabel());
            } else {
                assertEquals(
                        BankTestData.FIRST_NAME + " "
                            + BankTestData.LAST_NAME
                        + " (@" + BankTestData.USERNAME + ")",
                        actual.getReviewerLabel()
                );
            }
        }

        verify(accountDAO).findByOwnerId(BankTestData.USER_ID);
    }

//    Reject invalid IDs before calling either DAO

    @ParameterizedTest(name = "Reject account lookup for ID: {0}")
    @ValueSource(longs = {0L, -1L})
    void getCustomerAccountsRejectsInvalidId(long customerId) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getCustomerAccounts(customerId)
        );

        assertEquals(
                "Customer ID must be greater than zero.",
                error.getMessage()
        );

        verifyNoInteractions(userDAO, accountDAO);
    }

//    Check missing customers and employee access
    @Test
    void getCustomerAccountsRejectsMissingCustomer() throws SQLException {
        when(userDAO.findById(BankTestData.USER_ID))
                .thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getCustomerAccounts(BankTestData.USER_ID)
        );

        assertEquals("Customer not found.", error.getMessage());
        verifyNoInteractions(accountDAO);
    }

    @Test
    void getCustomerAccountsRejectsEmployee() throws SQLException {
        BankUserEntity employee = BankTestData.customer("unused-hash");
        employee.setRole("EMPLOYEE");

        when(userDAO.findById(BankTestData.USER_ID))
                .thenReturn(Optional.of(employee));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getCustomerAccounts(BankTestData.USER_ID)
        );

        assertEquals(
                "Only customers can use this account view.",
                error.getMessage()
        );

        verifyNoInteractions(accountDAO);
    }

//    Group database failures at either lookup
    @ParameterizedTest(name = "Database failure during account lookup: {0}")
    @ValueSource(booleans = {false, true})
    void getCustomerAccountsPropagatesDatabaseErrors(
            boolean failureDuringAccountLookup
    ) throws SQLException
    {

        SQLException databaseError = new SQLException(
                "Database connection lost.",
                "08006"
        );

        if (failureDuringAccountLookup) {
            BankUserEntity customer = BankTestData.customer("unused-hash");

            when(userDAO.findById(BankTestData.USER_ID))
                    .thenReturn(Optional.of(customer));
            when(accountDAO.findByOwnerId(BankTestData.USER_ID))
                    .thenThrow(databaseError);
        } else {
            when(userDAO.findById(BankTestData.USER_ID))
                    .thenThrow(databaseError);
        }

        SQLException actualError = assertThrows(
                SQLException.class,
                () -> accountService.getCustomerAccounts(BankTestData.USER_ID)
        );

        assertSame(databaseError, actualError);

        if (failureDuringAccountLookup) {
            verify(accountDAO).findByOwnerId(BankTestData.USER_ID);
        } else {
            // Do not query accounts when the customer lookup fails.
            verifyNoInteractions(accountDAO);
        }
    }

    @ParameterizedTest(name = "{0}; review operation: {1}")
    @MethodSource("employeeAccessFailureCases")
    void employeeOperationsRejectUnauthorizedUsers(
            String scenario,
            boolean reviewOperation,
            long employeeId,
            boolean userExists,
            String role,
            String expectedMessage
    ) throws SQLException {

        // Invalid IDs must be rejected before the service queries the DAO.
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
                () -> {
                    if (reviewOperation) {
                        accountService.reviewApplication(
                                employeeId,
                                10L,
                                true
                        );
                    } else {
                        accountService.getPendingApplications(employeeId);
                    }
                }
        );

        assertEquals(expectedMessage, error.getMessage());

        // Neither listing nor updating accounts is allowed without authorization.
        verifyNoInteractions(accountDAO);

        if (employeeId <= 0) {
            verifyNoInteractions(userDAO);
        } else {
            verify(userDAO).findById(employeeId);
        }
    }

    static Stream<Arguments> employeeAccessFailureCases() {
        return Stream.of(false, true)
                .flatMap(reviewOperation -> Stream.of(
                        Arguments.of(
                                "Zero employee ID",
                                reviewOperation,
                                0L,
                                false,
                                null,
                                "Employee ID must be greater than zero."
                        ),
                        Arguments.of(
                                "Negative employee ID",
                                reviewOperation,
                                -1L,
                                false,
                                null,
                                "Employee ID must be greater than zero."
                        ),
                        Arguments.of(
                                "Employee not found",
                                reviewOperation,
                                BankTestData.USER_ID,
                                false,
                                null,
                                "Employee not found."
                        ),
                        Arguments.of(
                                "Customer role",
                                reviewOperation,
                                BankTestData.USER_ID,
                                true,
                                "CUSTOMER",
                                "Only employees can review account applications."
                        )
                ));
    }

    private void allowEmployee(long employeeId) throws SQLException {
        BankUserEntity employee = BankTestData.customer("unused-hash");
        employee.setUserId(employeeId);
        employee.setRole("EMPLOYEE");

        when(userDAO.findById(employeeId))
                .thenReturn(Optional.of(employee));
    }

//    test successful employee operations: listing pending applications and approving or denying one.
//This covers an empty list, one application, and several applications. It verifies the service maps every returned entity correctly.
    @ParameterizedTest(name = "Pending applications: {0}")
    @ValueSource(ints = {0, 1, 3})
    void getPendingApplicationsReturnsAccountDetails(int applicationCount)
            throws SQLException {

        long employeeId = 99L;
        allowEmployee(employeeId);

        OffsetDateTime appliedAt =
                OffsetDateTime.parse("2026-11-19T12:00:00Z");

        List<AccountEntity> applications = new ArrayList<>();

        // These applications may belong to different customers.
        for (int i = 0; i < applicationCount; i++) {
            applications.add(new AccountEntity(
                    20L + i,
                    new BigDecimal("0.00"),
                    "PENDING",
                    appliedAt.plusDays(i),
                    null,
                    null
            ));
        }

        when(accountDAO.findPendingApplications())
                .thenReturn(applications);

        List<AccountResponseDTO> results =
                accountService.getPendingApplications(employeeId);

        assertEquals(applicationCount, results.size());

        for (int i = 0; i < applicationCount; i++) {
            AccountEntity expected = applications.get(i);
            AccountResponseDTO actual = results.get(i);

            assertEquals(expected.getAccountId(), actual.getAccountId());
            assertEquals(expected.getBalance(), actual.getBalance());
            assertEquals(expected.getStatus(), actual.getStatus());
            assertEquals(expected.getAppliedAt(), actual.getAppliedAt());
        }

        verify(accountDAO).findPendingApplications();
    }

    @ParameterizedTest(name = "Review decision approved: {0}")
    @ValueSource(booleans = {true, false})
    void reviewApplicationSavesEmployeeDecision(boolean approved)
            throws SQLException {

        // Different IDs help catch accidentally swapped method arguments.
        long employeeId = 99L;
        long accountId = 20L;

        allowEmployee(employeeId);

        when(accountDAO.reviewApplication(accountId, employeeId, approved))
                .thenReturn(true);

        assertDoesNotThrow(
                () -> accountService.reviewApplication(
                        employeeId,
                        accountId,
                        approved
                )
        );

        verify(accountDAO).reviewApplication(
                accountId,
                employeeId,
                approved
        );
    }

//    an invalid account ID and an application the DAO cannot update.

    @ParameterizedTest(name = "Reject review for account ID: {0}")
    @ValueSource(longs = {0L, -1L})
    void reviewApplicationRejectsInvalidAccountId(long accountId)
            throws SQLException {

        long employeeId = 99L;
        allowEmployee(employeeId);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.reviewApplication(
                        employeeId,
                        accountId,
                        true
                )
        );

        assertEquals(
                "Account ID must be greater than zero.",
                error.getMessage()
        );

        // Invalid account IDs must never reach the update operation.
        verifyNoInteractions(accountDAO);
    }

    @ParameterizedTest(name = "Unsuccessful review; approved: {0}")
    @ValueSource(booleans = {true, false})
    void reviewApplicationRejectsMissingOrReviewedApplication(
            boolean approved
    ) throws SQLException {

        long employeeId = 99L;
        long accountId = 20L;
        allowEmployee(employeeId);

        // No row matches the DAO's account ID and PENDING conditions.
        when(accountDAO.reviewApplication(accountId, employeeId, approved))
                .thenReturn(false);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.reviewApplication(
                        employeeId,
                        accountId,
                        approved
                )
        );

        assertEquals(
                "Application not found or already reviewed.",
                error.getMessage()
        );

        verify(accountDAO).reviewApplication(
                accountId,
                employeeId,
                approved
        );
    }

//    test that an employee receives the selected customer’s accounts

    @ParameterizedTest(name = "Employee views customer with {0} accounts")
    @ValueSource(ints = {0, 1, 3})
    void getCustomerAccountsForEmployeeReturnsSelectedCustomerAccounts(
            int accountCount
    ) throws SQLException {

        // Different IDs help catch querying the employee's accounts by mistake.
        long employeeId = 99L;
        long reviewerId = 98L;
        long customerId = BankTestData.USER_ID;

        allowEmployee(employeeId);

        if (accountCount > 0) {
            allowEmployee(reviewerId);
        }

        BankUserEntity customer = BankTestData.customer("unused-hash");
        when(userDAO.findById(customerId))
                .thenReturn(Optional.of(customer));

        OffsetDateTime appliedAt =
                OffsetDateTime.parse("2026-11-19T12:00:00Z");

        List<AccountEntity> accounts = new ArrayList<>();

        for (int i = 0; i < accountCount; i++) {
            accounts.add(new AccountEntity(
                    20L + i,
                    new BigDecimal("125.50"),
                    "APPROVED",
                    appliedAt.plusDays(i),
                    reviewerId,
                    appliedAt.plusDays(i).plusHours(1)
            ));
        }

        when(accountDAO.findByOwnerId(customerId))
                .thenReturn(accounts);

        List<AccountResponseDTO> results =
                accountService.getCustomerAccountsForEmployee(
                        employeeId,
                        customerId
                );

        assertEquals(accountCount, results.size());

        for (int i = 0; i < accountCount; i++) {
            AccountEntity expected = accounts.get(i);
            AccountResponseDTO actual = results.get(i);

            assertEquals(expected.getAccountId(), actual.getAccountId());
            assertEquals(expected.getBalance(), actual.getBalance());
            assertEquals(expected.getStatus(), actual.getStatus());
            assertEquals(expected.getAppliedAt(), actual.getAppliedAt());
            assertEquals(
                    BankTestData.FIRST_NAME + " "
                            + BankTestData.LAST_NAME
                            + " (@" + BankTestData.USERNAME + ")",
                    actual.getReviewerLabel()
            );
            assertEquals(expected.getReviewedAt(), actual.getReviewedAt());
        }

        verify(userDAO).findById(employeeId);
        verify(userDAO).findById(customerId);
        verify(accountDAO).findByOwnerId(customerId);
        verify(accountDAO, never()).findByOwnerId(employeeId);
    }

//    test that a customer cannot use this employee operation:
@Test
void getCustomerAccountsForEmployeeRejectsCustomerCaller()
        throws SQLException {

    long callerId = BankTestData.USER_ID;
    long selectedCustomerId = 20L;

    BankUserEntity caller = BankTestData.customer("unused-hash");

    when(userDAO.findById(callerId))
            .thenReturn(Optional.of(caller));

    IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> accountService.getCustomerAccountsForEmployee(
                    callerId,
                    selectedCustomerId
            )
    );

    assertEquals(
            "Only employees can review account applications.",
            error.getMessage()
    );

    // Failed employee authorization must stop before the customer lookup.
    verify(userDAO, never()).findById(selectedCustomerId);
    verifyNoInteractions(accountDAO);
}

}
