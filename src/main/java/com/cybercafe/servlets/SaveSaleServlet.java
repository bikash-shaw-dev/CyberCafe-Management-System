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

@WebServlet("/SaveSale")
public class SaveSaleServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // MULTI-TENANT SECURITY CHECK
        String adminId = JwtHelper.getAdminIdFromRequest(request);
        if (adminId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Unauthorized. Invalid or missing authentication token.\"}");
            return;
        }

        String category = request.getParameter("category");
        String customerName = request.getParameter("customerName");
        String paymentStatus = request.getParameter("paymentStatus");
        String narration = request.getParameter("narration");
        double amount = Double.parseDouble(request.getParameter("amount"));

        String debitAccountName = "Cashbook";
        if ("Online".equalsIgnoreCase(paymentStatus)) debitAccountName = "Bank A/c";
        else if ("Unpaid".equalsIgnoreCase(paymentStatus)) debitAccountName = "Debtors";

        StringBuilder desc = new StringBuilder(category != null ? category.trim() : "Service Sale");
        if (customerName != null && !customerName.isEmpty()) desc.append(" - ").append(customerName.trim());
        if (narration != null && !narration.isEmpty()) desc.append(" (").append(narration.trim()).append(")");

        try (Connection conn = DatabaseHelper.getConnection()) {
            
            // Inject admin_id into the Transactions table
            String ledgerSql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status, admin_id) " +
                               "VALUES ((SELECT account_id FROM Accounts WHERE account_name = ?), " +
                               "(SELECT account_id FROM Accounts WHERE account_name = 'Sales A/c'), ?, ?, 'ACTIVE', ?::uuid)";
            try (PreparedStatement stmt = conn.prepareStatement(ledgerSql)) {
                stmt.setString(1, debitAccountName);
                stmt.setDouble(2, amount);
                stmt.setString(3, desc.toString());
                stmt.setString(4, adminId);
                stmt.executeUpdate();
            }

            if ("Unpaid".equalsIgnoreCase(paymentStatus)) {
                CustomerManager.addCustomerDebt(conn, customerName, amount, adminId);
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"status\":\"success\",\"message\":\"Sale recorded safely in your tenant space.\"}");

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Database error: " + CustomerManager.escapeJson(e.getMessage()) + "\"}");
        }
    }
}
