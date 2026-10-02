// ============================================================================
// 1. CLOUD CONFIGURATION & SUPABASE INITIALIZATION
// ============================================================================
const API_BASE_URL = "https://cybercafe-management-system.onrender.com";
const SUPABASE_URL = "https://hyrhegfoukbvjyyqobkh.supabase.co";
const SUPABASE_ANON_KEY = "YOUR_SUPABASE_ANON_KEY"; // <-- Keep your existing Supabase Anon Key here

const supabaseClient = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

// Global Application State
let currentSession = null;
let revenueChart = null;
let loanChart = null;
let cachedLedgerData = null;
let selectedFolios = new Set(["Cashbook", "Sales A/c", "Debtors", "Discount Allowed", "Loans Given A/c"]);
let currentLedgerMode = "view"; // 'view' or 'adjustment'

// Alpha channel steps for dynamic per-customer chart slices
const CHART_ALPHAS = ["FF", "E6", "CC", "B3", "99", "80", "66", "4D"];

// ============================================================================
// 2. THEME & UI HELPERS
// ============================================================================
function themeColor(varName) {
    return getComputedStyle(document.body).getPropertyValue(varName).trim();
}

function showToast(message, isError = false) {
    const toast = document.getElementById("toastNotification");
    if (!toast) return;
    toast.textContent = message;
    toast.className = isError ? "show error" : "show";
    setTimeout(() => {
        toast.className = "";
    }, 3500);
}

function escapeHtml(str) {
    if (str === null || str === undefined) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function toggleTheme() {
    const body = document.body;
    const btn = document.getElementById("themeBtn");
    btn.classList.add("pressing");
    body.classList.toggle("dark-mode");

    setTimeout(() => {
        if (body.classList.contains("dark-mode")) {
            localStorage.setItem("cyberCafeTheme", "dark");
            btn.innerHTML = "Day Mode";
        } else {
            localStorage.setItem("cyberCafeTheme", "light");
            btn.innerHTML = "Night Mode";
        }
        btn.classList.remove("pressing");
    }, 200);

    if (window.lastDashboardData) {
        setTimeout(() => renderDualCharts(window.lastDashboardData), 150);
    }
}

// ============================================================================
// 3. SUPABASE AUTHENTICATION & AUTHORIZED FETCH WRAPPER
// ============================================================================
async function apiFetch(endpoint, options = {}) {
    const { data: { session } } = await supabaseClient.auth.getSession();
    currentSession = session;

    const headers = new Headers(options.headers || {});
    if (session && session.access_token) {
        headers.set("Authorization", "Bearer " + session.access_token);
    }
    if (options.body && typeof options.body === "string" && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers
    });

    const json = await response.json();
    if (!response.ok || json.status === "error") {
        throw new Error(json.message || `Server error (${response.status})`);
    }
    return json;
}

async function checkSession() {
    const { data: { session } } = await supabaseClient.auth.getSession();
    currentSession = session;

    const loginSection = document.getElementById("loginSection");
    const dashboardSection = document.getElementById("dashboardSection");

    if (session) {
        loginSection.style.display = "none";
        dashboardSection.style.display = "block";
        await loadDashboard();
    } else {
        loginSection.style.display = "block";
        dashboardSection.style.display = "none";
    }
}

// ============================================================================
// 4. SPA VIEW SWITCHING (COUNTER / LEDGER / A/R / LOANS)
// ============================================================================
function switchView(viewName) {
    const views = {
        counter: {
            el: "viewCounter",
            nav: "navCounter",
            title: "Counter Ledger",
            sub: "Daily Record",
            loader: loadDashboard
        },
        ledger: {
            el: "viewLedger",
            nav: "navLedger",
            title: "General Ledger",
            sub: "Full Book",
            loader: loadLedger
        },
        debtors: {
            el: "viewDebtors",
            nav: "navDebtors",
            title: "Accounts Receivable",
            sub: "Customer Dues",
            loader: loadDebtors
        },
        loans: {
            el: "viewLoans",
            nav: "navLoans",
            title: "Loans & Advances",
            sub: "Outstanding",
            loader: loadLoans
        }
    };

    const target = views[viewName];
    if (!target) return;

    document.querySelectorAll(".spa-view").forEach(v => v.classList.remove("active-view"));
    document.querySelectorAll(".btn-nav").forEach(b => b.classList.remove("active-nav"));

    document.getElementById(target.el).classList.add("active-view");
    document.getElementById(target.nav).classList.add("active-nav");

    document.getElementById("pageMainTitle").innerHTML =
        `${target.title} <span class="handwritten" id="pageSubTitle">${target.sub}</span>`;

    target.loader();
}

