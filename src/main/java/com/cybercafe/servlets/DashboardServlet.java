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

@WebServlet("/Dashboard")
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        double totalCash = 0.0;
        double totalBank = 0.0;
        double totalDebt = 0.0;
        double grandTotal = 0.0;
        double totalLoans = 0.0;
        double totalLoansRecovered = 0.0;

        StringBuilder debtorsJson = new StringBuilder("[");
        StringBuilder loansJson = new StringBuilder("[");

        try (Connection conn = DatabaseHelper.getConnection()) {

            // 1. Fetch Sales Revenue Breakdown (Cashbook, Bank A/c, Debtors credited to Sales A/c)
            String salesSql = "SELECT d.account_name, COALESCE(SUM(t.amount), 0) AS total " +
                    "FROM Transactions t " +
                    "JOIN Accounts d ON t.debit_account_id = d.account_id " +
                    "WHERE t.credit_account_id = (SELECT account_id FROM Accounts WHERE account_name = 'Sales A/c') " +
                    "AND t.status = 'ACTIVE' " +
                    "GROUP BY d.account_name";
            try (PreparedStatement stmt = conn.prepareStatement(salesSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String acc = rs.getString("account_name");
                    double amount = rs.getDouble("total");
                    if ("Cashbook".equals(acc)) {
                        totalCash = amount;
                    } else if ("Bank A/c".equals(acc)) {
                        totalBank = amount;
                    }
                }
            }

            // 2. Fetch Active Debtors for Chart 1 & live Accounts Receivable total
            String dSql = "SELECT name, total_debt FROM Customers WHERE total_debt > 0 ORDER BY total_debt DESC";
            try (PreparedStatement pst = conn.prepareStatement(dSql);
                 ResultSet rs = pst.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    String name = rs.getString("name");
                    double debt = rs.getDouble("total_debt");
                    totalDebt += debt;

                    if (!first) {
                        debtorsJson.append(",");
                    }
                    debtorsJson.append("{\"name\":\"").append(CustomerManager.escapeJson(name)).append("\",")
                            .append("\"amount\":").append(String.format(Locale.US, "%.2f", debt)).append("}");
                    first = false;
                }
            }
            debtorsJson.append("]");

            grandTotal = totalCash + totalBank + totalDebt;

            // 3. Fetch Active Borrowers for Chart 2 & Outstanding Loans total
            String lSql = "SELECT name, loan_balance FROM Customers WHERE loan_balance > 0 ORDER BY loan_balance DESC";
            try (PreparedStatement pst = conn.prepareStatement(lSql);
                 ResultSet rs = pst.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    String name = rs.getString("name");
                    double bal = rs.getDouble("loan_balance");
                    totalLoans += bal;

                    if (!first) {
                        loansJson.append(",");
                    }
                    loansJson.append("{\"name\":\"").append(CustomerManager.escapeJson(name)).append("\",")
                            .append("\"amount\":").append(String.format(Locale.US, "%.2f", bal)).append("}");
                    first = false;
                }
            }
            loansJson.append("]");

            // 4. Fetch Total Recovered Loans (Credits to 'Loans Given A/c')
            String rSql = "SELECT COALESCE(SUM(amount), 0) AS recovered FROM Transactions " +
                    "WHERE credit_account_id = (SELECT account_id FROM Accounts WHERE account_name = 'Loans Given A/c') " +
                    "AND status = 'ACTIVE'";
            try (PreparedStatement pst = conn.prepareStatement(rSql);
                 ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    totalLoansRecovered = rs.getDouble("recovered");
                }
            }

            double totalLoansGiven = totalLoans + totalLoansRecovered;

            String jsonResponse = String.format(Locale.US,
                    "{\"status\":\"success\",\"message\":\"Dashboard metrics loaded.\",\"data\":{" +
                            "\"totalCash\":%.2f," +
                            "\"totalBank\":%.2f," +
                            "\"totalDebt\":%.2f," +
                            "\"grandTotal\":%.2f," +
                            "\"totalLoans\":%.2f," +
                            "\"totalLoansRecovered\":%.2f," +
                            "\"totalLoansGiven\":%.2f," +
                            "\"debtors\":%s," +
                            "\"loans\":%s" +
                            "}}",
                    totalCash, totalBank, totalDebt, grandTotal,
                    totalLoans, totalLoansRecovered, totalLoansGiven,
                    debtorsJson.toString(), loansJson.toString()
            );

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(jsonResponse);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Failed to load dashboard data: " + CustomerManager.escapeJson(e.getMessage()) + "\"}");
        }
    }
}