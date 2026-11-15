package com.cybercafe.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import jakarta.servlet.http.HttpServletRequest;

public class JwtHelper {

    /**
     * Intercepts the Authorization header, verifies the Supabase token, 
     * and returns the unique admin UUID (stored in the 'sub' claim).
     */
    public static String getAdminIdFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        
        if (header == null || !header.startsWith("Bearer ")) {
            System.err.println("JWT Error: Missing or invalid Authorization header.");
            return null;
        }
        
        String token = header.substring(7); // Remove "Bearer " prefix
        
        // Fetch secret dynamically at runtime to prevent Docker cold-start bugs
        String secret = System.getenv("SUPABASE_JWT_SECRET");
        
        if (secret == null || secret.trim().isEmpty()) {
            System.err.println("JWT FATAL ERROR: SUPABASE_JWT_SECRET environment variable is missing in Render!");
            return null;
        }
        
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);
            
            return jwt.getSubject();
        } catch (Exception e) {
            System.err.println("JWT Verification Failed: " + e.getMessage());
            return null;
        }
    }
}