// ============================================================================
// 5. COUNTER DASHBOARD & DUAL DOUGHNUT CHARTS
// ============================================================================
async function loadDashboard() {
    try {
        const result = await apiFetch("/Dashboard", { method: "GET" });
        const d = result.data;
        window.lastDashboardData = d;

        document.getElementById("valGross").textContent = `₹${Number(d.grandTotal).toFixed(2)}`;
        document.getElementById("valCash").textContent = `₹${Number(d.totalCash).toFixed(2)}`;
        document.getElementById("valBank").textContent = `₹${Number(d.totalBank).toFixed(2)}`;
        document.getElementById("valDebt").textContent = `₹${Number(d.totalDebt).toFixed(2)}`;

        document.getElementById("valLoansGiven").textContent = `₹${Number(d.totalLoansGiven).toFixed(2)}`;
        document.getElementById("valLoansRecovered").textContent = `₹${Number(d.totalLoansRecovered).toFixed(2)}`;
        document.getElementById("valLoansOutstanding").textContent = `₹${Number(d.totalLoans).toFixed(2)}`;

        renderDualCharts(d);
    } catch (err) {
        showToast(err.message, true);
    }
}

function renderDualCharts(d) {
    const cashCol = themeColor("--cash");
    const bankCol = themeColor("--bank");
    const debtBase = themeColor("--debt");
    const loanBase = themeColor("--loan");

    // 1. Sales & Receivables Chart Data
    const debtorsList = Array.isArray(d.debtors) ? d.debtors : [];
    const salesLabels = ["Cash", "Bank", ...debtorsList.map(item => `${item.name} (A/R)`)];
    const salesData = [
        Number(d.totalCash) || 0,
        Number(d.totalBank) || 0,
        ...debtorsList.map(item => Number(item.amount) || 0)
    ];
    const salesColors = [
        cashCol,
        bankCol,
        ...debtorsList.map((_, i) => debtBase + CHART_ALPHAS[i % CHART_ALPHAS.length])
    ];

    const ctxRev = document.getElementById("revenueChart").getContext("2d");
    if (revenueChart) revenueChart.destroy();
    revenueChart = new Chart(ctxRev, {
        type: "doughnut",
        data: {
            labels: salesLabels,
            datasets: [{
                data: salesData,
                backgroundColor: salesColors,
                borderWidth: 0,
                hoverOffset: 8
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: ctx => `  ${ctx.label}: ₹${Number(ctx.raw).toFixed(2)}`
                    }
                }
            },
            cutout: "76%",
            animation: { animateScale: true, animateRotate: true }
        }
    });

    // 2. Loans & Advances Chart Data
    const loansList = Array.isArray(d.loans) ? d.loans : [];
    const loanLabels = ["Recovered", ...loansList.map(item => `${item.name} (Outstanding)`)];
    const loanData = [
        Number(d.totalLoansRecovered) || 0,
        ...loansList.map(item => Number(item.amount) || 0)
    ];
    const loanColors = [
        cashCol,
        ...loansList.map((_, i) => loanBase + CHART_ALPHAS[i % CHART_ALPHAS.length])
    ];

    const ctxLoan = document.getElementById("loanChart").getContext("2d");
    if (loanChart) loanChart.destroy();
    loanChart = new Chart(ctxLoan, {
        type: "doughnut",
        data: {
            labels: loanLabels,
            datasets: [{
                data: loanData,
                backgroundColor: loanColors,
                borderWidth: 0,
                hoverOffset: 8
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: ctx => `  ${ctx.label}: ₹${Number(ctx.raw).toFixed(2)}`
                    }
                }
            },
            cutout: "76%",
            animation: { animateScale: true, animateRotate: true }
        }
    });
}

