package com.cybercafe.servlets;

import com.cybercafe.utils.CustomerManager;
import com.cybercafe.utils.DatabaseHelper;
import com.cybercafe.utils.JwtHelper;
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

        String adminId = JwtHelper.getAdminIdFromRequest(request);
        if (adminId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Unauthorized.\"}");
            return;
        }

        String personName = request.getParameter("personName");
        String paymentMethod = request.getParameter("paymentMethod");
        String narration = request.getParameter("narration");
        double amount = Double.parseDouble(request.getParameter("amount"));

        String creditAccount = "Bank A/c".equalsIgnoreCase(paymentMethod) ? "Bank A/c" : "Cashbook";
        String description = "Loan Issued: " + personName.trim() + (narration != null && !narration.isEmpty() ? " (" + narration.trim() + ")" : "");

        try (Connection conn = DatabaseHelper.getConnection()) {
            String ledgerSql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status, admin_id) " +
                               "VALUES ((SELECT account_id FROM Accounts WHERE account_name = 'Loans Given A/c'), " +
                               "(SELECT account_id FROM Accounts WHERE account_name = ?), ?, ?, 'ACTIVE', ?::uuid)";
            try (PreparedStatement stmt = conn.prepareStatement(ledgerSql)) {
                stmt.setString(1, creditAccount);
                stmt.setDouble(2, amount);
                stmt.setString(3, description);
                stmt.setString(4, adminId);
                stmt.executeUpdate();
            }

            CustomerManager.addCustomerLoan(conn, personName.trim(), amount, adminId);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"status\":\"success\",\"message\":\"Loan authorized safely in your tenant space.\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Database error.\"}");
        }
    }
}
