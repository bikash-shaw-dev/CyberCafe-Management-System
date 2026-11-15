package com.cybercafe.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import jakarta.servlet.http.HttpServletRequest;

public class JwtHelper {
    // Fetches the secret you saved in the Render Environment Variables
    private static final String SECRET = System.getenv("SUPABASE_JWT_SECRET");

    /**
     * Intercepts the Authorization header, verifies the Supabase token, 
     * and returns the unique admin UUID (stored in the 'sub' claim).
     */
    public static String getAdminIdFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        
        if (header == null || !header.startsWith("Bearer ")) {
            return null; // Missing or invalid header
        }
        
        String token = header.substring(7); // Remove "Bearer " prefix
        
        try {
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);
            
            // In Supabase, the user's UUID is always stored in the 'sub' (subject) claim
            return jwt.getSubject();
        } catch (Exception e) {
            System.err.println("JWT Verification Failed: " + e.getMessage());
            return null;
        }
    }
}
