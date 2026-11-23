package com.bank.repository;

import com.bank.entity.AccountEntity;
import com.bank.repository.exception.*;
import com.bank.util.ConnectionUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.bank.repository.dto.TransferRecipientDTO;
import java.util.Optional;

public class AccountDAO {

    private static final BigDecimal MAX_BALANCE = new BigDecimal("9999999999999.99");

    public AccountEntity createApplication(long customerId)
            throws SQLException {
        String accountSql =
                "insert into accounts default values "
                + "returning account_id, balance, status, applied_at, reviewed_by, reviewed_at";

        String ownerSql =
                "insert into account_owners (account_id, user_id) "
                + "values (?, ?)";

//        Selecting user_id is just a lightweight way to lock the customer record before performing further operations.
        String lockCustomerSql =
                "select user_id from bank_users "
                + "where user_id = ? for update";

        String pendingSql =
                "select 1 "
                + "from accounts a "
                + "join account_owners ao on ao.account_id = a.account_id "
                + "where ao.user_id = ? and a.status = 'PENDING' "
                + "limit 1";

        try (Connection connection = ConnectionUtil.getConnection()) {
            // Keep both inserts in the same transaction.
            connection.setAutoCommit(false);

            try {

//                Serialize application requests for the same customer until commit or rolllback.
                try
                (
                    PreparedStatement statement = connection.prepareStatement(lockCustomerSql)
                ){
                    statement.setLong(1, customerId);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SQLException(
                                    "The customer no longer exists."
                            );
                        }
                    }
                }

//                Check only after acquiring the customer lock.
                try(
                    PreparedStatement statement = connection.prepareStatement(pendingSql)
                ) {
                    statement.setLong(1, customerId);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            throw new PendingApplicationException();
                        }
                    }
                }
                AccountEntity account;

                try
                (
                    PreparedStatement statement = connection.prepareStatement(accountSql);
                    ResultSet resultSet = statement.executeQuery()
                ) {
                    if (!resultSet.next()) {
                        throw new SQLException(
                                "Creating the application returned no row."
                        );
                    }

                    account = new AccountEntity(
                            resultSet.getLong("account_id"),
                            resultSet.getBigDecimal("balance"),
                            resultSet.getString("status"),
                            resultSet.getObject(
                                    "applied_at",
                                    OffsetDateTime.class
                            ),
                            resultSet.getObject("reviewed_by", Long.class),
                            resultSet.getObject(
                                    "reviewed_at",
                                    OffsetDateTime.class
                            )
                    );
                }

                try
                (
                    PreparedStatement statement = connection.prepareStatement(ownerSql)
                ) {
                    statement.setLong(1, account.getAccountId());
                    statement.setLong(2, customerId);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "Creating the account ownership failed."
                        );
                    }
                }

                connection.commit();
                return account;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    // Preserve the original failure and the rollback failure
                    e.addSuppressed(rollbackError);
                }

                throw e;
            }
        }
    }

    public List<AccountEntity> findByOwnerId(long customerId)
        throws SQLException {

        String sql =
                "select a.account_id, a.balance, a.status, a.applied_at, "
                + "a.reviewed_by, a.reviewed_at "
                + "from accounts a "
                + "join account_owners ao on ao.account_id = a.account_id "
                + "where ao.user_id = ? "
                + "order by a.account_id";

        List<AccountEntity> accounts = new ArrayList<>();

        try (
            Connection connection = ConnectionUtil.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, customerId);

            try (ResultSet resultSet = statement.executeQuery()) {
//                A customer can own multiple accounts including shared accounts.
                while (resultSet.next()) {
                    AccountEntity account = new AccountEntity(
                            resultSet.getLong("account_id"),
                            resultSet.getBigDecimal("balance"),
                            resultSet.getString("status"),
                            resultSet.getObject(
                                    "applied_at",
                                    OffsetDateTime.class
                            ),
                            resultSet.getObject("reviewed_by", Long.class),
                            resultSet.getObject(
                                    "reviewed_at",
                                    OffsetDateTime.class
                            )
                    );

                    accounts.add(account);
                }

            }
        }

        return accounts;
    }

    public List<AccountEntity> findPendingApplications()
        throws SQLException {

        String sql =
                "select account_id, balance, status, applied_at, "
                + "reviewed_by, reviewed_at "
                + "from accounts "
                + "where status = 'PENDING' "
                + "order by applied_at, account_id";

        List<AccountEntity> applications = new ArrayList<>();

        try
        (
                Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                AccountEntity account = new AccountEntity(
                        resultSet.getLong("account_id"),
                        resultSet.getBigDecimal("balance"),
                        resultSet.getString("status"),
                        resultSet.getObject("applied_at", OffsetDateTime.class),
                        resultSet.getObject("reviewed_by", Long.class),
                        resultSet.getObject("reviewed_at", OffsetDateTime.class)
                );

                applications.add(account);
            }
        }

        return applications;
    }

    public boolean reviewApplication(
            long accountId,
            long employeeId,
            boolean approved
    ) throws SQLException {
        String sql =
                "update accounts "
                + "set status = ?, reviewed_by = ?, "
                + "reviewed_at = current_timestamp "
                + "where account_id = ? and status = 'PENDING'";

        try
        (
                Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, approved ? "APPROVED" : "DENIED");
            statement.setLong(2, employeeId);
            statement.setLong(3, accountId);

//            Only a still-pending application can receive a decision
            return statement.executeUpdate() == 1;
        }
    }

    public BigDecimal deposit(
            long accountId,
            long customerId,
            BigDecimal amount
    ) throws SQLException {
//        Protect the stored balance even if this DAO is called directly.
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Deposit amount must be greater than zero."
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

        String accountSql =
                "select a.balance, a.status "
                + "from accounts a "
                + "where a.account_id = ? "
                + "and exists ("
                + "select 1 from account_owners ao "
                + "where ao.account_id = a.account_id and ao.user_id = ?) "
                + "for update of a";

        String balanceSql =
                "update accounts set balance = ? where account_id = ?";

        String transactionSql =
                "insert into bank_transactions "
                + "(transaction_type, amount, destination_account_id, performed_by) "
                + "values ('DEPOSIT', ?, ?, ?)";

        try (Connection connection = ConnectionUtil.getConnection()) {
            connection.setAutoCommit(false);

            try {
                BigDecimal updatedBalance;

//                Hold the account lock through validation, update, and commit.

                try (PreparedStatement statement =
                        connection.prepareStatement(accountSql)) {
                    statement.setLong(1, accountId);
                    statement.setLong(2, customerId);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new AccountUnavailableException();
                        }

                        if (!"APPROVED".equals(resultSet.getString("status"))) {
                            throw new AccountNotApprovedException();
                        }

                        BigDecimal currentBalance = resultSet.getBigDecimal("balance");

                        updatedBalance = currentBalance.add(depositAmount);

                        if (updatedBalance.compareTo(MAX_BALANCE) > 0) {
                            throw new BalanceLimitExceededException();
                        }
                    }
                }

                try (PreparedStatement statement =
                        connection.prepareStatement(balanceSql)) {

                    statement.setBigDecimal(1, updatedBalance);
                    statement.setLong(2, accountId);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "Updating the account balance failed."
                        );
                    }
                }

                try (PreparedStatement statement =
                        connection.prepareStatement(transactionSql)) {
                    statement.setBigDecimal(1, depositAmount);
                    statement.setLong(2, accountId);
                    statement.setLong(3, customerId);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "Recording the deposit failed."
                        );
                    }
                }

