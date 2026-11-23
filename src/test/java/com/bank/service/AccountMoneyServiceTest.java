package com.bank.service;

import com.bank.entity.BankUserEntity;
import com.bank.repository.AccountDAO;
import com.bank.repository.UserDAO;
import com.bank.repository.dto.TransferRecipientDTO;
import com.bank.repository.exception.*;
import com.bank.support.BankTestData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.OngoingStubbing;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountMoneyServiceTest {

    // Distinct IDs help detect accidentally swapped arguments.
    private static final long SOURCE_ID = 20L;
    private static final long DESTINATION_ID = 30L;

    private static final BigDecimal AMOUNT = new BigDecimal("25.00");
    private static final BigDecimal SAVED_BALANCE = new BigDecimal("75.00");

    private AccountDAO accountDAO;
    private UserDAO userDAO;
    private AccountService service;

    enum Operation {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER
    }

    @BeforeEach
    void setUp() {
        accountDAO = mock(AccountDAO.class);
        userDAO = mock(UserDAO.class);
        service = new AccountService(accountDAO, userDAO);
    }

    @ParameterizedTest(name = "{0}: accepts amount {1}")
    @MethodSource("validAmountCases")
    void returnsSavedBalanceAndPassesNormalizedAmount(
            Operation operation,
            String enteredAmount,
            String normalizedAmount
    ) throws SQLException {
        // Arrange
        allowCustomer();
        BigDecimal expectedAmount = new BigDecimal(normalizedAmount);

        stubMoneyOperation(operation, expectedAmount)
                .thenReturn(SAVED_BALANCE);

        // Act
        BigDecimal result = invokeMoneyOperation(
                operation,
                BankTestData.USER_ID,
                SOURCE_ID,
                DESTINATION_ID,
                new BigDecimal(enteredAmount)
        );

        // Assert
        assertEquals(SAVED_BALANCE, result);
        verifyMoneyOperation(operation, expectedAmount);
        verifyNoMoreInteractions(accountDAO);
    }

    static Stream<Arguments> validAmountCases() {
        return Stream.of(Operation.values())
                .flatMap(operation -> Stream.of(
                        Arguments.of(operation, "25", "25.00"),
                        Arguments.of(operation, "25.500", "25.50"),
                        Arguments.of(operation, "0.01", "0.01"),
                        Arguments.of(
                                operation,
                                "9999999999999.99",
                                "9999999999999.99"
                        )
                ));
    }

    @ParameterizedTest(name = "{0}: rejects amount {1}")
    @MethodSource("invalidAmountCases")
    void rejectsInvalidAmountsBeforeCallingDAOs(
            Operation operation,
            BigDecimal amount,
            String expectedMessage
    ) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> invokeMoneyOperation(
                        operation,
                        BankTestData.USER_ID,
                        SOURCE_ID,
                        DESTINATION_ID,
                        amount
                )
        );

        assertEquals(expectedMessage, error.getMessage());
        verifyNoInteractions(userDAO, accountDAO);
    }

    static Stream<Arguments> invalidAmountCases() {
        return Stream.of(Operation.values())
                .flatMap(operation -> {
                    String label = operationLabel(operation);

                    return Stream.of(
                            Arguments.of(
                                    operation,
                                    null,
                                    label + " amount must be greater than zero."
                            ),
                            Arguments.of(
                                    operation,
                                    BigDecimal.ZERO,
                                    label + " amount must be greater than zero."
                            ),
                            Arguments.of(
                                    operation,
                                    new BigDecimal("-1.00"),
                                    label + " amount must be greater than zero."
                            ),
                            Arguments.of(
                                    operation,
                                    new BigDecimal("0.001"),
                                    label + " amount must not contain fractions of a cent."
                            ),
                            Arguments.of(
                                    operation,
                                    new BigDecimal("10000000000000.00"),
                                    label + " amount exceeds the supported limit."
                            )
                    );
                });
    }

    @ParameterizedTest(name = "{0}: customer={1}, source={2}, destination={3}")
    @MethodSource("invalidIdCases")
    void rejectsInvalidIdsBeforeCallingDAOs(
            Operation operation,
            long customerId,
            long sourceId,
            long destinationId,
            String expectedMessage
    ) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> invokeMoneyOperation(
                        operation,
                        customerId,
                        sourceId,
                        destinationId,
                        AMOUNT
                )
        );

        assertEquals(expectedMessage, error.getMessage());
        verifyNoInteractions(userDAO, accountDAO);
    }

    static Stream<Arguments> invalidIdCases() {
        return Stream.of(Operation.values())
                .flatMap(operation -> Stream.of(0L, -1L)
                        .flatMap(invalidId -> {
                            Stream<Arguments> commonCases = Stream.of(
                                    Arguments.of(
                                            operation,
                                            invalidId,
                                            SOURCE_ID,
                                            DESTINATION_ID,
                                            "Customer ID must be greater than zero."
                                    ),
                                    Arguments.of(
                                            operation,
                                            BankTestData.USER_ID,
                                            invalidId,
                                            DESTINATION_ID,
                                            operation == Operation.TRANSFER
                                                    ? "Source and destination account IDs must be greater than zero."
                                                    : "Account ID must be greater than zero."
                                    )
                            );

                            if (operation != Operation.TRANSFER) {
                                return commonCases;
                            }

                            return Stream.concat(
                                    commonCases,
                                    Stream.of(Arguments.of(
                                            operation,
                                            BankTestData.USER_ID,
                                            SOURCE_ID,
                                            invalidId,
                                            "Source and destination account IDs must be greater than zero."
                                    ))
                            );
                        }));
    }

    @Test
    void transferRejectsSameSourceAndDestination() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.transfer(
                        BankTestData.USER_ID,
                        SOURCE_ID,
                        SOURCE_ID,
                        AMOUNT
                )
        );

        assertEquals(
                "Choose a different destination account.",
                error.getMessage()
        );
        verifyNoInteractions(userDAO, accountDAO);
    }

    @ParameterizedTest(name = "{0}: customer exists={1}")
    @MethodSource("unauthorizedCustomerCases")
    void rejectsMissingCustomersAndEmployees(
            Operation operation,
            boolean customerExists
    ) throws SQLException {
        if (customerExists) {
            BankUserEntity employee = BankTestData.customer("unused-hash");
            employee.setRole("EMPLOYEE");

            when(userDAO.findById(BankTestData.USER_ID))
                    .thenReturn(Optional.of(employee));
        } else {
            when(userDAO.findById(BankTestData.USER_ID))
                    .thenReturn(Optional.empty());
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> invokeMoneyOperation(
                        operation,
                        BankTestData.USER_ID,
                        SOURCE_ID,
                        DESTINATION_ID,
                        AMOUNT
                )
        );

        String expectedMessage;

        if (!customerExists) {
            expectedMessage = "Customer not found.";
        } else if (operation == Operation.DEPOSIT) {
            expectedMessage = "Only customers can deposit into their accounts.";
        } else {
            expectedMessage =
                    "Only customers can perform these account transactions.";
        }

        assertEquals(expectedMessage, error.getMessage());
        verifyNoInteractions(accountDAO);
    }

    static Stream<Arguments> unauthorizedCustomerCases() {
        return Stream.of(Operation.values())
                .flatMap(operation -> Stream.of(
                        Arguments.of(operation, false),
                        Arguments.of(operation, true)
                ));
    }

    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("rejectionCases")
    void translatesDAORejections(
            Operation operation,
            RuntimeException rejection,
            String expectedMessage
    ) throws SQLException {
        allowCustomer();
        stubMoneyOperation(operation, AMOUNT).thenThrow(rejection);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> invokeMoneyOperation(
                        operation,
                        BankTestData.USER_ID,
                        SOURCE_ID,
                        DESTINATION_ID,
                        AMOUNT
                )
        );

        assertEquals(expectedMessage, error.getMessage());
        assertSame(rejection, error.getCause());
        verifyMoneyOperation(operation, AMOUNT);
    }

    static Stream<Arguments> rejectionCases() {
        return Stream.of(
                Arguments.of(
                        Operation.DEPOSIT,
                        new AccountUnavailableException(),
                        "The selected account is unavailable or does not belong to you."
                ),
                Arguments.of(
                        Operation.DEPOSIT,
                        new AccountNotApprovedException(),
                        "You can deposit only into an approved account."
                ),
                Arguments.of(
                        Operation.DEPOSIT,
                        new BalanceLimitExceededException(),
                        "This deposit would exceed the maximum account balance."
                ),
                Arguments.of(
                        Operation.WITHDRAWAL,
                        new AccountUnavailableException(),
                        "The selected account is unavailable or does not belong to you."
                ),
                Arguments.of(
                        Operation.WITHDRAWAL,
                        new AccountNotApprovedException(),
                        "You can withdraw only from an approved account."
                ),
                Arguments.of(
                        Operation.WITHDRAWAL,
                        new InsufficientFundsException(),
                        "Your account does not have enough money for this withdrawal."
                ),
                Arguments.of(
                        Operation.TRANSFER,
                        new AccountUnavailableException(),
                        "The source account is unavailable or does not belong to you."
                ),
                Arguments.of(
                        Operation.TRANSFER,
                        new DestinationAccountUnavailableException(),
                        "The destination account could not be found."
                ),
                Arguments.of(
                        Operation.TRANSFER,
                        new AccountNotApprovedException(),
                        "Both accounts must be approved before you can transfer money."
                ),
                Arguments.of(
                        Operation.TRANSFER,
                        new InsufficientFundsException(),
                        "Your source account does not have enough money for this transfer."
                ),
                Arguments.of(
                        Operation.TRANSFER,
                        new BalanceLimitExceededException(),
                        "This transfer would exceed the destination account's maximum balance."
                )
        );
    }

    @ParameterizedTest(name = "{0}: failure during account operation={1}")
    @MethodSource("databaseFailureCases")
    void propagatesDatabaseFailures(
            Operation operation,
            boolean failureDuringAccountOperation
    ) throws SQLException {
        SQLException failure = new SQLException(
                "Connection lost.",
                "08006"
        );

        if (failureDuringAccountOperation) {
            allowCustomer();
            stubMoneyOperation(operation, AMOUNT).thenThrow(failure);
        } else {
            when(userDAO.findById(BankTestData.USER_ID))
                    .thenThrow(failure);
        }

        SQLException error = assertThrows(
                SQLException.class,
                () -> invokeMoneyOperation(
                        operation,
                        BankTestData.USER_ID,
                        SOURCE_ID,
                        DESTINATION_ID,
                        AMOUNT
                )
        );

        assertSame(failure, error);

        if (failureDuringAccountOperation) {
            verifyMoneyOperation(operation, AMOUNT);
        } else {
            verifyNoInteractions(accountDAO);
        }
    }

    static Stream<Arguments> databaseFailureCases() {
        return Stream.of(Operation.values())
                .flatMap(operation -> Stream.of(
                        Arguments.of(operation, false),
                        Arguments.of(operation, true)
                ));
    }

    @Test
    void recipientLookupReturnsDisplayDetails() throws SQLException {
        allowCustomer();

        TransferRecipientDTO recipient = new TransferRecipientDTO(
                DESTINATION_ID,
                "Maya Smith (@maya)"
        );

        when(accountDAO.findTransferRecipient(DESTINATION_ID))
                .thenReturn(Optional.of(recipient));

        TransferRecipientDTO result = service.getTransferRecipient(
                BankTestData.USER_ID,
                DESTINATION_ID
        );

        assertEquals(DESTINATION_ID, result.getAccountId());
        assertEquals("Maya Smith (@maya)", result.getOwnerLabel());

        // Looking up a recipient must not perform a money operation.
        verify(accountDAO).findTransferRecipient(DESTINATION_ID);
        verifyNoMoreInteractions(accountDAO);
    }

    @Test
    void recipientLookupRejectsUnavailableDestination() throws SQLException {
        allowCustomer();

        when(accountDAO.findTransferRecipient(DESTINATION_ID))
                .thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.getTransferRecipient(
                        BankTestData.USER_ID,
                        DESTINATION_ID
                )
        );

        assertEquals(
                "The destination account is unavailable or is not approved.",
                error.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void recipientLookupRejectsInvalidDestination(long destinationId)
            throws SQLException {
        // This service method authorizes the customer before checking the ID.
        allowCustomer();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.getTransferRecipient(
                        BankTestData.USER_ID,
                        destinationId
                )
        );

        assertEquals(
                "Destination account number must be greater than zero.",
                error.getMessage()
        );
        verifyNoInteractions(accountDAO);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void recipientLookupRejectsInvalidCustomerId(long customerId) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.getTransferRecipient(
                        customerId,
                        DESTINATION_ID
                )
        );

        assertEquals(
                "Customer ID must be greater than zero.",
                error.getMessage()
        );
        verifyNoInteractions(userDAO, accountDAO);
    }

    @ParameterizedTest(name = "Customer exists: {0}")
    @ValueSource(booleans = {false, true})
    void recipientLookupRejectsMissingCustomerOrEmployee(boolean exists)
            throws SQLException {
        if (exists) {
            BankUserEntity employee = BankTestData.customer("unused-hash");
            employee.setRole("EMPLOYEE");

            when(userDAO.findById(BankTestData.USER_ID))
                    .thenReturn(Optional.of(employee));
        } else {
            when(userDAO.findById(BankTestData.USER_ID))
                    .thenReturn(Optional.empty());
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.getTransferRecipient(
                        BankTestData.USER_ID,
                        DESTINATION_ID
                )
        );

        assertEquals(
                exists
                        ? "Only customers can perform these account transactions."
                        : "Customer not found.",
                error.getMessage()
        );
        verifyNoInteractions(accountDAO);
    }

    @ParameterizedTest(name = "Failure during recipient query: {0}")
    @ValueSource(booleans = {false, true})
    void recipientLookupPropagatesDatabaseFailures(boolean recipientQuery)
            throws SQLException {
        SQLException failure = new SQLException(
                "Connection lost.",
                "08006"
        );

        if (recipientQuery) {
            allowCustomer();

            when(accountDAO.findTransferRecipient(DESTINATION_ID))
                    .thenThrow(failure);
        } else {
            when(userDAO.findById(BankTestData.USER_ID))
                    .thenThrow(failure);
        }

        SQLException error = assertThrows(
                SQLException.class,
                () -> service.getTransferRecipient(
                        BankTestData.USER_ID,
                        DESTINATION_ID
                )
        );

        assertSame(failure, error);

        if (!recipientQuery) {
            verifyNoInteractions(accountDAO);
        }
    }

    private void allowCustomer() throws SQLException {
        when(userDAO.findById(BankTestData.USER_ID))
                .thenReturn(Optional.of(
                        BankTestData.customer("unused-hash")
                ));
    }

    // Dispatch helpers let related scenarios share the same assertions.
    private BigDecimal invokeMoneyOperation(
            Operation operation,
            long customerId,
            long sourceId,
            long destinationId,
            BigDecimal amount
    ) throws SQLException {
        switch (operation) {
            case DEPOSIT:
                return service.deposit(customerId, sourceId, amount);
            case WITHDRAWAL:
                return service.withdraw(customerId, sourceId, amount);
            case TRANSFER:
                return service.transfer(
                        customerId, sourceId, destinationId, amount
                );
            default:
                throw new AssertionError("Unexpected operation: " + operation);
        }
    }

    private OngoingStubbing<BigDecimal> stubMoneyOperation(
            Operation operation,
            BigDecimal amount
    ) throws SQLException {
        switch (operation) {
            case DEPOSIT:
                return when(accountDAO.deposit(
                        SOURCE_ID, BankTestData.USER_ID, amount
                ));
            case WITHDRAWAL:
                return when(accountDAO.withdraw(
                        SOURCE_ID, BankTestData.USER_ID, amount
                ));
            case TRANSFER:
                return when(accountDAO.transfer(
                        SOURCE_ID, DESTINATION_ID, BankTestData.USER_ID, amount
                ));
            default:
                throw new AssertionError("Unexpected operation: " + operation);
        }
    }

    private void verifyMoneyOperation(
            Operation operation,
            BigDecimal amount
    ) throws SQLException {
        switch (operation) {
            case DEPOSIT:
                verify(accountDAO).deposit(
                        SOURCE_ID, BankTestData.USER_ID, amount
                );
                break;
            case WITHDRAWAL:
                verify(accountDAO).withdraw(
                        SOURCE_ID, BankTestData.USER_ID, amount
                );
                break;
            case TRANSFER:
                verify(accountDAO).transfer(
                        SOURCE_ID, DESTINATION_ID, BankTestData.USER_ID, amount
                );
                break;
            default:
                throw new AssertionError("Unexpected operation: " + operation);
        }
    }

    private static String operationLabel(Operation operation) {
        switch (operation) {
            case DEPOSIT:
                return "Deposit";
            case WITHDRAWAL:
                return "Withdrawal";
            case TRANSFER:
                return "Transfer";
            default:
                throw new AssertionError("Unexpected operation: " + operation);
        }
    }
}