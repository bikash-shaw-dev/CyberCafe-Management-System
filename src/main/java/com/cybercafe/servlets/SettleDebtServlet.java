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
import java.util.Locale;

@WebServlet("/SettleDebt")
public class SettleDebtServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String customerName = request.getParameter("customerName");
        String paymentMethod = request.getParameter("paymentMethod");
        String amountStr = request.getParameter("amount");

        if (customerName == null || customerName.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Customer name is required.\"}");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                throw new NumberFormatException("Settlement amount must be greater than zero.");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Invalid settlement amount.\"}");
            return;
        }

        String debitAccount = ("Bank A/c".equalsIgnoreCase(paymentMethod)) ? "Bank A/c" : "Cashbook";

        try (Connection conn = DatabaseHelper.getConnection()) {
            // 1. Reduce trade debt in Customers table
            CustomerManager.reduceCustomerDebt(conn, customerName.trim(), amount);

            // 2. Post to General Ledger: Dr Cashbook/Bank A/c, Cr Debtors
            String ledgerSql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status) " +
                    "VALUES ((SELECT account_id FROM Accounts WHERE account_name = ?), " +
                    "(SELECT account_id FROM Accounts WHERE account_name = 'Debtors'), ?, ?, 'ACTIVE')";
            try (PreparedStatement stmt = conn.prepareStatement(ledgerSql)) {
                stmt.setString(1, debitAccount);
                stmt.setDouble(2, amount);
                stmt.setString(3, "A/R Settlement - " + customerName.trim());
                stmt.executeUpdate();
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(String.format(Locale.US,
                    "{\"status\":\"success\",\"message\":\"Receivable settled for %s.\",\"data\":{" +
                            "\"customerName\":\"%s\",\"settledAmount\":%.2f,\"paymentMethod\":\"%s\"}}",
                    CustomerManager.escapeJson(customerName.trim()),
                    CustomerManager.escapeJson(customerName.trim()),
                    amount,
                    CustomerManager.escapeJson(debitAccount)
            ));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Database error: " + CustomerManager.escapeJson(e.getMessage()) + "\"}");
        }
    }
}