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

@WebServlet("/IssueLoan")
public class IssueLoanServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String personName = request.getParameter("personName");
        String paymentMethod = request.getParameter("paymentMethod");
        String narration = request.getParameter("narration");
        String amountStr = request.getParameter("amount");

        if (personName == null || personName.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Recipient name is required to issue a loan.\"}");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                throw new NumberFormatException("Loan amount must be greater than zero.");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Invalid loan amount.\"}");
            return;
        }

        String creditAccount = ("Bank A/c".equalsIgnoreCase(paymentMethod)) ? "Bank A/c" : "Cashbook";

        String description = "Loan Issued: " + personName.trim();
        if (narration != null && !narration.trim().isEmpty()) {
            description += " (" + narration.trim() + ")";
        }

        try (Connection conn = DatabaseHelper.getConnection()) {
            // 1. Post to General Ledger: Dr Loans Given A/c, Cr Cashbook/Bank A/c
            String ledgerSql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status) " +
                    "VALUES ((SELECT account_id FROM Accounts WHERE account_name = 'Loans Given A/c'), " +
                    "(SELECT account_id FROM Accounts WHERE account_name = ?), ?, ?, 'ACTIVE')";
            try (PreparedStatement stmt = conn.prepareStatement(ledgerSql)) {
                stmt.setString(1, creditAccount);
                stmt.setDouble(2, amount);
                stmt.setString(3, description);
                stmt.executeUpdate();
            }

            // 2. Upsert into Customers.loan_balance (strictly isolated from total_debt)
            CustomerManager.addCustomerLoan(conn, personName.trim(), amount);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(
                    "{\"status\":\"success\",\"message\":\"Loan authorized and posted to ledger.\",\"data\":{" +
                            "\"personName\":\"" + CustomerManager.escapeJson(personName.trim()) + "\"," +
                            "\"amount\":" + String.format("%.2f", amount) + "," +
                            "\"paymentMethod\":\"" + CustomerManager.escapeJson(creditAccount) + "\"" +
                            "}}"
            );

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Database error: " + CustomerManager.escapeJson(e.getMessage()) + "\"}");
        }
    }
}