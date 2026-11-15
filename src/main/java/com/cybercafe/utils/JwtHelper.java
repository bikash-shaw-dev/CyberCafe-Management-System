package com.cybercafe.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import jakarta.servlet.http.HttpServletRequest;

public class JwtHelper {

    public static String getAdminIdFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        
        if (header == null || !header.startsWith("Bearer ")) {
            System.err.println("JWT Error: Missing or invalid Authorization header.");
            return null;
        }
        
        String token = header.substring(7);
        String secret = System.getenv("SUPABASE_JWT_SECRET");
        
        if (secret == null || secret.trim().isEmpty()) {
            System.err.println("JWT FATAL ERROR: SUPABASE_JWT_SECRET is missing in Render!");
            return null;
        }
        
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            
            // CRITICAL FIX: .acceptLeeway(120) prevents crashes if Render's clock 
            // is slightly behind Supabase's clock when the token is freshly minted.
            JWTVerifier verifier = JWT.require(algorithm)
                                      .acceptLeeway(120) 
                                      .build();
                                      
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getSubject();
            
        } catch (Exception e) {
            // This will print the exact reason to your Render Logs 
            // (e.g., "The signature is invalid" or "Token expired")
            System.err.println("JWT Verification Failed: " + e.getMessage());
            return null;
        }
    }
}
