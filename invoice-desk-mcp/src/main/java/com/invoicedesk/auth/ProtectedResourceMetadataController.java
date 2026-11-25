package com.invoicedesk.auth;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;

@Controller("/.well-known/oauth-protected-resource")
@Secured(SecurityRule.IS_ANONYMOUS)
class ProtectedResourceMetadataController {

    private final ProtectedResourceMetadata metadata;

    ProtectedResourceMetadataController(ResourceServerConfig config) {
        this.metadata = new ProtectedResourceMetadata(
                config.resourceUri(),
                List.of(config.authorizationServer()),
                config.scopesSupported(),
                List.of("header"),
                "Invoice Desk MCP");
    }

    // RFC 9728 section 3.1: for a resource with a path (https://host/mcp) the
    // well-known URI has the path appended: /.well-known/oauth-protected-resource/mcp
    @Get(uri = "/mcp", produces = MediaType.APPLICATION_JSON)
    ProtectedResourceMetadata forMcpPath() {
        return metadata;
    }

    // Root location, for clients that fall back to it
    @Get(produces = MediaType.APPLICATION_JSON)
    ProtectedResourceMetadata root() {
        return metadata;
    }
}
