package com.cybercafe.servlets;

import com.cybercafe.utils.CustomerManager;
import com.cybercafe.utils.DatabaseHelper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;

@WebServlet("/SaveSale")
public class SaveSaleServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String category = request.getParameter("category");
        String customerName = request.getParameter("customerName");
        String paymentStatus = request.getParameter("paymentStatus");
        String narration = request.getParameter("narration");
        String amountStr = request.getParameter("amount");

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                throw new NumberFormatException("Amount must be positive.");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Invalid sale amount provided.\"}");
            return;
        }

        if ("Unpaid".equalsIgnoreCase(paymentStatus) && (customerName == null || customerName.trim().isEmpty())) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Customer name is required for Accounts Receivable (A/R) transactions.\"}");
            return;
        }

        // Map payment status to the target Debit Ledger Account
        String debitAccountName;
        if ("Cash".equalsIgnoreCase(paymentStatus)) {
            debitAccountName = "Cashbook";
        } else if ("Online".equalsIgnoreCase(paymentStatus)) {
            debitAccountName = "Bank A/c";
        } else if ("Unpaid".equalsIgnoreCase(paymentStatus)) {
            debitAccountName = "Debtors";
        } else {
            debitAccountName = "Cashbook";
        }

        // Build readable ledger particulars
        StringBuilder description = new StringBuilder();
        description.append(category != null && !category.trim().isEmpty() ? category.trim() : "Service Sale");
        if (customerName != null && !customerName.trim().isEmpty()) {
            description.append(" - ").append(customerName.trim());
        }
        if (narration != null && !narration.trim().isEmpty()) {
            description.append(" (").append(narration.trim()).append(")");
        }

        try (Connection conn = DatabaseHelper.getConnection()) {
            // 1. Record Double-Entry Transaction: Dr Cashbook/Bank/Debtors, Cr Sales A/c
            String ledgerSql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status) " +
                    "VALUES ((SELECT account_id FROM Accounts WHERE account_name = ?), " +
                    "(SELECT account_id FROM Accounts WHERE account_name = 'Sales A/c'), ?, ?, 'ACTIVE')";
            try (PreparedStatement stmt = conn.prepareStatement(ledgerSql)) {
                stmt.setString(1, debitAccountName);
                stmt.setDouble(2, amount);
                stmt.setString(3, description.toString());
                stmt.executeUpdate();
            }

            // 2. If Accounts Receivable, upsert into Customers table
            if ("Unpaid".equalsIgnoreCase(paymentStatus)) {
                CustomerManager.addCustomerDebt(conn, customerName, amount);
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(
                    "{\"status\":\"success\",\"message\":\"Sale recorded and posted to ledger.\",\"data\":{" +
                            "\"category\":\"" + CustomerManager.escapeJson(category) + "\"," +
                            "\"amount\":" + String.format("%.2f", amount) + "," +
                            "\"debitAccount\":\"" + CustomerManager.escapeJson(debitAccountName) + "\"" +
                            "}}"
            );

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Database error: " + CustomerManager.escapeJson(e.getMessage()) + "\"}");
        }
    }
}