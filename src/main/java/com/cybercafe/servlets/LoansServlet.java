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
import java.util.Locale;

@WebServlet("/Loans")
public class LoansServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String adminId = JwtHelper.getAdminIdFromRequest(request);
        if (adminId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        StringBuilder loansJson = new StringBuilder("[");
        double totalOutstandingLoans = 0.0;

        try (Connection conn = DatabaseHelper.getConnection()) {
            String sql = "SELECT customer_id, name, loan_balance FROM Customers WHERE loan_balance > 0 AND admin_id = ?::uuid ORDER BY loan_balance DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, adminId);
                try (ResultSet rs = stmt.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) loansJson.append(",");
                        loansJson.append(String.format(Locale.US, "{\"id\":%d,\"name\":\"%s\",\"amount\":%.2f}", rs.getInt("customer_id"), CustomerManager.escapeJson(rs.getString("name")), rs.getDouble("loan_balance")));
                        totalOutstandingLoans += rs.getDouble("loan_balance");
                        first = false;
                    }
                }
            }
            loansJson.append("]");

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(String.format(Locale.US, "{\"status\":\"success\",\"data\":{\"totalOutstandingLoans\":%.2f,\"loans\":%s}}", totalOutstandingLoans, loansJson.toString()));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
