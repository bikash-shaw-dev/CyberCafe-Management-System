const API_BASE_URL = "https://cybercafe-management-system.onrender.com";

// Supabase Configuration (Project ID: hyrhegfoukbvjyyqobkh)
const SUPABASE_URL = "https://hyrhegfoukbvjyyqobkh.supabase.co";
const SUPABASE_ANON_KEY = "PASTE_YOUR_SUPABASE_ANON_PUBLIC_KEY_HERE";

const supabaseClient = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

const loginSection = document.getElementById("loginSection");
const dashboardSection = document.getElementById("dashboardSection");
const loginForm = document.getElementById("loginForm");
const loginBtn = document.getElementById("loginBtn");
const loginStatus = document.getElementById("loginStatus");
const logoutBtn = document.getElementById("logoutBtn");

const saleForm = document.getElementById("saleForm");
const submitBtn = document.getElementById("submitBtn");
const statusMessage = document.getElementById("statusMessage");

// Toggle UI based on active session
async function checkSession() {
    const { data: { session } } = await supabaseClient.auth.getSession();
    if (session) {
        loginSection.style.display = "none";
        dashboardSection.style.display = "block";
    } else {
        loginSection.style.display = "block";
        dashboardSection.style.display = "none";
    }
}

// 1. Handle Owner Sign In
loginForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    loginBtn.disabled = true;
    loginBtn.textContent = "Signing in...";
    loginStatus.style.display = "none";

    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;

    const { error } = await supabaseClient.auth.signInWithPassword({ email, password });

    if (error) {
        loginStatus.style.display = "block";
        loginStatus.style.background = "#7f1d1d";
        loginStatus.style.color = "#fca5a5";
        loginStatus.textContent = "❌ " + error.message;
    } else {
        loginForm.reset();
        await checkSession();
    }
    loginBtn.disabled = false;
    loginBtn.textContent = "Sign In";
});

// 2. Handle Sign Out
logoutBtn.addEventListener("click", async () => {
    await supabaseClient.auth.signOut();
    await checkSession();
});

// 3. Handle Authenticated Sale Submission
saleForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    submitBtn.disabled = true;
    submitBtn.textContent = "Saving to Cloud...";
    statusMessage.style.display = "none";

    const { data: { session } } = await supabaseClient.auth.getSession();
    if (!session) {
        await checkSession();
        return;
    }

    const formData = new URLSearchParams(new FormData(saleForm));

    try {
        const response = await fetch(`${API_BASE_URL}/SaveSaleServlet`, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                "Authorization": `Bearer ${session.access_token}`
            },
            body: formData.toString(),
        });

        const data = await response.json();

        if (response.ok) {
            statusMessage.style.display = "block";
            statusMessage.style.background = "#064e3b";
            statusMessage.style.color = "#6ee7b7";
            statusMessage.textContent = "✅ " + (data.message || "Transaction saved to Supabase Cloud!");
            saleForm.reset();
        } else {
            throw new Error(data.message || `Server returned status ${response.status}`);
        }
    } catch (error) {
        statusMessage.style.display = "block";
        statusMessage.style.background = "#7f1d1d";
        statusMessage.style.color = "#fca5a5";
        statusMessage.textContent = "❌ Error saving transaction: " + error.message;
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = "Save Transaction";
    }
});

// Check login state on initial page load
checkSession();