package com.invoicedesk;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;

/** Mints JWTs signed with the test-only secret from application-test.yml. */
final class TestTokens {

    static final String SECRET = "test-only-secret-that-is-at-least-32-chars";
    static final String ISSUER = "https://test-issuer.example";
    static final String AUDIENCE = "http://localhost:8080/mcp";

    private TestTokens() {
    }

    static String token(String issuer, String audience, Instant expiresAt) throws JOSEException {
        var claims = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer(issuer)
                .audience(audience)
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .claim("scope", "invoices:read")
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET));
        return jwt.serialize();
    }

    static String valid() throws JOSEException {
        return token(ISSUER, AUDIENCE, Instant.now().plusSeconds(300));
    }
}