// ============================================================================
// 6. GENERAL LEDGER, DOUBLE-COLUMN CASHBOOK & ADJUSTMENT MODE
// ============================================================================
async function loadLedger() {
    try {
        const result = await apiFetch("/Ledger", { method: "GET" });
        cachedLedgerData = result.data;
        renderFolioCheckboxes(cachedLedgerData.allAccounts || []);
        populateManualEntrySelects(cachedLedgerData.allAccounts || []);
        renderSelectedFolios();
    } catch (err) {
        showToast(err.message, true);
    }
}

function renderFolioCheckboxes(allAccounts) {
    const grid = document.getElementById("folioCheckboxGrid");
    grid.innerHTML = allAccounts.map(acc => {
        const isChecked = selectedFolios.has(acc) ? "checked" : "";
        return `
            <label class="checkbox-label">
                <input type="checkbox" value="${escapeHtml(acc)}" ${isChecked} onchange="toggleFolioSelection(this)">
                ${escapeHtml(acc)}
            </label>
        `;
    }).join("");
}

function populateManualEntrySelects(allAccounts) {
    const optionsHtml = allAccounts.map(acc => `<option value="${escapeHtml(acc)}">${escapeHtml(acc)}</option>`).join("");
    document.getElementById("manualDebitAcc").innerHTML = `<option value="">-- Debit Account --</option>` + optionsHtml;
    document.getElementById("manualCreditAcc").innerHTML = `<option value="">-- Credit Account --</option>` + optionsHtml;
}

function toggleFolioSelection(checkbox) {
    if (checkbox.checked) {
        selectedFolios.add(checkbox.value);
    } else {
        selectedFolios.delete(checkbox.value);
    }
    renderSelectedFolios();
}

function setLedgerMode(mode) {
    currentLedgerMode = mode;
    document.getElementById("btnStandardMode").classList.toggle("active", mode === "view");
    document.getElementById("btnAdjustMode").classList.toggle("active", mode === "adjustment");

    document.getElementById("adjustmentWarning").style.display = mode === "adjustment" ? "block" : "none";
    document.getElementById("btnToggleManualEntry").style.display = mode === "adjustment" ? "inline-block" : "none";
    if (mode !== "adjustment") {
        document.getElementById("manualEntryBox").style.display = "none";
    }
    renderSelectedFolios();
}

function toggleManualEntryBox() {
    const box = document.getElementById("manualEntryBox");
    box.style.display = box.style.display === "block" ? "none" : "block";
}

function renderSelectedFolios() {
    if (!cachedLedgerData) return;
    const container = document.getElementById("ledgerTablesContainer");
    const discountBox = document.getElementById("adminDiscountBox");

    // Show Owner Discount Panel only in Adjustment Mode when Debtors is selected
    const showDiscountPanel = currentLedgerMode === "adjustment" && selectedFolios.has("Debtors");
    discountBox.style.display = showDiscountPanel ? "block" : "none";

    if (selectedFolios.size === 0) {
        container.innerHTML = `
            <div style="text-align: center; color: var(--ink-soft); font-family: 'Caveat', cursive; font-size: 2em; margin-top: 60px;">
                Please open a folio from the list above...
            </div>`;
        return;
    }

    let html = "";
    selectedFolios.forEach(account => {
        if (account === "Cashbook") {
            html += buildCashbookTableHtml(cachedLedgerData.cashbook);
        } else if (cachedLedgerData.accounts && cachedLedgerData.accounts[account]) {
            html += buildStandardLedgerTableHtml(account, cachedLedgerData.accounts[account]);
        }
    });
    container.innerHTML = html;
}

function formatParticularsHtml(row) {
    if (!row) return "";
    const text = escapeHtml(row.particulars);
    if (row.status === "VOID") {
        return `<del>${text} [VOID]</del>`;
    }
    if (currentLedgerMode === "adjustment") {
        return `${text} <button type="button" class="void-btn" onclick="voidTransaction(${row.id})">Void</button>`;
    }
    return text;
}

