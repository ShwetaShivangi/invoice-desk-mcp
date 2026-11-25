package com.invoicedesk.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/** OAuth 2.0 Protected Resource Metadata document (RFC 9728). */
@Serdeable
public record ProtectedResourceMetadata(
        @JsonProperty("resource") String resource,
        @JsonProperty("authorization_servers") List<String> authorizationServers,
        @JsonProperty("scopes_supported") List<String> scopesSupported,
        @JsonProperty("bearer_methods_supported") List<String> bearerMethodsSupported,
        @JsonProperty("resource_name") String resourceName) {
}
