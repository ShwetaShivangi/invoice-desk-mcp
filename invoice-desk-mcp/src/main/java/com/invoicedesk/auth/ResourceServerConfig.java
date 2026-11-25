package com.invoicedesk.auth;

import io.micronaut.context.annotation.ConfigurationProperties;
import java.util.List;

/**
 * Who this MCP server is (its canonical resource URI), which authorization server
 * issues its tokens, and the minimal scopes a client needs for basic use.
 */
@ConfigurationProperties("invoicedesk")
public record ResourceServerConfig(
        String resourceUri,
        String authorizationServer,
        List<String> scopesSupported) {
}
