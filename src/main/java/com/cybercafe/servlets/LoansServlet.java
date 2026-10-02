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
import java.sql.ResultSet;
import java.util.Locale;

@WebServlet("/Loans")
public class LoansServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        StringBuilder loansJson = new StringBuilder("[");
        double totalOutstandingLoans = 0.0;

        try (Connection conn = DatabaseHelper.getConnection()) {
            String sql = "SELECT customer_id, name, loan_balance FROM Customers WHERE loan_balance > 0 ORDER BY loan_balance DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    int id = rs.getInt("customer_id");
                    String name = rs.getString("name");
                    double amount = rs.getDouble("loan_balance");
                    totalOutstandingLoans += amount;

                    if (!first) {
                        loansJson.append(",");
                    }
                    loansJson.append(String.format(Locale.US,
                            "{\"id\":%d,\"name\":\"%s\",\"amount\":%.2f}",
                            id, CustomerManager.escapeJson(name), amount
                    ));
                    first = false;
                }
            }
            loansJson.append("]");

            String jsonResponse = String.format(Locale.US,
                    "{\"status\":\"success\",\"message\":\"Loans & Advances loaded.\",\"data\":{" +
                            "\"totalOutstandingLoans\":%.2f," +
                            "\"loans\":%s" +
                            "}}",
                    totalOutstandingLoans, loansJson.toString()
            );

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(jsonResponse);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Failed to load Loans & Advances: " + CustomerManager.escapeJson(e.getMessage()) + "\"}");
        }
    }
}