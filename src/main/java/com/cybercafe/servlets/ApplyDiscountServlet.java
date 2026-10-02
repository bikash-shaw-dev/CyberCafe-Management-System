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

@WebServlet("/ApplyDiscount")
public class ApplyDiscountServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String customerName = request.getParameter("customerName");
        String discountReason = request.getParameter("discountReason");
        String saleDate = request.getParameter("saleDate");
        String saleParticulars = request.getParameter("saleParticulars");
        String discountAmountStr = request.getParameter("discountAmount");

        if (customerName == null || customerName.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Customer name is required to grant a discount.\"}");
            return;
        }

        double discountAmount;
        try {
            discountAmount = Double.parseDouble(discountAmountStr);
            if (discountAmount <= 0) {
                throw new NumberFormatException("Discount amount must be greater than zero.");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Invalid discount amount.\"}");
            return;
        }

        // Construct the specific audit trail narration:
        // e.g. "Discount: Jit Das (Goodwill) for [Color Printout] on 2026-09-19"
        StringBuilder detailedNarration = new StringBuilder("Discount: ").append(customerName.trim());
        if (discountReason != null && !discountReason.trim().isEmpty()) {
            detailedNarration.append(" (").append(discountReason.trim()).append(")");
        }
        if (saleParticulars != null && !saleParticulars.trim().isEmpty()) {
            detailedNarration.append(" for [").append(saleParticulars.trim()).append("]");
        }
        if (saleDate != null && !saleDate.trim().isEmpty()) {
            detailedNarration.append(" on ").append(saleDate.trim());
        }

        try (Connection conn = DatabaseHelper.getConnection()) {

            // 1. Verify customer exists in Customers table and check current debt
            String checkSql = "SELECT total_debt FROM Customers WHERE name = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, customerName.trim());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.getWriter().write("{\"status\":\"error\",\"message\":\"Customer '" + CustomerManager.escapeJson(customerName.trim()) + "' not found in Accounts Receivable.\"}");
                        return;
                    }
                }
            }

            // 2. Reduce trade debt in Customers table
            CustomerManager.reduceCustomerDebt(conn, customerName.trim(), discountAmount);

            // 3. Post Compound Audit Entry to General Ledger: Dr Discount Allowed, Cr Debtors
            String ledgerSql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status) " +
                    "VALUES ((SELECT account_id FROM Accounts WHERE account_name = 'Discount Allowed'), " +
                    "(SELECT account_id FROM Accounts WHERE account_name = 'Debtors'), ?, ?, 'ACTIVE')";
            try (PreparedStatement stmt = conn.prepareStatement(ledgerSql)) {
                stmt.setDouble(1, discountAmount);
                stmt.setString(2, detailedNarration.toString());
                stmt.executeUpdate();
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(String.format(Locale.US,
                    "{\"status\":\"success\",\"message\":\"Discount granted and posted to General Ledger.\",\"data\":{" +
                            "\"customerName\":\"%s\"," +
                            "\"discountAmount\":%.2f," +
                            "\"narration\":\"%s\"" +
                            "}}",
                    CustomerManager.escapeJson(customerName.trim()),
                    discountAmount,
                    CustomerManager.escapeJson(detailedNarration.toString())
            ));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Database error: " + CustomerManager.escapeJson(e.getMessage()) + "\"}");
        }
    }
}