function buildCashbookTableHtml(cb) {
    const debits = (cb && cb.debits) ? cb.debits : [];
    const credits = (cb && cb.credits) ? cb.credits : [];
    const maxRows = Math.max(debits.length, credits.length);

    let cashDr = 0, cashCr = 0, bankDr = 0, bankCr = 0;
    debits.forEach(d => {
        if (d.status !== "VOID") {
            cashDr += Number(d.cashAmount) || 0;
            bankDr += Number(d.bankAmount) || 0;
        }
    });
    credits.forEach(c => {
        if (c.status !== "VOID") {
            cashCr += Number(c.cashAmount) || 0;
            bankCr += Number(c.bankAmount) || 0;
        }
    });

    const cashBal = Math.abs(cashDr - cashCr);
    const bankBal = Math.abs(bankDr - bankCr);
    const cashTotal = Math.max(cashDr, cashCr);
    const bankTotal = Math.max(bankDr, bankCr);

    let rowsHtml = "";
    for (let i = 0; i < maxRows; i++) {
        const d = i < debits.length ? debits[i] : null;
        const c = i < credits.length ? credits[i] : null;

        rowsHtml += `
            <tr>
                <td>${d ? escapeHtml(d.date) : ""}</td>
                <td class="particulars-col margin-line">${formatParticularsHtml(d)}</td>
                <td></td>
                <td class="amount-col">${d && d.cashAmount > 0 ? d.cashAmount.toFixed(2) : ""}</td>
                <td class="amount-col bank-col">${d && d.bankAmount > 0 ? d.bankAmount.toFixed(2) : ""}</td>

                <td class="center-fold">${c ? escapeHtml(c.date) : ""}</td>
                <td class="particulars-col margin-line">${formatParticularsHtml(c)}</td>
                <td></td>
                <td class="amount-col">${c && c.cashAmount > 0 ? c.cashAmount.toFixed(2) : ""}</td>
                <td class="amount-col bank-col">${c && c.bankAmount > 0 ? c.bankAmount.toFixed(2) : ""}</td>
            </tr>
        `;
    }

    return `
        <div class="table-responsive">
            <div class="account-header">
                <span class="dr-cr">Dr.</span><span>Double-Column Cash Book</span><span class="dr-cr">Cr.</span>
            </div>
            <table class="ledger-table">
                <thead>
                    <tr>
                        <th>Date</th><th class="margin-line">Particulars</th><th>LF</th><th class="amount-col">Cash(₹)</th><th class="amount-col bank-col">Bank(₹)</th>
                        <th class="center-fold">Date</th><th class="margin-line">Particulars</th><th>LF</th><th class="amount-col">Cash(₹)</th><th class="amount-col bank-col">Bank(₹)</th>
                    </tr>
                </thead>
                <tbody>
                    ${rowsHtml}
                    <tr>
                        <td>End Mth</td><td class="particulars-col margin-line" style="color: var(--ink-soft);">To Balance c/d</td><td></td>
                        <td class="amount-col">${cashCr > cashDr ? cashBal.toFixed(2) : ""}</td>
                        <td class="amount-col bank-col">${bankCr > bankDr ? bankBal.toFixed(2) : ""}</td>
                        <td class="center-fold">End Mth</td><td class="particulars-col margin-line" style="color: var(--ink-soft);">By Balance c/d</td><td></td>
                        <td class="amount-col">${cashDr > cashCr ? cashBal.toFixed(2) : ""}</td>
                        <td class="amount-col bank-col">${bankDr > bankCr ? bankBal.toFixed(2) : ""}</td>
                    </tr>
                    <tr style="border-top: 2px solid var(--ink); border-bottom: 4px double var(--ink); background: var(--highlight);">
                        <td></td><td class="particulars-col margin-line" style="text-align: right;"><strong>Total</strong></td><td></td>
                        <td class="amount-col"><strong>${cashTotal.toFixed(2)}</strong></td>
                        <td class="amount-col bank-col"><strong>${bankTotal.toFixed(2)}</strong></td>
                        <td class="center-fold"></td><td class="particulars-col margin-line" style="text-align: right;"><strong>Total</strong></td><td></td>
                        <td class="amount-col"><strong>${cashTotal.toFixed(2)}</strong></td>
                        <td class="amount-col bank-col"><strong>${bankTotal.toFixed(2)}</strong></td>
                    </tr>
                    <tr>
                        <td>1st Mth</td><td class="particulars-col margin-line" style="color: var(--margin); font-weight: 600;">To Balance b/d</td><td></td>
                        <td class="amount-col">${cashDr > cashCr ? cashBal.toFixed(2) : ""}</td>
                        <td class="amount-col bank-col">${bankDr > bankCr ? bankBal.toFixed(2) : ""}</td>
                        <td class="center-fold">1st Mth</td><td class="particulars-col margin-line" style="color: var(--margin); font-weight: 600;">By Balance b/d</td><td></td>
                        <td class="amount-col">${cashCr > cashDr ? cashBal.toFixed(2) : ""}</td>
                        <td class="amount-col bank-col">${bankCr > bankDr ? bankBal.toFixed(2) : ""}</td>
                    </tr>
                </tbody>
            </table>
        </div>
    `;
}