//                Commit only after both the balace and history are saved.
                connection.commit();
                return updatedBalance;

            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    public BigDecimal withdraw(
            long accountId,
            long customerId,
            BigDecimal amount
    ) throws SQLException {
        return executeDebit(accountId, customerId, null, amount);
    }

    public BigDecimal transfer(
            long sourceAccountId,
            long destinationAccountId,
            long customerId,
            BigDecimal amount
    ) throws SQLException {
        if (sourceAccountId == destinationAccountId) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different."
            );
        }
        return executeDebit(sourceAccountId, customerId, destinationAccountId, amount);
    }

    private BigDecimal executeDebit(
            long sourceAccountId,
            long customerId,
            Long destinationAccountId,
            BigDecimal amount
    ) throws SQLException {
        if (sourceAccountId <= 0 || customerId <= 0 || (destinationAccountId != null && destinationAccountId <= 0)) {
            throw new IllegalArgumentException(
                    "Account and customer IDs must be greater than zero."
            );
        }

//            Defensive validation also protects direct DAO calls.
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero."
            );
        }

        BigDecimal debitAmount;

        try {
            debitAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                "Amount must not contain fractions of a cent.",
                e
            );
        }

        if (debitAmount.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException(
                    "Amount exceeds the supported limit."
            );
        }

        String ownershipSql =
                "select 1 from account_owners "
                + "where account_id = ? and user_id = ?";

        String transactionSql =
                "insert into bank_transactions "
                + "(transaction_type, amount, source_account_id, "
                + "destination_account_id, performed_by) "
                + "values (?, ?, ?, ?, ?)";

        try (Connection connection = ConnectionUtil.getConnection()) {
            connection.setAutoCommit(false);

            try {
                AccountEntity source;
                AccountEntity destination = null;

//                    Every transfer locks accounts in ascending ID order.
//                    Opposite-direction transfers therefore use the same lock order.
                if (destinationAccountId != null
                    && destinationAccountId < sourceAccountId) {
                    destination = lockAccount(connection, destinationAccountId);
                    source = lockAccount(connection, sourceAccountId);
                } else {
                    source = lockAccount(connection, sourceAccountId);

                    if (destinationAccountId != null) {
                        destination = lockAccount(connection, destinationAccountId);
                    }
                }

                if (source == null) {
                    throw new AccountUnavailableException();
                }

//                    Only the owner of the source account may spend its money.

                try (PreparedStatement statement = connection.prepareStatement(ownershipSql)){
                    statement.setLong(1, sourceAccountId);
                    statement.setLong(2, customerId);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new AccountUnavailableException();
                        }
                    }
                }

                if (destinationAccountId != null && destination == null) {
                    throw new DestinationAccountUnavailableException();
                }

                if (!"APPROVED".equals(source.getStatus())
                    || (destination != null
                    && !"APPROVED".equals(destination.getStatus()))) {
                    throw new AccountNotApprovedException();
                }

                if (source.getBalance().compareTo(debitAmount) < 0) {
                    throw new InsufficientFundsException();
                }

                BigDecimal sourceBalance =
                        source.getBalance().subtract(debitAmount);

                BigDecimal destinationBalance = null;

                if (destination != null) {
                    destinationBalance = destination.getBalance().add(debitAmount);

                    if (destinationBalance.compareTo(MAX_BALANCE) > 0) {
                        throw new BalanceLimitExceededException();
                    }
                }

                updateBalance(connection, sourceAccountId, sourceBalance);

                if (destinationAccountId != null) {
                    updateBalance(connection, destinationAccountId, destinationBalance);
                }

                try (PreparedStatement statement = connection.prepareStatement(transactionSql)) {
                    statement.setString(
                            1,
                            destinationAccountId == null
                            ? "WITHDRAWAL"
                                    : "TRANSFER"
                    );
                    statement.setBigDecimal(2, debitAmount);
                    statement.setLong(3, sourceAccountId);

                    if (destinationAccountId == null) {
                        statement.setNull(4, Types.BIGINT);
                    } else {
                        statement.setLong(4, destinationAccountId);
                    }

                    statement.setLong(5, customerId);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "Recording the transaction failed."
                        );
                    }
                }

                //                balance changes and transaction history succeed together.
                connection.commit();
                return sourceBalance;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                throw e;
            }
        }
    }

    private AccountEntity lockAccount(
            Connection connection,
            long accountId
    ) throws SQLException {
        String sql =
                "select account_id, balance, status "
                + "from accounts "
                + "where account_id = ? "
                + "for update";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, accountId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()){
                    return null;
                }

                AccountEntity account = new AccountEntity();
                account.setAccountId(resultSet.getLong("account_id"));
                account.setBalance(resultSet.getBigDecimal("balance"));
                account.setStatus(resultSet.getString("status"));

                return account;
            }
        }
    }

    private void updateBalance(
            Connection connection,
            long accountId,
            BigDecimal balance
    ) throws SQLException {
        String sql =
                "update accounts set balance = ? where account_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setBigDecimal(1, balance);
            statement.setLong(2, accountId);

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "Updating the account balance failed."
                );
            }
        }
    }

    public Optional<TransferRecipientDTO> findTransferRecipient(long accountId)
            throws SQLException {
        String sql =
                "select a.account_id, "
                        + "string_agg("
                        + "u.first_name || ' ' || u.last_name "
                        + "|| ' (@' || u.username || ')', "
                        + "', ' order by u.user_id) as owner_label "
                        + "from accounts a "
                        + "join account_owners ao on ao.account_id = a.account_id "
                        + "join bank_users u on u.user_id = ao.user_id "
                        + "where a.account_id = ? and a.status = 'APPROVED' "
                        + "group by a.account_id";

        try (Connection connection = ConnectionUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, accountId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new TransferRecipientDTO(
                        resultSet.getLong("account_id"),
                        resultSet.getString("owner_label")
                ));
            }
        }
    }

}
