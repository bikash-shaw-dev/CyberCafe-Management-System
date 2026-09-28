-- Create Database
CREATE DATABASE IF NOT EXISTS cybercafe_db;
USE cybercafe_db;

-- 1. Customers Table (Tracks Debtors / Accounts Receivable)
CREATE TABLE Customers (
                           customer_id INT AUTO_INCREMENT PRIMARY KEY,
                           name VARCHAR(100) NOT NULL,
                           phone VARCHAR(15) UNIQUE,
                           outstanding_balance DECIMAL(10,2) DEFAULT 0.00
);

-- 2. Creditors Table (Tracks Suppliers, ISPs, Landlords / Accounts Payable)
CREATE TABLE Creditors (
                           creditor_id INT AUTO_INCREMENT PRIMARY KEY,
                           company_name VARCHAR(100) NOT NULL,
                           service_type VARCHAR(50),
                           payable_balance DECIMAL(10,2) DEFAULT 0.00
);

-- 3. Sales Table (Tracks Revenue by Service Category)
CREATE TABLE Sales (
                       sale_id INT AUTO_INCREMENT PRIMARY KEY,
                       customer_id INT NULL,
                       sale_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                       category VARCHAR(50) NOT NULL,
                       total_amount DECIMAL(10,2) NOT NULL,
                       payment_status VARCHAR(20) NOT NULL,
                       FOREIGN KEY (customer_id) REFERENCES Customers(customer_id)
);

-- 4. Expenses Table (Tracks Business Purchases and Bills)
CREATE TABLE Expenses (
                          expense_id INT AUTO_INCREMENT PRIMARY KEY,
                          creditor_id INT NULL,
                          expense_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                          category VARCHAR(50) NOT NULL,
                          total_amount DECIMAL(10,2) NOT NULL,
                          payment_status VARCHAR(20) NOT NULL,
                          FOREIGN KEY (creditor_id) REFERENCES Creditors(creditor_id)
);

-- 5. Cashbook Table (Master Ledger for Cash in Hand and Bank)
CREATE TABLE Cashbook (
                          transaction_id INT AUTO_INCREMENT PRIMARY KEY,
                          transaction_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                          account_type VARCHAR(20) NOT NULL,      -- 'Cash in Hand' or 'Bank'
                          transaction_type VARCHAR(20) NOT NULL,  -- 'Income' or 'Expense'
                          amount DECIMAL(10,2) NOT NULL,
                          reference_id INT,                       -- Links to sale_id or expense_id
                          description VARCHAR(255)
);