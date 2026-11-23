package com.bank.util;

import java.sql.Connection;
import java.sql.SQLException;

import org.postgresql.ds.PGSimpleDataSource;

//final class → cannot be extended
//private constructor → cannot be instantiated
//
//Together they enforce:
//
//        "This class exists only as a container for static utility methods."
public final class ConnectionUtil {

    private static final PGSimpleDataSource dataSource = new PGSimpleDataSource();

    static {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");


        if (url == null || username == null || password == null) {
            throw new IllegalStateException(
                    "DB_URL, DB_USERNAME, and DB_PASSWORD must be set."
            );
        }

        dataSource.setURL(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);

    }

    private ConnectionUtil() {
    }

    /**
     * @return an active connection to the database
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