function buildStandardLedgerTableHtml(accountName, accData) {
    const debits = (accData && accData.debits) ? accData.debits : [];
    const credits = (accData && accData.credits) ? accData.credits : [];
    const maxRows = Math.max(debits.length, credits.length);

    let totalDr = 0, totalCr = 0;
    debits.forEach(d => { if (d.status !== "VOID") totalDr += Number(d.amount) || 0; });
    credits.forEach(c => { if (c.status !== "VOID") totalCr += Number(c.amount) || 0; });

    const balance = Math.abs(totalDr - totalCr);
    const grandTotal = Math.max(totalDr, totalCr);
    const isDrBal = totalDr > totalCr;
    const isCrBal = totalCr > totalDr;

    let rowsHtml = "";
    for (let i = 0; i < maxRows; i++) {
        const d = i < debits.length ? debits[i] : null;
        const c = i < credits.length ? credits[i] : null;

        rowsHtml += `
            <tr>
                <td>${d ? escapeHtml(d.date) : ""}</td>
                <td class="particulars-col margin-line">${formatParticularsHtml(d)}</td>
                <td></td>
                <td class="amount-col">${d ? Number(d.amount).toFixed(2) : ""}</td>

                <td class="center-fold">${c ? escapeHtml(c.date) : ""}</td>
                <td class="particulars-col margin-line">${formatParticularsHtml(c)}</td>
                <td></td>
                <td class="amount-col">${c ? Number(c.amount).toFixed(2) : ""}</td>
            </tr>
        `;
    }

    let cdRowHtml = "";
    if (isCrBal) {
        cdRowHtml = `
            <tr>
                <td>End Mth</td><td class="particulars-col margin-line" style="color: var(--ink-soft);">To Balance c/d</td><td></td><td class="amount-col">${balance.toFixed(2)}</td>
                <td class="center-fold"></td><td class="margin-line"></td><td></td><td></td>
            </tr>`;
    } else if (isDrBal) {
        cdRowHtml = `
            <tr>
                <td></td><td class="margin-line"></td><td></td><td></td>
                <td class="center-fold">End Mth</td><td class="particulars-col margin-line" style="color: var(--ink-soft);">By Balance c/d</td><td></td><td class="amount-col">${balance.toFixed(2)}</td>
            </tr>`;
    }

    let bdRowHtml = "";
    if (isDrBal) {
        bdRowHtml = `
            <tr>
                <td>1st Mth</td><td class="particulars-col margin-line" style="color: var(--margin); font-weight: 600;">To Balance b/d</td><td></td><td class="amount-col">${balance.toFixed(2)}</td>
                <td class="center-fold"></td><td class="margin-line"></td><td></td><td></td>
            </tr>`;
    } else if (isCrBal) {
        bdRowHtml = `
            <tr>
                <td></td><td class="margin-line"></td><td></td><td></td>
                <td class="center-fold">1st Mth</td><td class="particulars-col margin-line" style="color: var(--margin); font-weight: 600;">By Balance b/d</td><td></td><td class="amount-col">${balance.toFixed(2)}</td>
            </tr>`;
    }

    return `
        <div class="table-responsive">
            <div class="account-header">
                <span class="dr-cr">Dr.</span><span>${escapeHtml(accountName)}</span><span class="dr-cr">Cr.</span>
            </div>
            <table class="ledger-table">
                <thead>
                    <tr>
                        <th>Date</th><th class="margin-line">Particulars</th><th>JF</th><th class="amount-col">Amount(₹)</th>
                        <th class="center-fold">Date</th><th class="margin-line">Particulars</th><th>JF</th><th class="amount-col">Amount(₹)</th>
                    </tr>
                </thead>
                <tbody>
                    ${rowsHtml}
                    ${cdRowHtml}
                    <tr style="border-top: 2px solid var(--ink); border-bottom: 4px double var(--ink); background: var(--highlight);">
                        <td></td><td class="particulars-col margin-line" style="text-align: right;"><strong>Total</strong></td><td></td><td class="amount-col"><strong>${grandTotal.toFixed(2)}</strong></td>
                        <td class="center-fold"></td><td class="particulars-col margin-line" style="text-align: right;"><strong>Total</strong></td><td></td><td class="amount-col"><strong>${grandTotal.toFixed(2)}</strong></td>
                    </tr>
                    ${bdRowHtml}
                </tbody>
            </table>
        </div>
    `;
}

