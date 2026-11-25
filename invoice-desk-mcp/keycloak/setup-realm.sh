#!/usr/bin/env bash
# Creates the invoice-desk realm in the local Keycloak (docker compose) using kcadm.
# Safe to re-run: exits early if the realm already exists.
set -euo pipefail
cd "$(dirname "$0")/.."

REALM=invoice-desk
MCP_AUDIENCE="${MCP_RESOURCE_URI:-http://localhost:8080/mcp}"
KC="docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh"

echo "Waiting for Keycloak..."
until $KC config credentials --server http://localhost:8080 --realm master \
      --user admin --password admin >/dev/null 2>&1; do sleep 2; done

if $KC get "realms/$REALM" >/dev/null 2>&1; then
  echo "Realm '$REALM' already exists - nothing to do."
  exit 0
fi

echo "Creating realm '$REALM'"
$KC create realms -s realm=$REALM -s enabled=true

# A client scope whose Audience mapper binds tokens to the MCP server.
# Optional = clients only get it (and the audience) when they request it.
create_scope() { # name audience
  local id
  id=$($KC create client-scopes -r $REALM -i \
        -s name="$1" -s protocol=openid-connect \
        -s 'attributes."include.in.token.scope"=true' \
        -s 'attributes."display.on.consent.screen"=true')
  $KC create "client-scopes/$id/protocol-mappers/models" -r $REALM \
        -s name="$1-audience" -s protocol=openid-connect \
        -s protocolMapper=oidc-audience-mapper \
        -s "config.\"included.custom.audience\"=$2" \
        -s 'config."access.token.claim"=true' \
        -s 'config."id.token.claim"=false' >/dev/null
  $KC update "realms/$REALM/default-optional-client-scopes/$id" -r $REALM
  echo "$id"
}

echo "Creating client scopes"
INVOICES_SCOPE=$(create_scope "invoices:read" "$MCP_AUDIENCE")
REPORTS_SCOPE=$(create_scope "reports:read" "http://localhost:9090/reports")  # the "other API"

echo "Creating client 'cli-tester' (confidential, client_credentials for curl tests)"
CLI_ID=$($KC create clients -r $REALM -i \
  -s clientId=cli-tester -s publicClient=false -s secret=dev-only-cli-tester-secret \
  -s serviceAccountsEnabled=true -s standardFlowEnabled=false -s directAccessGrantsEnabled=false)
$KC update "clients/$CLI_ID/optional-client-scopes/$INVOICES_SCOPE" -r $REALM
$KC update "clients/$CLI_ID/optional-client-scopes/$REPORTS_SCOPE" -r $REALM

echo "Creating client 'mcp-inspector' (public, authorization code + PKCE)"
INSPECTOR_ID=$($KC create clients -r $REALM -i \
  -s clientId=mcp-inspector -s publicClient=true -s standardFlowEnabled=true \
  -s directAccessGrantsEnabled=false \
  -s 'redirectUris=["http://localhost:6274/*"]' \
  -s 'webOrigins=["http://localhost:6274"]' \
  -s 'attributes."pkce.code.challenge.method"=S256')
$KC update "clients/$INSPECTOR_ID/optional-client-scopes/$INVOICES_SCOPE" -r $REALM

echo "Creating user 'alice' (password: alice)"
$KC create users -r $REALM -s username=alice -s enabled=true \
  -s email=alice@example.com -s emailVerified=true -s firstName=Alice -s lastName=Test
$KC set-password -r $REALM --username alice --new-password alice

echo
echo "Done. Issuer: http://localhost:8180/realms/$REALM"
echo "cli-tester secret: dev-only-cli-tester-secret   (local dev only)"
