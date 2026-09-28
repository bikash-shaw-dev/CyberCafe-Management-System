package com.cybercafe.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles database operations for Customers (Debtors).
 */
public class CustomerManager {

    /**
     * Searches for a customer by name. If found, returns their ID.
     * If not found, creates a new customer profile and returns the new ID.
     */
    public int getOrCreateCustomer(Connection conn, String customerName) throws SQLException {

        // Step 1: Check if the customer already exists in the database
        String findQuery = "SELECT customer_id FROM Customers WHERE name = ?";
        try (PreparedStatement findStmt = conn.prepareStatement(findQuery)) {
            findStmt.setString(1, customerName);
            try (ResultSet rs = findStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("customer_id"); // Return existing ID
                }
            }
        }

        // Step 2: Customer does not exist; insert a new record
        String insertQuery = "INSERT INTO Customers (name, outstanding_balance) VALUES (?, 0.00)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
            insertStmt.setString(1, customerName);
            insertStmt.executeUpdate();

            // Step 3: Retrieve and return the auto-generated primary key (customer_id)
            try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Failed to create customer profile; no ID returned.");
                }
            }
        }
    }

    /**
     * Increases a customer's outstanding debt when they take a service on credit (Udhar).
     */
    public void addCustomerDebt(Connection conn, int customerId, double amount) throws SQLException {
        String updateQuery = "UPDATE Customers SET outstanding_balance = outstanding_balance + ? WHERE customer_id = ?";
        try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
            updateStmt.setDouble(1, amount);
            updateStmt.setInt(2, customerId);
            updateStmt.executeUpdate();
        }
    }
}