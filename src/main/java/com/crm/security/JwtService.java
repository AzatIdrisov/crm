package com.crm.security;

import com.crm.config.JwtProperties;
import com.crm.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // Ключ строится один раз, чтобы не пересоздавать на каждый запрос.
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        // Минимальный набор клеймов: роль и id, чтобы не делать лишние запросы.
        Map<String, Object> claims = Map.of(
                "role", user.getRole().name(),
                "userId", user.getId()
        );
        // subject — уникальный идентификатор пользователя (email).
        return buildToken(claims, user.getEmail().toString());
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> extractor) {
        Claims claims = extractAllClaims(token);
        return extractor.apply(claims);
    }

    public long getExpirationSeconds() {
        return properties.getExpiration().getSeconds();
    }

    private String buildToken(Map<String, Object> extraClaims, String subject) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getExpiration());
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                // issuer помогает отличить токены этого сервиса от других.
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
