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
import java.sql.ResultSet;

@WebServlet("/ApplyDiscount")
public class ApplyDiscountServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String adminId = JwtHelper.getAdminIdFromRequest(request);
        if (adminId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String customerName = request.getParameter("customerName");
        double discountAmount = Double.parseDouble(request.getParameter("discountAmount"));
        String desc = "Discount: " + customerName.trim();

        try (Connection conn = DatabaseHelper.getConnection()) {
            String checkSql = "SELECT total_debt FROM Customers WHERE name = ? AND admin_id = ?::uuid";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, customerName.trim());
                checkStmt.setString(2, adminId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.getWriter().write("{\"status\":\"error\",\"message\":\"Customer not found.\"}");
                        return;
                    }
                }
            }

            CustomerManager.reduceCustomerDebt(conn, customerName.trim(), discountAmount, adminId);

            String ledgerSql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status, admin_id) " +
                               "VALUES ((SELECT account_id FROM Accounts WHERE account_name = 'Discount Allowed'), " +
                               "(SELECT account_id FROM Accounts WHERE account_name = 'Debtors'), ?, ?, 'ACTIVE', ?::uuid)";
            try (PreparedStatement stmt = conn.prepareStatement(ledgerSql)) {
                stmt.setDouble(1, discountAmount);
                stmt.setString(2, desc);
                stmt.setString(3, adminId);
                stmt.executeUpdate();
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"status\":\"success\",\"message\":\"Discount granted.\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