async function voidTransaction(transactionId) {
    if (!confirm(`Are you sure you want to VOID Transaction #${transactionId}? This action is permanent.`)) {
        return;
    }
    try {
        const params = new URLSearchParams({ action: "void", transactionId: String(transactionId) });
        const res = await apiFetch("/Ledger", { method: "POST", body: params.toString() });
        showToast(res.message);
        await loadLedger();
    } catch (err) {
        showToast(err.message, true);
    }
}

// ============================================================================
// 7. ACCOUNTS RECEIVABLE (DEBTORS) & LOANS MANAGEMENT
// ============================================================================
async function loadDebtors() {
    try {
        const result = await apiFetch("/Debtors", { method: "GET" });
        const debtors = result.data.debtors || [];
        const grid = document.getElementById("debtorsGrid");

        if (debtors.length === 0) {
            grid.innerHTML = `<div class="empty-state">No outstanding customer dues. All Accounts Receivable are settled!</div>`;
            return;
        }

        grid.innerHTML = debtors.map(d => `
            <div class="account-card">
                <h3 class="card-name">${escapeHtml(d.name)}</h3>
                <div class="card-amount">₹${Number(d.amount).toFixed(2)}</div>
                <form class="settle-form" onsubmit="handleSettleDebt(event, '${escapeHtml(d.name)}')">
                    <input type="number" step="0.01" max="${Number(d.amount).toFixed(2)}" name="amount" class="settle-input" value="${Number(d.amount).toFixed(2)}" required>
                    <select name="paymentMethod" class="settle-select">
                        <option value="Cashbook">Settled via Cash</option>
                        <option value="Bank A/c">Settled via UPI / Bank</option>
                    </select>
                    <button type="submit" class="btn-settle">Settle Receivable</button>
                </form>
            </div>
        `).join("");
    } catch (err) {
        showToast(err.message, true);
    }
}

async function handleSettleDebt(event, customerName) {
    event.preventDefault();
    const form = event.target;
    const amount = form.amount.value;
    const paymentMethod = form.paymentMethod.value;

    try {
        const params = new URLSearchParams({ customerName, amount, paymentMethod });
        const res = await apiFetch("/SettleDebt", { method: "POST", body: params.toString() });
        showToast(res.message);
        await loadDebtors();
    } catch (err) {
        showToast(err.message, true);
    }
}

async function loadLoans() {
    try {
        const result = await apiFetch("/Loans", { method: "GET" });
        const loans = result.data.loans || [];
        const grid = document.getElementById("loansGrid");

        if (loans.length === 0) {
            grid.innerHTML = `<div class="empty-state">No outstanding loans currently. The books are clear!</div>`;
            return;
        }

        grid.innerHTML = loans.map(l => `
            <div class="account-card loan-card">
                <h3 class="card-name">${escapeHtml(l.name)}</h3>
                <div class="card-amount">₹${Number(l.amount).toFixed(2)}</div>
                <form class="settle-form" onsubmit="handleSettleLoan(event, '${escapeHtml(l.name)}')">
                    <input type="number" step="0.01" max="${Number(l.amount).toFixed(2)}" name="amount" class="settle-input" value="${Number(l.amount).toFixed(2)}" required>
                    <select name="paymentMethod" class="settle-select">
                        <option value="Cashbook">Repaid via Cash</option>
                        <option value="Bank A/c">Repaid via UPI / Bank</option>
                    </select>
                    <button type="submit" class="btn-settle loan-settle">Mark Repaid</button>
                </form>
            </div>
        `).join("");
    } catch (err) {
        showToast(err.message, true);
    }
}

