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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@WebServlet("/Ledger")
public class LedgerServlet extends HttpServlet {

    private static class LedgerRow {
        int id;
        String date;
        String particulars;
        double cashAmount;
        double bankAmount;
        double amount;
        String status;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        List<String> allAccounts = new ArrayList<>();
        Map<String, List<LedgerRow>> debitEntries = new LinkedHashMap<>();
        Map<String, List<LedgerRow>> creditEntries = new LinkedHashMap<>();
        List<LedgerRow> cashbookDebits = new ArrayList<>();
        List<LedgerRow> cashbookCredits = new ArrayList<>();

        try (Connection conn = DatabaseHelper.getConnection()) {

            // 1. Fetch all General Ledger Accounts
            String accSql = "SELECT account_name FROM Accounts ORDER BY account_id ASC";
            try (PreparedStatement stmt = conn.prepareStatement(accSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String accName = rs.getString("account_name");
                    allAccounts.add(accName);
                    debitEntries.put(accName, new ArrayList<>());
                    creditEntries.put(accName, new ArrayList<>());
                }
            }

            // 2. Fetch all Transactions formatted for PostgreSQL
            String txSql = "SELECT t.transaction_id, " +
                    "TO_CHAR(COALESCE(t.transaction_date, CURRENT_TIMESTAMP), 'YYYY-MM-DD') AS tx_date, " +
                    "d.account_name AS debit_acc, " +
                    "c.account_name AS credit_acc, " +
                    "t.amount, t.description, COALESCE(t.status, 'ACTIVE') AS status " +
                    "FROM Transactions t " +
                    "JOIN Accounts d ON t.debit_account_id = d.account_id " +
                    "JOIN Accounts c ON t.credit_account_id = c.account_id " +
                    "ORDER BY t.transaction_id ASC";

            try (PreparedStatement stmt = conn.prepareStatement(txSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int txId = rs.getInt("transaction_id");
                    String date = rs.getString("tx_date");
                    String debitAcc = rs.getString("debit_acc");
                    String creditAcc = rs.getString("credit_acc");
                    double amount = rs.getDouble("amount");
                    String desc = rs.getString("description") != null ? rs.getString("description") : "";
                    String status = rs.getString("status");

                    // A. Populate Double-Column Cashbook (Cashbook + Bank A/c)
                    if ("Cashbook".equals(debitAcc) || "Bank A/c".equals(debitAcc)) {
                        LedgerRow cbDr = new LedgerRow();
                        cbDr.id = txId;
                        cbDr.date = date;
                        cbDr.particulars = "To " + creditAcc + (desc.isEmpty() ? "" : " (" + desc + ")");
                        cbDr.cashAmount = "Cashbook".equals(debitAcc) ? amount : 0.0;
                        cbDr.bankAmount = "Bank A/c".equals(debitAcc) ? amount : 0.0;
                        cbDr.status = status;
                        cashbookDebits.add(cbDr);
                    }

                    if ("Cashbook".equals(creditAcc) || "Bank A/c".equals(creditAcc)) {
                        LedgerRow cbCr = new LedgerRow();
                        cbCr.id = txId;
                        cbCr.date = date;
                        cbCr.particulars = "By " + debitAcc + (desc.isEmpty() ? "" : " (" + desc + ")");
                        cbCr.cashAmount = "Cashbook".equals(creditAcc) ? amount : 0.0;
                        cbCr.bankAmount = "Bank A/c".equals(creditAcc) ? amount : 0.0;
                        cbCr.status = status;
                        cashbookCredits.add(cbCr);
                    }

                    // B. Populate Standard T-Account Ledgers
                    if (debitEntries.containsKey(debitAcc)) {
                        LedgerRow dr = new LedgerRow();
                        dr.id = txId;
                        dr.date = date;
                        dr.particulars = "To " + creditAcc + (desc.isEmpty() ? "" : " — " + desc);
                        dr.amount = amount;
                        dr.status = status;
                        debitEntries.get(debitAcc).add(dr);
                    }

                    if (creditEntries.containsKey(creditAcc)) {
                        LedgerRow cr = new LedgerRow();
                        cr.id = txId;
                        cr.date = date;
                        cr.particulars = "By " + debitAcc + (desc.isEmpty() ? "" : " — " + desc);
                        cr.amount = amount;
                        cr.status = status;
                        creditEntries.get(creditAcc).add(cr);
                    }
                }
            }

            // 3. Serialize Everything to Clean JSON
            StringBuilder json = new StringBuilder();
            json.append("{\"status\":\"success\",\"message\":\"General Ledger loaded.\",\"data\":{");

            // allAccounts array
            json.append("\"allAccounts\":[");
            for (int i = 0; i < allAccounts.size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(CustomerManager.escapeJson(allAccounts.get(i))).append("\"");
            }
            json.append("],");

            // cashbook object
            json.append("\"cashbook\":{");
            json.append("\"debits\":").append(serializeCashbookRows(cashbookDebits)).append(",");
            json.append("\"credits\":").append(serializeCashbookRows(cashbookCredits));
            json.append("},");

            // standard accounts map
            json.append("\"accounts\":{");
            boolean firstAcc = true;
            for (String acc : allAccounts) {
                if (!firstAcc) json.append(",");
                json.append("\"").append(CustomerManager.escapeJson(acc)).append("\":{");
                json.append("\"debits\":").append(serializeStandardRows(debitEntries.get(acc))).append(",");
                json.append("\"credits\":").append(serializeStandardRows(creditEntries.get(acc)));
                json.append("}");
                firstAcc = false;
            }
            json.append("}}}");

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Failed to load General Ledger: " + CustomerManager.escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Supports Adjustment Mode operations: Manual Journal Entry & Voiding Transactions
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String action = request.getParameter("action");

        try (Connection conn = DatabaseHelper.getConnection()) {
            if ("void".equalsIgnoreCase(action)) {
                int txId = Integer.parseInt(request.getParameter("transactionId"));
                String sql = "UPDATE Transactions SET status = 'VOID' WHERE transaction_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, txId);
                    stmt.executeUpdate();
                }
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("{\"status\":\"success\",\"message\":\"Transaction #" + txId + " marked as VOID.\"}");
                return;
            }

            // Default POST action: Manual Journal Entry
            String debitAccount = request.getParameter("debitAccount");
            String creditAccount = request.getParameter("creditAccount");
            String description = request.getParameter("description");
            double amount = Double.parseDouble(request.getParameter("amount"));

            if (amount <= 0 || debitAccount == null || creditAccount == null) {
                throw new IllegalArgumentException("Valid debit account, credit account, and positive amount are required.");
            }

            String sql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status) " +
                    "VALUES ((SELECT account_id FROM Accounts WHERE account_name = ?), " +
                    "(SELECT account_id FROM Accounts WHERE account_name = ?), ?, ?, 'ACTIVE')";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, debitAccount.trim());
                stmt.setString(2, creditAccount.trim());
                stmt.setDouble(3, amount);
                stmt.setString(4, description != null ? description.trim() : "Manual Journal Adjustment");
                stmt.executeUpdate();
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"status\":\"success\",\"message\":\"Manual journal entry posted to General Ledger.\"}");

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Ledger adjustment failed: " + CustomerManager.escapeJson(e.getMessage()) + "\"}");
        }
    }

    private String serializeCashbookRows(List<LedgerRow> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            LedgerRow r = rows.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(Locale.US,
                    "{\"id\":%d,\"date\":\"%s\",\"particulars\":\"%s\",\"cashAmount\":%.2f,\"bankAmount\":%.2f,\"status\":\"%s\"}",
                    r.id,
                    CustomerManager.escapeJson(r.date),
                    CustomerManager.escapeJson(r.particulars),
                    r.cashAmount,
                    r.bankAmount,
                    CustomerManager.escapeJson(r.status)
            ));
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeStandardRows(List<LedgerRow> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            LedgerRow r = rows.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(Locale.US,
                    "{\"id\":%d,\"date\":\"%s\",\"particulars\":\"%s\",\"amount\":%.2f,\"status\":\"%s\"}",
                    r.id,
                    CustomerManager.escapeJson(r.date),
                    CustomerManager.escapeJson(r.particulars),
                    r.amount,
                    CustomerManager.escapeJson(r.status)
            ));
        }
        sb.append("]");
        return sb.toString();
    }
}