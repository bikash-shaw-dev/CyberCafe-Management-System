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
import java.util.*;

@WebServlet("/Ledger")
public class LedgerServlet extends HttpServlet {
    private static class LedgerRow { int id; String date; String particulars; double cashAmount; double bankAmount; double amount; String status; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String adminId = JwtHelper.getAdminIdFromRequest(request);
        if (adminId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        List<String> allAccounts = new ArrayList<>();
        Map<String, List<LedgerRow>> debitEntries = new LinkedHashMap<>();
        Map<String, List<LedgerRow>> creditEntries = new LinkedHashMap<>();
        List<LedgerRow> cashbookDebits = new ArrayList<>();
        List<LedgerRow> cashbookCredits = new ArrayList<>();

        try (Connection conn = DatabaseHelper.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT account_name FROM Accounts ORDER BY account_id ASC"); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String accName = rs.getString("account_name");
                    allAccounts.add(accName);
                    debitEntries.put(accName, new ArrayList<>());
                    creditEntries.put(accName, new ArrayList<>());
                }
            }

            String txSql = "SELECT t.transaction_id, TO_CHAR(COALESCE(t.transaction_date, CURRENT_TIMESTAMP), 'YYYY-MM-DD') AS tx_date, " +
                           "d.account_name AS debit_acc, c.account_name AS credit_acc, t.amount, t.description, COALESCE(t.status, 'ACTIVE') AS status " +
                           "FROM Transactions t JOIN Accounts d ON t.debit_account_id = d.account_id JOIN Accounts c ON t.credit_account_id = c.account_id " +
                           "WHERE t.admin_id = ?::uuid ORDER BY t.transaction_id ASC";
            try (PreparedStatement stmt = conn.prepareStatement(txSql)) {
                stmt.setString(1, adminId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String debitAcc = rs.getString("debit_acc"), creditAcc = rs.getString("credit_acc"), desc = rs.getString("description") != null ? rs.getString("description") : "", status = rs.getString("status"), date = rs.getString("tx_date");
                        double amount = rs.getDouble("amount");
                        int txId = rs.getInt("transaction_id");

                        if ("Cashbook".equals(debitAcc) || "Bank A/c".equals(debitAcc)) { LedgerRow r = new LedgerRow(); r.id = txId; r.date = date; r.particulars = "To " + creditAcc + (desc.isEmpty()?"":" ("+desc+")"); r.cashAmount = "Cashbook".equals(debitAcc)?amount:0; r.bankAmount = "Bank A/c".equals(debitAcc)?amount:0; r.status = status; cashbookDebits.add(r); }
                        if ("Cashbook".equals(creditAcc) || "Bank A/c".equals(creditAcc)) { LedgerRow r = new LedgerRow(); r.id = txId; r.date = date; r.particulars = "By " + debitAcc + (desc.isEmpty()?"":" ("+desc+")"); r.cashAmount = "Cashbook".equals(creditAcc)?amount:0; r.bankAmount = "Bank A/c".equals(creditAcc)?amount:0; r.status = status; cashbookCredits.add(r); }
                        if (debitEntries.containsKey(debitAcc)) { LedgerRow r = new LedgerRow(); r.id = txId; r.date = date; r.particulars = "To " + creditAcc + (desc.isEmpty()?"":" — "+desc); r.amount = amount; r.status = status; debitEntries.get(debitAcc).add(r); }
                        if (creditEntries.containsKey(creditAcc)) { LedgerRow r = new LedgerRow(); r.id = txId; r.date = date; r.particulars = "By " + debitAcc + (desc.isEmpty()?"":" — "+desc); r.amount = amount; r.status = status; creditEntries.get(creditAcc).add(r); }
                    }
                }
            }
            
            // Build JSON output exactly as previously formatted
            StringBuilder json = new StringBuilder("{\"status\":\"success\",\"data\":{");
            json.append("\"allAccounts\":[");
            for (int i=0; i<allAccounts.size(); i++) { if(i>0)json.append(","); json.append("\"").append(CustomerManager.escapeJson(allAccounts.get(i))).append("\""); }
            json.append("],\"cashbook\":{\"debits\":").append(serializeCash(cashbookDebits)).append(",\"credits\":").append(serializeCash(cashbookCredits)).append("},\"accounts\":{");
            boolean firstAcc = true;
            for (String acc : allAccounts) {
                if (!firstAcc) json.append(",");
                json.append("\"").append(CustomerManager.escapeJson(acc)).append("\":{\"debits\":").append(serializeStd(debitEntries.get(acc))).append(",\"credits\":").append(serializeStd(creditEntries.get(acc))).append("}");
                firstAcc = false;
            }
            json.append("}}}");

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(json.toString());
        } catch (Exception e) { response.setStatus(500); }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        String adminId = JwtHelper.getAdminIdFromRequest(request);
        if (adminId == null) { response.setStatus(401); return; }

        try (Connection conn = DatabaseHelper.getConnection()) {
            if ("void".equalsIgnoreCase(request.getParameter("action"))) {
                String sql = "UPDATE Transactions SET status = 'VOID' WHERE transaction_id = ? AND admin_id = ?::uuid";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setInt(1, Integer.parseInt(request.getParameter("transactionId"))); stmt.setString(2, adminId); stmt.executeUpdate(); }
            } else {
                String sql = "INSERT INTO Transactions (debit_account_id, credit_account_id, amount, description, status, admin_id) VALUES ((SELECT account_id FROM Accounts WHERE account_name = ?), (SELECT account_id FROM Accounts WHERE account_name = ?), ?, ?, 'ACTIVE', ?::uuid)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setString(1, request.getParameter("debitAccount")); stmt.setString(2, request.getParameter("creditAccount")); stmt.setDouble(3, Double.parseDouble(request.getParameter("amount"))); stmt.setString(4, request.getParameter("description")); stmt.setString(5, adminId); stmt.executeUpdate(); }
            }
            response.getWriter().write("{\"status\":\"success\"}");
        } catch (Exception e) { response.setStatus(500); }
    }

    private String serializeCash(List<LedgerRow> r) { StringBuilder s=new StringBuilder("["); for(int i=0;i<r.size();i++){if(i>0)s.append(",");s.append(String.format(Locale.US,"{\"id\":%d,\"date\":\"%s\",\"particulars\":\"%s\",\"cashAmount\":%.2f,\"bankAmount\":%.2f,\"status\":\"%s\"}",r.get(i).id,CustomerManager.escapeJson(r.get(i).date),CustomerManager.escapeJson(r.get(i).particulars),r.get(i).cashAmount,r.get(i).bankAmount,CustomerManager.escapeJson(r.get(i).status)));} return s.append("]").toString();}
    private String serializeStd(List<LedgerRow> r) { StringBuilder s=new StringBuilder("["); for(int i=0;i<r.size();i++){if(i>0)s.append(",");s.append(String.format(Locale.US,"{\"id\":%d,\"date\":\"%s\",\"particulars\":\"%s\",\"amount\":%.2f,\"status\":\"%s\"}",r.get(i).id,CustomerManager.escapeJson(r.get(i).date),CustomerManager.escapeJson(r.get(i).particulars),r.get(i).amount,CustomerManager.escapeJson(r.get(i).status)));} return s.append("]").toString();}
}
