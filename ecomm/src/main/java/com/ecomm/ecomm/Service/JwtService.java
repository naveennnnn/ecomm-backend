package com.ecomm.ecomm.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiry}")
    private long accessExpiry;

    @Value("${jwt.refresh-expiry}")
    private long refreshExpiry;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String uid, String email) {
        return Jwts.builder()
                .subject(uid)
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiry))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * A refresh-token "family" identifier, created once per login. It stays
     * constant across rotations so the server can locate the owning user even
     * after the token value itself has changed.
     */
    public String generateRefreshFamily() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a signed refresh token bound to a family id. Each call produces a
     * unique token (random jti) so rotation always yields a new value, while the
     * embedded family id lets us find the user and detect reuse of old tokens.
     */
    public String generateRefreshToken(String familyId) {
        return Jwts.builder()
                .subject(familyId)
                .claim("typ", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiry))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts the family id from a refresh token, or null if the token is
     * invalid, expired, or not a refresh token.
     */
    public String extractRefreshFamily(String refreshToken) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();
            if (!"refresh".equals(claims.get("typ", String.class))) {
                return null;
            }
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public long getRefreshExpiry() {
        return refreshExpiry;
    }

    public String validateAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
