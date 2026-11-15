package com.cybercafe.utils;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JwtHelper {

    // Your exact Supabase project URL
    private static final String SUPABASE_URL = "https://hyrhegfoukbvjyyqobkh.supabase.co";
    
    // PASTE YOUR SUPABASE ANON PUBLIC KEY HERE (The exact same one from frontend/app.js)
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imh5cmhlZ2ZvdWtidmp5eXFvYmtoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA2MTU1MTgsImV4cCI6MjEwNjE5MTUxOH0.nlkwuNmXSPbrM9LoRQqQJKFDDM7JSYZdiw5X5zRdnJU"; 

    public static String getAdminIdFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        
        if (header == null || !header.startsWith("Bearer ")) {
            System.err.println("JWT Error: Missing Authorization header.");
            return null;
        }

        try {
            // Delegate token validation directly to Supabase's live Auth servers
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            // Ask Supabase: "Is this token valid? If so, who does it belong to?"
            HttpRequest authRequest = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/auth/v1/user"))
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", header) // Sends the Bearer token
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(authRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                
                // Safely extract the user's UUID from the Supabase JSON response
                Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                if (m.find()) {
                    return m.group(1); // Returns the verified admin_id!
                }
            } else {
                System.err.println("Supabase Auth Rejected Token: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Network Error calling Supabase: " + e.getMessage());
        }

        return null;
    }
}
