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

@WebServlet("/Dashboard")
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // MULTI-TENANT SECURITY CHECK
        String adminId = JwtHelper.getAdminIdFromRequest(request);
        if (adminId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Unauthorized.\"}");
            return;
        }

        double totalCash = 0, totalBank = 0, totalDebt = 0, grandTotal = 0;
        double totalLoans = 0, totalLoansRecovered = 0;
        StringBuilder debtorsJson = new StringBuilder("[");
        StringBuilder loansJson = new StringBuilder("[");

        try (Connection conn = DatabaseHelper.getConnection()) {

            // 1. Fetch Sales (Scoped by admin_id)
            String salesSql = "SELECT d.account_name, COALESCE(SUM(t.amount), 0) AS total FROM Transactions t " +
                              "JOIN Accounts d ON t.debit_account_id = d.account_id " +
                              "WHERE t.credit_account_id = (SELECT account_id FROM Accounts WHERE account_name = 'Sales A/c') " +
                              "AND t.status = 'ACTIVE' AND t.admin_id = ?::uuid GROUP BY d.account_name";
            try (PreparedStatement stmt = conn.prepareStatement(salesSql)) {
                stmt.setString(1, adminId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String acc = rs.getString("account_name");
                        double amount = rs.getDouble("total");
                        if ("Cashbook".equals(acc)) totalCash = amount;
                        else if ("Bank A/c".equals(acc)) totalBank = amount;
                    }
                }
            }

            // 2. Fetch Debtors (Scoped by admin_id)
            String dSql = "SELECT name, total_debt FROM Customers WHERE total_debt > 0 AND admin_id = ?::uuid ORDER BY total_debt DESC";
            try (PreparedStatement pst = conn.prepareStatement(dSql)) {
                pst.setString(1, adminId);
                try (ResultSet rs = pst.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        String name = rs.getString("name");
                        double debt = rs.getDouble("total_debt");
                        totalDebt += debt;
                        if (!first) debtorsJson.append(",");
                        debtorsJson.append("{\"name\":\"").append(CustomerManager.escapeJson(name)).append("\",\"amount\":").append(String.format(Locale.US, "%.2f", debt)).append("}");
                        first = false;
                    }
                }
            }
            debtorsJson.append("]");

            grandTotal = totalCash + totalBank + totalDebt;

            // 3. Fetch Active Borrowers (Scoped by admin_id)
            String lSql = "SELECT name, loan_balance FROM Customers WHERE loan_balance > 0 AND admin_id = ?::uuid ORDER BY loan_balance DESC";
            try (PreparedStatement pst = conn.prepareStatement(lSql)) {
                pst.setString(1, adminId);
                try (ResultSet rs = pst.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        String name = rs.getString("name");
                        double bal = rs.getDouble("loan_balance");
                        totalLoans += bal;
                        if (!first) loansJson.append(",");
                        loansJson.append("{\"name\":\"").append(CustomerManager.escapeJson(name)).append("\",\"amount\":").append(String.format(Locale.US, "%.2f", bal)).append("}");
                        first = false;
                    }
                }
            }
            loansJson.append("]");

            // 4. Fetch Total Recovered Loans (Scoped by admin_id)
            String rSql = "SELECT COALESCE(SUM(amount), 0) AS recovered FROM Transactions " +
                          "WHERE credit_account_id = (SELECT account_id FROM Accounts WHERE account_name = 'Loans Given A/c') " +
                          "AND status = 'ACTIVE' AND admin_id = ?::uuid";
            try (PreparedStatement pst = conn.prepareStatement(rSql)) {
                pst.setString(1, adminId);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) totalLoansRecovered = rs.getDouble("recovered");
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(String.format(Locale.US,
                "{\"status\":\"success\",\"message\":\"Dashboard loaded.\",\"data\":{" +
                "\"totalCash\":%.2f, \"totalBank\":%.2f, \"totalDebt\":%.2f, \"grandTotal\":%.2f, " +
                "\"totalLoans\":%.2f, \"totalLoansRecovered\":%.2f, \"totalLoansGiven\":%.2f, " +
                "\"debtors\":%s, \"loans\":%s}}",
                totalCash, totalBank, totalDebt, grandTotal, totalLoans, totalLoansRecovered, (totalLoans + totalLoansRecovered),
                debtorsJson.toString(), loansJson.toString()
            ));

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Failed to load dashboard data.\"}");
        }
    }
}
