package com.invoicedesk.auth;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import java.net.URI;

/**
 * Adds the MCP / RFC 9728 challenge to 401 responses so clients can discover
 * where to get a token:
 * WWW-Authenticate: Bearer resource_metadata="...", scope="..."
 */
@ServerFilter({"/mcp", "/mcp/**"})
class WwwAuthenticateChallengeFilter implements Ordered {

    private final String challenge;

    WwwAuthenticateChallengeFilter(ResourceServerConfig config) {
        URI resource = URI.create(config.resourceUri());
        String metadataUrl = resource.getScheme() + "://" + resource.getAuthority()
                + "/.well-known/oauth-protected-resource" + resource.getPath();
        this.challenge = "Bearer resource_metadata=\"%s\", scope=\"%s\""
                .formatted(metadataUrl, String.join(" ", config.scopesSupported()));
    }

    @ResponseFilter
    void addChallenge(MutableHttpResponse<?> response) {
        if (response.status() == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, challenge);
        }
    }

    @Override
    public int getOrder() {
        // Run before (outside) the security filter so we see its rejection
        return ServerFilterPhase.SECURITY.before();
    }
}
