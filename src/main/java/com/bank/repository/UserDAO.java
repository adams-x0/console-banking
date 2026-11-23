package com.bank.repository;

import com.bank.entity.BankUserEntity;
import com.bank.util.ConnectionUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class UserDAO {


    // CREATE
    public BankUserEntity createCustomer(BankUserEntity user)
        throws SQLException {

        String sql =
                "insert into bank_users "
                + "(username, password_hash, first_name, last_name, email, role) "
                + "values (?, ?, ?, ?, ?, 'CUSTOMER') "
                + "returning user_id, role, created_at";

        try (Connection connection = ConnectionUtil.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, user.getFirstName());
            statement.setString(4, user.getLastName());
            statement.setString(5, user.getEmail());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "Creating the customer returned no row."
                    );
                }

                long userId = resultSet.getLong("user_id");
                String role = resultSet.getString("role");
                OffsetDateTime createdAt = resultSet.getObject(
                        "created_at",
                        OffsetDateTime.class
                );

                user.setUserId(userId);
                user.setRole(role);
                user.setCreatedAt(createdAt);

                return user;
            }
        }

    }

    // READ
    public Optional<BankUserEntity> findByUsername(String username)
            throws SQLException {
        String sql =
                "select user_id, username, password_hash, first_name, "
                        + "last_name, email, role, created_at "
                        + "from bank_users where username = ?";

        try (Connection connection = ConnectionUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    BankUserEntity user = new BankUserEntity(
                            resultSet.getLong("user_id"),
                            resultSet.getString("username"),
                            resultSet.getString("password_hash"),
                            resultSet.getString("first_name"),
                            resultSet.getString("last_name"),
                            resultSet.getString("email"),
                            resultSet.getString("role"),
                            resultSet.getObject("created_at", OffsetDateTime.class
                            )
                    );

                    return Optional.of(user);
                }

                return Optional.empty();
            }
        }
    }

    public Optional<BankUserEntity> findById(long userId)
            throws SQLException {
        String sql =
                "select user_id, username, password_hash, first_name, "
                        + "last_name, email, role, created_at "
                        + "from bank_users where user_id = ?";

        try (Connection connection = ConnectionUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    BankUserEntity user = new BankUserEntity(
                            resultSet.getLong("user_id"),
                            resultSet.getString("username"),
                            resultSet.getString("password_hash"),
                            resultSet.getString("first_name"),
                            resultSet.getString("last_name"),
                            resultSet.getString("email"),
                            resultSet.getString("role"),
                            resultSet.getObject("created_at", OffsetDateTime.class
                            )
                    );

                    return Optional.of(user);
                }

                return Optional.empty();
            }
        }
    }


    //UPDATE
    public boolean updateProfile(BankUserEntity user)
        throws SQLException {
        String sql =
                "update bank_users "
                + "set first_name = ?, last_name = ?, email = ? "
                + "where user_id = ?";

        try
        (
                Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, user.getFirstName());
            statement.setString(2, user.getLastName());
            statement.setString(3, user.getEmail());
            statement.setLong(4, user.getUserId());

            return statement.executeUpdate() == 1;
        }
    }

    // DELETE
    public boolean deleteById(long userId) throws SQLException {
        String sql = "DELETE from bank_users where user_id = ?";

        try
        (
            Connection connection = ConnectionUtil.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, userId);

            return statement.executeUpdate() == 1;
        }
    }

    public List<BankUserEntity> findAllCustomers() throws SQLException {
        String sql =
                "select user_id, username, password_hash, first_name, "
                + "last_name, email, role, created_at "
                + "from bank_users "
                + "where role = 'CUSTOMER' "
                + "order by last_name, first_name, user_id";

        List<BankUserEntity> customers = new ArrayList<>();

        try (Connection connection = ConnectionUtil.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                BankUserEntity customer = new BankUserEntity(
                        resultSet.getLong("user_id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("first_name"),
                        resultSet.getString("last_name"),
                        resultSet.getString("email"),
                        resultSet.getString("role"),
                        resultSet.getObject("created_at", OffsetDateTime.class

                        )
                );

                customers.add(customer);
            }
        }

        return customers;
    }

}
