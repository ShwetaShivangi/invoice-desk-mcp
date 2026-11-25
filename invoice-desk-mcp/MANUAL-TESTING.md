# Manual Testing Guide

Test the protected MCP server by hand, step by step. Use two Terminal tabs:
**Tab A** runs the server, **Tab B** runs the checks.

All commands run from the project folder:

```bash
cd ~/Documents/invoice-desk-mcp/invoice-desk-mcp
```

---

## 0. One-time setup

1. **Start Docker Desktop** and wait until it says "Engine running".
2. **Check Java 25:** `java -version`. If it shows another version, run `sdk use java <your-25-version>`.
3. **Make the scripts executable:**
   ```bash
   chmod +x gradlew keycloak/setup-realm.sh
   ```
4. Install `jq` if you don't have it: `brew install jq`

---

## 1. Run the automated tests

```bash
./gradlew clean test
```

**Expect:** `BUILD SUCCESSFUL`. Open the report to see every test:

```bash
open build/reports/tests/test/index.html
```

You should see `AuthTest` (7 tests) and `InvoiceDeskMcpTest` (1 test), all green. These tests don't need Keycloak.

---

## 2. Start Keycloak and create the realm

```bash
docker compose up -d
./keycloak/setup-realm.sh
```

**Expect:** either "Done. Issuer: http://localhost:8180/realms/invoice-desk" or "Realm already exists". It's safe to re-run.

**Look around (optional, good for blog screenshots):** open http://localhost:8180, log in as `admin` / `admin`, switch to the **invoice-desk** realm, and check:
- **Client scopes → invoices:read → Mappers → invoices:read-audience**: Included Custom Audience is `http://localhost:8080/mcp`
- **Clients**: `cli-tester` and `mcp-inspector`
- **Users**: `alice`

> `docker compose down` deletes the container and the realm with it (dev mode keeps data inside the container). Just re-run `setup-realm.sh` after the next `up`. Use `docker compose stop` / `start` to keep it.

---

## 3. Start the MCP server (Tab A)

```bash
MICRONAUT_ENVIRONMENTS=dev ./gradlew run
```

**Expect:** a log line like `Startup completed in ...ms. Server Running: http://localhost:8080`. Leave it running.

`dev` is what tells the server to check token signatures against Keycloak's keys. Without it, every real token is rejected.

---

## 4. Check discovery (Tab B)

```bash
curl -s http://localhost:8080/.well-known/oauth-protected-resource/mcp | jq
```

**Expect:**
```json
{
  "resource": "http://localhost:8080/mcp",
  "authorization_servers": ["http://localhost:8180/realms/invoice-desk"],
  "scopes_supported": ["invoices:read"],
  "bearer_methods_supported": ["header"],
  "resource_name": "Invoice Desk MCP"
}
```

Also try the root location, which should return the same document:
```bash
curl -s http://localhost:8080/.well-known/oauth-protected-resource | jq
```

---

## 5. No token → 401 with a challenge

```bash
curl -si -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

**Expect:**
```
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer resource_metadata="http://localhost:8080/.well-known/oauth-protected-resource/mcp", scope="invoices:read"
```

If the 401 comes back **without** the `WWW-Authenticate` line, the challenge filter isn't running. Tell me and I'll fix it.

---

## 6. Get tokens from Keycloak

Paste these helpers into Tab B (they last for that Terminal session):

```bash
ISSUER=http://localhost:8180/realms/invoice-desk
get_token() {
  curl -s "$ISSUER/protocol/openid-connect/token" \
    -d grant_type=client_credentials -d client_id=cli-tester \
    -d client_secret=dev-only-cli-tester-secret -d scope="$1" | jq -r .access_token
}
claims() { echo "$1" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss, aud, scope, exp}'; }
mcp() { curl -si -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Authorization: Bearer $1" -d "$2"; }

MCP_TOKEN=$(get_token invoices:read)
OTHER_TOKEN=$(get_token reports:read)
claims "$MCP_TOKEN"
claims "$OTHER_TOKEN"
```

**Expect:**
- MCP token: `"aud": ["http://localhost:8080/mcp", "account"]`
- Other token: `"aud": ["http://localhost:9090/reports", "account"]`

Both have the same issuer and both are validly signed. The only difference is who they're *for*. (If `base64 -d` complains about padding, paste the token into https://jwt.io instead.)

---

## 7. Valid token → the tools work

```bash
mcp "$MCP_TOKEN" '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"manual","version":"1"}}}'
mcp "$MCP_TOKEN" '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
mcp "$MCP_TOKEN" '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"listInvoices","arguments":{}}}'
```

**Expect:** `HTTP/1.1 200`, with `listInvoices` in the tools list and the three invoices (INV-1001, INV-1002, INV-1003) in the `tools/call` result.

If you get a 4xx that isn't 401/403 (e.g. 400), auth passed and the MCP layer wants different headers or a different protocol version. Send me the response body.

---

## 8. The skeleton-key test (the important one)

```bash
mcp "$OTHER_TOKEN" '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | head -3
```

**Expect:** `HTTP/1.1 401 Unauthorized`. The token is real, but it isn't for this server.

**Now prove the audience check is what saves you:**
1. In `src/main/resources/application.yml`, comment out the `audience:` line under `claims-validators`
2. Restart the server in Tab A (Ctrl+C, then run it again)
3. Re-run the command above. It now **succeeds**: a token meant for another API can read your invoices.
4. Uncomment the line and restart.

Screenshot both results for the blog.

---

## 9. Expired / tampered tokens

```bash
# tamper: change the last few characters of the signature
mcp "${MCP_TOKEN%?????}AAAAA" '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | head -1   # expect 401
```

Keycloak access tokens expire after 5 minutes by default. Wait 5 minutes and reuse `$MCP_TOKEN` to see a 401 for expiry.

---

## 10. A real MCP client: MCP Inspector

```bash
npx @modelcontextprotocol/inspector
```

It opens a browser tab (http://localhost:6274). Then:

1. **Transport type:** Streamable HTTP
2. **URL:** `http://localhost:8080/mcp`
3. Open the **Authentication / OAuth** settings. Set **Client ID** to `mcp-inspector` and **Scope** to `invoices:read`
4. Click **Connect** (or use the **Auth / OAuth flow** debugger to step through it)
5. You're redirected to Keycloak. Log in as **alice / alice**
6. Back in Inspector: **Tools → List Tools → listInvoices → Run Tool**

**Expect:** the three invoices. Open browser DevTools → **Network** to watch the flow from Part 1: the 401, the metadata fetch, the Keycloak discovery, the authorize redirect with `code_challenge` and `resource`, and the token request.

**If it fails:**
| Symptom | Fix |
|---|---|
| "Invalid redirect URI" on Keycloak page | Check the exact redirect URL Inspector uses (shown in its OAuth settings) and add it to `mcp-inspector` → Valid redirect URIs |
| CORS error in DevTools console | Note which URL (our server or Keycloak) and send it to me |
| Login works but MCP call returns 401 | Decode the token in jwt.io and check `aud` contains `http://localhost:8080/mcp` (Inspector must request `invoices:read`) |

Record this as a GIF for the post.

---

## 11. Tear down

- Tab A: **Ctrl+C** to stop the server
- `docker compose stop` (keeps the realm) or `docker compose down` (deletes it)

