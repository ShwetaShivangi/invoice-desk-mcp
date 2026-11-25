package com.invoicedesk;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class AuthTest {

    private static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

    @Inject
    @Client("/")
    HttpClient client;

    private HttpStatus callMcp(String bearer) {
        MutableHttpRequest<String> req = HttpRequest.POST("/mcp", TOOLS_LIST)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE);
        if (bearer != null) {
            req.bearerAuth(bearer);
        }
        try {
            return client.toBlocking().exchange(req, String.class).getStatus();
        } catch (HttpClientResponseException e) {
            return e.getStatus();
        }
    }

    @Test
    void noTokenGets401WithChallenge() {
        var e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(
                HttpRequest.POST("/mcp", TOOLS_LIST).contentType(MediaType.APPLICATION_JSON)));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        String header = e.getResponse().getHeaders().get(HttpHeaders.WWW_AUTHENTICATE);
        assertNotNull(header, "401 must carry a WWW-Authenticate challenge");
        assertEquals("Bearer resource_metadata=\"http://localhost:8080/.well-known/oauth-protected-resource/mcp\", "
                + "scope=\"invoices:read\"", header);
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        HttpStatus status = callMcp(TestTokens.valid());
        assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        assertNotEquals(HttpStatus.FORBIDDEN, status);
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        String t = TestTokens.token(TestTokens.ISSUER, "http://localhost:9090/reports",
                Instant.now().plusSeconds(300));
        assertEquals(HttpStatus.UNAUTHORIZED, callMcp(t));
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        String t = TestTokens.token("https://evil.example", TestTokens.AUDIENCE,
                Instant.now().plusSeconds(300));
        assertEquals(HttpStatus.UNAUTHORIZED, callMcp(t));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String t = TestTokens.token(TestTokens.ISSUER, TestTokens.AUDIENCE,
                Instant.now().minusSeconds(3600));
        assertEquals(HttpStatus.UNAUTHORIZED, callMcp(t));
    }

    @Test
    void metadataIsPublic() {
        Map<?, ?> m = client.toBlocking().retrieve(
                HttpRequest.GET("/.well-known/oauth-protected-resource/mcp"), Map.class);
        assertEquals("http://localhost:8080/mcp", m.get("resource"));
        assertEquals(List.of("https://test-issuer.example"), m.get("authorization_servers"));
        assertEquals(List.of("invoices:read"), m.get("scopes_supported"));
        assertEquals(List.of("header"), m.get("bearer_methods_supported"));
    }

    @Test
    void rootMetadataIsPublicToo() {
        Map<?, ?> m = client.toBlocking().retrieve(
                HttpRequest.GET("/.well-known/oauth-protected-resource"), Map.class);
        assertTrue(m.containsKey("authorization_servers"));
    }
}
