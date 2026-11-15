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

@WebServlet("/Debtors")
public class DebtorsServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String adminId = JwtHelper.getAdminIdFromRequest(request);
        if (adminId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        StringBuilder debtorsJson = new StringBuilder("[");
        double totalReceivable = 0.0;

        try (Connection conn = DatabaseHelper.getConnection()) {
            String sql = "SELECT customer_id, name, total_debt FROM Customers WHERE total_debt > 0 AND admin_id = ?::uuid ORDER BY total_debt DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, adminId);
                try (ResultSet rs = stmt.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) debtorsJson.append(",");
                        debtorsJson.append(String.format(Locale.US, "{\"id\":%d,\"name\":\"%s\",\"amount\":%.2f}", rs.getInt("customer_id"), CustomerManager.escapeJson(rs.getString("name")), rs.getDouble("total_debt")));
                        totalReceivable += rs.getDouble("total_debt");
                        first = false;
                    }
                }
            }
            debtorsJson.append("]");

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(String.format(Locale.US, "{\"status\":\"success\",\"data\":{\"totalReceivable\":%.2f,\"debtors\":%s}}", totalReceivable, debtorsJson.toString()));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
