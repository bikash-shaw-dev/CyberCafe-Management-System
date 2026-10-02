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
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Servlet that processes new sales from the dashboard and executes double-entry accounting logic.
 */
@WebServlet("/SaveSaleServlet")
public class SaveSaleServlet extends HttpServlet {

    // Optional health-check when opening /SaveSaleServlet directly in a browser
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":\"online\",\"message\":\"SaveSaleServlet API is live. Submit the form via POST to record sales.\"}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // 1. Extract form inputs using HTML 'name' attributes
            String category = request.getParameter("category");
            String customerName = request.getParameter("customerName");
            String accountType = request.getParameter("accountType"); // 'Cash in Hand' or 'Bank'
            String amountStr = request.getParameter("amount");

            if (amountStr == null || amountStr.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing 'amount' parameter from form submission.");
            }
            double amount = Double.parseDouble(amountStr.trim());

            // Determine if this is a Walk-in Paid Sale or a Credit (Debtor) Sale
            boolean isCreditSale = (customerName != null && !customerName.trim().isEmpty());
            String paymentStatus = isCreditSale ? "Unpaid" : "Paid";

            try (Connection conn = DatabaseHelper.getConnection()) {
                if (conn == null) {
                    throw new SQLException("Database connection is null. Verify SUPABASE_DB_URL environment variable.");
                }

                // Disable auto-commit to ensure both sides of the double-entry succeed together
                conn.setAutoCommit(false);

                try {
                    Integer customerId = null;
                    CustomerManager customerManager = new CustomerManager();

                    // 2. If a customer name was entered, find or create their Debtor profile
                    if (isCreditSale) {
                        customerId = customerManager.getOrCreateCustomer(conn, customerName.trim());
                    }

                    // 3. CREDIT ENTRY: Record the revenue in the Sales table
                    String saleSql = "INSERT INTO Sales (customer_id, category, total_amount, payment_status) VALUES (?, ?, ?, ?)";
                    int saleId = 0;

                    try (PreparedStatement saleStmt = conn.prepareStatement(saleSql, Statement.RETURN_GENERATED_KEYS)) {
                        if (customerId != null) {
                            saleStmt.setInt(1, customerId);
                        } else {
                            saleStmt.setNull(1, Types.INTEGER); // Walk-in customer
                        }
                        saleStmt.setString(2, category);
                        saleStmt.setDouble(3, amount);
                        saleStmt.setString(4, paymentStatus);
                        saleStmt.executeUpdate();

                        // Retrieve the generated sale_id for the Cashbook reference
                        try (ResultSet rs = saleStmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                saleId = rs.getInt(1);
                            }
                        }
                    }

                    // 4. DEBIT ENTRY: Route to Debtor Balance (if credit) OR Cashbook (if paid now)
                    if (isCreditSale) {
                        customerManager.addCustomerDebt(conn, customerId, amount);
                        System.out.println("Credit Sale Recorded: ₹" + amount + " added to " + customerName + "'s tab.");
                    } else {
                        String cashSql = "INSERT INTO Cashbook (account_type, transaction_type, amount, reference_id, description) VALUES (?, 'Income', ?, ?, ?)";
                        try (PreparedStatement cashStmt = conn.prepareStatement(cashSql)) {
                            cashStmt.setString(1, accountType);
                            cashStmt.setDouble(2, amount);
                            cashStmt.setInt(3, saleId);
                            cashStmt.setString(4, "Walk-in sale: " + category);
                            cashStmt.executeUpdate();
                        }
                        System.out.println("Paid Sale Recorded: ₹" + amount + " added to " + accountType);
                    }

                    // Commit the double-entry transaction to the database
                    conn.commit();

                    // 5. Return a clean JSON success response to the cloud frontend
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write("{\"status\":\"success\",\"message\":\"Transaction saved to Supabase Cloud!\"}");

                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            }

        } catch (Exception e) {
            System.err.println("Database transaction failed!");
            e.printStackTrace();
            String safeMsg = (e.getMessage() != null) ? e.getMessage().replace("\"", "'") : e.toString();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"" + safeMsg + "\"}");
        }
    }
}