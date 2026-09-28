package com.cybercafe.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utility class responsible for managing the connection to the local MySQL database.
 */
public class DatabaseHelper {

    // Default XAMPP MySQL connection parameters
    private static final String URL = "jdbc:mysql://localhost:3306/cybercafe_db";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    /**
     * Establishes and returns a connection to the MySQL database.
     * @return Connection object, or null if connection fails.
     */
    public static Connection getConnection() {
        Connection conn = null;
        try {
            // Load the MySQL JDBC driver into memory
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Open the connection to the database
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            System.err.println("Error: MySQL JDBC Driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Error: Could not connect to cybercafe_db! Is XAMPP MySQL running?");
            e.printStackTrace();
        }
        return conn;
    }
}