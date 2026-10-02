package com.cybercafe.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class CustomerManager {

    /**
     * Adds trade debt (Accounts Receivable) to a customer using PostgreSQL ON CONFLICT upsert.
     */
    public static void addCustomerDebt(Connection conn, String customerName, double amount) throws SQLException {
        if (customerName == null || customerName.trim().isEmpty() || amount <= 0) {
            return;
        }
        String sql = "INSERT INTO Customers (name, total_debt, loan_balance) VALUES (?, ?, 0.0) " +
                "ON CONFLICT (name) DO UPDATE SET total_debt = Customers.total_debt + EXCLUDED.total_debt";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, customerName.trim());
            stmt.setDouble(2, amount);
            stmt.executeUpdate();
        }
    }

    /**
     * Reduces trade debt (Accounts Receivable) when a customer settles dues or receives a discount.
     */
    public static void reduceCustomerDebt(Connection conn, String customerName, double amount) throws SQLException {
        if (customerName == null || customerName.trim().isEmpty() || amount <= 0) {
            return;
        }
        String sql = "UPDATE Customers SET total_debt = GREATEST(0.0, total_debt - ?) WHERE name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, customerName.trim());
            stmt.executeUpdate();
        }
    }

    /**
     * Adds a non-sale cash/bank loan to a person's profile using PostgreSQL ON CONFLICT upsert.
     */
    public static void addCustomerLoan(Connection conn, String personName, double amount) throws SQLException {
        if (personName == null || personName.trim().isEmpty() || amount <= 0) {
            return;
        }
        String sql = "INSERT INTO Customers (name, total_debt, loan_balance) VALUES (?, 0.0, ?) " +
                "ON CONFLICT (name) DO UPDATE SET loan_balance = Customers.loan_balance + EXCLUDED.loan_balance";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, personName.trim());
            stmt.setDouble(2, amount);
            stmt.executeUpdate();
        }
    }

    /**
     * Reduces a person's outstanding loan balance when they repay a loan.
     */
    public static void reduceCustomerLoan(Connection conn, String personName, double amount) throws SQLException {
        if (personName == null || personName.trim().isEmpty() || amount <= 0) {
            return;
        }
        String sql = "UPDATE Customers SET loan_balance = GREATEST(0.0, loan_balance - ?) WHERE name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, personName.trim());
            stmt.executeUpdate();
        }
    }

    /**
     * Safely escapes strings for clean JSON output.
     */
    public static String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}