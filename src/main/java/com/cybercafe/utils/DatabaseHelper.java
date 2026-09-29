package com.cybercafe.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utility class responsible for managing the connection to the Supabase PostgreSQL cloud database.
 */
public class DatabaseHelper {

    // Reads the hidden URL from your server environment instead of hardcoding it
    private static final String URL = System.getenv("SUPABASE_DB_URL");

    public static Connection getConnection() {
        Connection conn = null;
        try {
            if (URL == null || URL.isEmpty()) {
                System.err.println("ERROR: SUPABASE_DB_URL environment variable is missing!");
                return null;
            }

            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(URL);

        } catch (ClassNotFoundException e) {
            System.err.println("Error: PostgreSQL JDBC Driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Error: Could not connect to Supabase Cloud Database!");
            e.printStackTrace();
        }
        return conn;
    }
}