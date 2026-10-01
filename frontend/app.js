// Replace this URL with your actual live Render Web Service URL (no trailing slash)
const API_BASE_URL = "https://cybercafe-management-system.onrender.com";

const saleForm = document.getElementById("saleForm");
const submitBtn = document.getElementById("submitBtn");
const statusMessage = document.getElementById("statusMessage");

saleForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    submitBtn.disabled = true;
    submitBtn.textContent = "Saving to Cloud...";
    statusMessage.style.display = "none";

    // Convert form fields into URL-encoded format for Java Servlet request.getParameter()
    const formData = new URLSearchParams(new FormData(saleForm));

    try {
        const response = await fetch(`${API_BASE_URL}/SaveSaleServlet`, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
            body: formData.toString(),
        });

        if (response.ok) {
            statusMessage.style.display = "block";
            statusMessage.style.background = "#064e3b";
            statusMessage.style.color = "#6ee7b7";
            statusMessage.textContent = "✅ Transaction saved to Supabase Cloud!";
            saleForm.reset();
        } else {
            throw new Error(`Server returned status ${response.status}`);
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