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

@WebServlet("/SettleLoan")
public class SettleLoanServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String adminId = JwtHelper.getAdminIdFromRequest(request);
        if (adminId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String personName = request.getParameter("personName");
        String paymentMethod = request.getParameter("paymentMethod");
        double amount = Double.parseDouble(request.getParameter("amount"));
        String debitAccount = "Bank A/c".equalsIgnoreCase(paymentMethod) ? "Bank A/c" : "Cashbook";

        try (Connection conn = DatabaseHelper.getConnection()) {
            CustomerManager.reduceCustomerLoan(conn, personName.trim(), amount, adminId);

            String ledgerSql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status, admin_id) " +
                               "VALUES ((SELECT account_id FROM Accounts WHERE account_name = ?), " +
                               "(SELECT account_id FROM Accounts WHERE account_name = 'Loans Given A/c'), ?, ?, 'ACTIVE', ?::uuid)";
            try (PreparedStatement stmt = conn.prepareStatement(ledgerSql)) {
                stmt.setString(1, debitAccount);
                stmt.setDouble(2, amount);
                stmt.setString(3, "Loan Repayment - " + personName.trim());
                stmt.setString(4, adminId);
                stmt.executeUpdate();
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"status\":\"success\",\"message\":\"Loan repayment recorded.\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
