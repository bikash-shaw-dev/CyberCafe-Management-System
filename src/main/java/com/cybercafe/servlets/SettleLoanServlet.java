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

@WebServlet("/SettleLoan")
public class SettleLoanServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String personName = request.getParameter("personName");
        String paymentMethod = request.getParameter("paymentMethod");
        String amountStr = request.getParameter("amount");

        if (personName == null || personName.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Recipient name is required.\"}");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                throw new NumberFormatException("Repayment amount must be greater than zero.");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Invalid loan repayment amount.\"}");
            return;
        }

        String debitAccount = ("Bank A/c".equalsIgnoreCase(paymentMethod)) ? "Bank A/c" : "Cashbook";

        try (Connection conn = DatabaseHelper.getConnection()) {
            // 1. Reduce loan_balance in Customers table
            CustomerManager.reduceCustomerLoan(conn, personName.trim(), amount);

            // 2. Post Repayment to General Ledger: Dr Cashbook/Bank A/c, Cr Loans Given A/c
            String ledgerSql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status) " +
                    "VALUES ((SELECT account_id FROM Accounts WHERE account_name = ?), " +
                    "(SELECT account_id FROM Accounts WHERE account_name = 'Loans Given A/c'), ?, ?, 'ACTIVE')";
            try (PreparedStatement stmt = conn.prepareStatement(ledgerSql)) {
                stmt.setString(1, debitAccount);
                stmt.setDouble(2, amount);
                stmt.setString(3, "Loan Repayment - " + personName.trim());
                stmt.executeUpdate();
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(String.format(Locale.US,
                    "{\"status\":\"success\",\"message\":\"Loan repayment recorded for %s.\",\"data\":{" +
                            "\"personName\":\"%s\",\"repaidAmount\":%.2f,\"paymentMethod\":\"%s\"}}",
                    CustomerManager.escapeJson(personName.trim()),
                    CustomerManager.escapeJson(personName.trim()),
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