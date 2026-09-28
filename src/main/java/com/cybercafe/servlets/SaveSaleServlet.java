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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        // 1. Extract form inputs using HTML 'name' attributes
        String category = request.getParameter("category");
        String customerName = request.getParameter("customerName");
        String accountType = request.getParameter("accountType"); // 'Cash in Hand' or 'Bank'
        double amount = Double.parseDouble(request.getParameter("amount"));

        // Determine if this is a Walk-in Paid Sale or a Credit (Debtor) Sale
        boolean isCreditSale = (customerName != null && !customerName.trim().isEmpty());
        String paymentStatus = isCreditSale ? "Unpaid" : "Paid";

        try (Connection conn = DatabaseHelper.getConnection()) {
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
                    // Increase Debtor's outstanding balance
                    customerManager.addCustomerDebt(conn, customerId, amount);
                    System.out.println("Credit Sale Recorded: ₹" + amount + " added to " + customerName + "'s tab.");
                } else {
                    // Increase Cash in Hand or Bank in the Cashbook
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

            } catch (SQLException ex) {
                // Roll back changes if any step fails so accounts never go out of balance
                conn.rollback();
                throw ex;
            }

        } catch (SQLException e) {
            System.err.println("Database transaction failed!");
            e.printStackTrace();
        }

        // 5. Redirect back to the dashboard
        response.sendRedirect("index.html");
    }
}