async function handleSettleLoan(event, personName) {
    event.preventDefault();
    const form = event.target;
    const amount = form.amount.value;
    const paymentMethod = form.paymentMethod.value;

    try {
        const params = new URLSearchParams({ personName, amount, paymentMethod });
        const res = await apiFetch("/SettleLoan", { method: "POST", body: params.toString() });
        showToast(res.message);
        await loadLoans();
    } catch (err) {
        showToast(err.message, true);
    }
}

// ============================================================================
// 8. EVENT LISTENERS (LOGIN, LOGOUT, SALE, LOAN, DISCOUNT, MANUAL ENTRY)
// ============================================================================
document.addEventListener("DOMContentLoaded", () => {
    // Restore saved theme
    if (localStorage.getItem("cyberCafeTheme") === "dark") {
        document.body.classList.add("dark-mode");
        const themeBtn = document.getElementById("themeBtn");
        if (themeBtn) themeBtn.innerHTML = "Day Mode";
    }

    // Check active Supabase session
    checkSession();

    // 1. Owner Login Form
    const loginForm = document.getElementById("loginForm");
    if (loginForm) {
        loginForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            const email = document.getElementById("loginEmail").value.trim();
            const password = document.getElementById("loginPassword").value;
            const errorBox = document.getElementById("loginError");
            const loginBtn = document.getElementById("loginBtn");

            errorBox.style.display = "none";
            loginBtn.textContent = "Authenticating...";

            const { error } = await supabaseClient.auth.signInWithPassword({ email, password });
            loginBtn.textContent = "Unlock Ledger";

            if (error) {
                errorBox.textContent = error.message;
                errorBox.style.display = "block";
                return;
            }
            showToast("Owner authenticated. Welcome back!");
            await checkSession();
        });
    }

    // 2. Logout Button
    const logoutBtn = document.getElementById("logoutBtn");
    if (logoutBtn) {
        logoutBtn.addEventListener("click", async () => {
            await supabaseClient.auth.signOut();
            showToast("Logged out securely.");
            await checkSession();
        });
    }

    // 3. Record Sale Form
    const saleForm = document.getElementById("saleForm");
    if (saleForm) {
        saleForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            const paymentStatus = document.getElementById("paymentStatus").value;
            const customerName = document.getElementById("customerName").value.trim();

            if (paymentStatus === "Unpaid" && !customerName) {
                showToast("Please enter a customer name for Accounts Receivable (A/R) transactions.", true);
                return;
            }

            try {
                const params = new URLSearchParams(new FormData(saleForm));
                const res = await apiFetch("/SaveSale", { method: "POST", body: params.toString() });
                showToast(res.message);
                saleForm.reset();
                await loadDashboard();
            } catch (err) {
                showToast(err.message, true);
            }
        });
    }

    // 4. Issue Loan Form
    const loanForm = document.getElementById("loanForm");
    if (loanForm) {
        loanForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            try {
                const params = new URLSearchParams(new FormData(loanForm));
                const res = await apiFetch("/IssueLoan", { method: "POST", body: params.toString() });
                showToast(res.message);
                loanForm.reset();
                await loadDashboard();
            } catch (err) {
                showToast(err.message, true);
            }
        });
    }

    // 5. Owner Discount Form (Adjustment Mode)
    const discountForm = document.getElementById("discountForm");
    if (discountForm) {
        discountForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            try {
                const params = new URLSearchParams(new FormData(discountForm));
                const res = await apiFetch("/ApplyDiscount", { method: "POST", body: params.toString() });
                showToast(res.message);
                discountForm.reset();
                selectedFolios.add("Debtors");
                selectedFolios.add("Discount Allowed");
                await loadLedger();
            } catch (err) {
                showToast(err.message, true);
            }
        });
    }

    // 6. Manual Double-Entry Journal Form (Adjustment Mode)
    const manualEntryForm = document.getElementById("manualEntryForm");
    if (manualEntryForm) {
        manualEntryForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            try {
                const params = new URLSearchParams(new FormData(manualEntryForm));
                const res = await apiFetch("/Ledger", { method: "POST", body: params.toString() });
                showToast(res.message);
                manualEntryForm.reset();
                document.getElementById("manualEntryBox").style.display = "none";
                await loadLedger();
            } catch (err) {
                showToast(err.message, true);
            }
        });
    }
});