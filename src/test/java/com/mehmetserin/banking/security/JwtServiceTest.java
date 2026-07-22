package com.mehmetserin.banking.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtProperties properties(long expirationMinutes) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-must-be-at-least-32-bytes-long");
        properties.setExpirationMinutes(expirationMinutes);
        return properties;
    }

    @Test
    void generatesTokenThatResolvesBackToTheSameUsername() {
        JwtService jwtService = new JwtService(properties(60));

        String token = jwtService.generateToken("alice");

        assertThat(jwtService.extractUsername(token)).contains("alice");
    }

    @Test
    void rejectsTokenSignedWithADifferentKey() {
        JwtService jwtService = new JwtService(properties(60));
        SecretKey otherKey = Keys.hmacShaKeyFor("a-completely-different-signing-key-32-bytes-min".getBytes(StandardCharsets.UTF_8));
        String foreignToken = Jwts.builder().subject("mallory").signWith(otherKey).compact();

        assertThat(jwtService.extractUsername(foreignToken)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwtService = new JwtService(properties(60));
        SecretKey key = Keys.hmacShaKeyFor("unit-test-secret-key-must-be-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("alice")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(1)))
                .signWith(key)
                .compact();

        assertThat(jwtService.extractUsername(expiredToken)).isEmpty();
    }

    @Test
    void rejectsGarbageToken() {
        JwtService jwtService = new JwtService(properties(60));

        assertThat(jwtService.extractUsername("not-a-jwt")).isEmpty();
    }
}
