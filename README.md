# Specmatic Sample: OpenAPI Multiple Security Schemes (OAuth2 + RBAC + Basic + API Key)

## Architecture

### What this application demonstrates

This sample shows how to contract test a Spring Boot API from a single OpenAPI spec when different endpoints use different authentication schemes and OAuth2 RBAC:
- OAuth2 bearer tokens for role-protected `POST` endpoints
  - Users with the `users` role can place orders via `POST /orders` and `POST /orders/{id}`.
  - Users with the `admins` role can create products via `POST /products` and `POST /products/{id}`.
  - Role-mismatch cases result in forbidden (`403`) scenarios.
- `GET` endpoints: Basic Auth for endpoints secured with HTTP Basic authentication.
- `DELETE` endpoints: API key authentication using the `X-API-Key` header for delete endpoints secured with API keys.

Spec used in this project:

- [`spec/order-api-with-auth.yaml`](spec/order-api-with-auth.yaml)

### Architecture (Manual Run with Keycloak for OAuth2)

![Architecture diagram: Spring Boot Order API with Keycloak OAuth2 support, role-protected OAuth endpoints, plus Basic Auth and API Key auth for other endpoints](assets/SpecmaticOAuth.gif)

### High-level flow

- `Keycloak` acts as the OAuth2 authorization server for real OAuth2 runs.
- `Order API` (Spring Boot) is the system under test and enforces:
  - OAuth2 bearer-token authentication for OAuth-protected endpoints
  - RBAC on OAuth-protected endpoints using the `users` and `admins` roles
  - Basic Auth for endpoints secured with HTTP Basic authentication
  - API key authentication for endpoints secured with `X-API-Key`
- `Specmatic` reads the OpenAPI spec and sends requests with the appropriate auth headers during contract tests.
- OAuth/RBAC contract examples use Specmatic fixtures:
  - A `before` fixture fetches an OAuth token from Keycloak before the protected API request runs.
  - The fixture captures `access_token` as `ACCESS_TOKEN`.
  - The contract example then uses the captured value as `Authorization: Bearer $(ACCESS_TOKEN)`.
- Fast local contract tests keep this flow mocked internally.
- Testcontainers-based contract tests run the same fixture-driven flow against real Keycloak.

Relevant code:

- Prod security config: [`src/main/java/com/store/config/SecurityConfig.kt`](src/main/java/com/store/config/SecurityConfig.kt)
- Test-mode dummy security config: [`src/test/java/com/store/config/DummySecurityConfig.kt`](src/test/java/com/store/config/DummySecurityConfig.kt)
- Dummy auth header validation filter: [`src/test/java/com/store/security/DummySecurityFilter.kt`](src/test/java/com/store/security/DummySecurityFilter.kt)
- Specmatic config (security tokens + base URL): [`specmatic.yaml`](specmatic.yaml)

## Contract Tests (Different Modes)

### How test mode works

For fast local contract tests, the app runs in test mode and mocks OAuth token retrieval internally instead of talking to Keycloak.
This keeps the local JUnit contract test focused on API behavior and contract conformance.

The OAuth/RBAC examples still model the real flow using Specmatic fixtures.
The `before` fixture performs the token request and captures `ACCESS_TOKEN`, and the protected API request uses that captured token.
In the fast local JUnit test, the OAuth fetch is mocked internally. In the Testcontainers test, the same fixture flow calls real Keycloak.

Use the Testcontainers mode when you want to verify the application against a real OAuth issuer, real JWTs, and role-specific access rules.

### Mode 1: Local JUnit contract test (fastest)

This starts the Spring app in-process using the `test` profile and runs Specmatic contract tests against it. OAuth token fetching is mocked internally for this mode.

- Test class: [`src/test/java/com/store/ContractTest.java`](src/test/java/com/store/ContractTest.java)
- Command:

```shell
./gradlew test --tests com.store.ContractTest
```

When to use:

- Fast feedback while developing endpoints/spec
- No Keycloak required
- No Docker required

### Mode 2: JUnit + Testcontainers (closer to real OAuth flow)

This mode runs:

- Spring Boot app in `prod` profile
- Keycloak in a Testcontainer
- Specmatic in a Testcontainer

The OAuth/RBAC contract examples use `before` fixtures to fetch real role-specific OAuth tokens from Keycloak before the protected API requests run.
This is the main verification path for the real OAuth flow and RBAC behavior.

- Test class: [`src/test/java/com/store/ContractTestUsingTestContainerTest.java`](src/test/java/com/store/ContractTestUsingTestContainerTest.java)
- Command:

```shell
./gradlew test --tests com.store.ContractTestUsingTestContainerTest
```

When to use:

- Validate the app with real Keycloak + JWT issuer configuration
- Verify role-specific access for `users` and `admins` OAuth tokens
- Reproduce an environment closer to deployment while still running from JUnit

Prerequisite:

- Docker running locally

### Mode 3: Docker Compose end-to-end contract test

This mode runs all components in containers:

- Keycloak
- Order API
- Specmatic test runner

Run:

```shell
./gradlew clean assemble
```

```shell
docker compose -f docker-compose-test.yaml up --build specmatic-test
```

Expected result:

- Compose exits with code `0`
- Specmatic output includes `Failures: 0`

Reports generated:

- `build/reports/specmatic/test/html/index.html`
- `build/reports/specmatic/test/ctrf/ctrf-report.json`

Cleanup:

```shell
docker compose -f docker-compose-test.yaml down
```

## OAuth Fixtures Used By The Contract Examples

OAuth/RBAC behavior is represented through Specmatic fixtures in the contract example JSON files.

Each OAuth example has this structure:

1. A `before` fixture calls the Keycloak token endpoint:
   ```text
   POST /realms/specmatic/protocol/openid-connect/token
   ```

2. The fixture sends the password-grant form body using either the user credentials or the admin credentials:
   ```text
   grant_type=password&client_id=order-api&username=...&password=...
   ```

3. The fixture expects a `200` response from Keycloak and captures the token:
   ```json
   {
     "access_token": "(ACCESS_TOKEN:string)"
   }
   ```

4. The protected API request then uses the captured token:
   ```json
   {
     "Authorization": "Bearer $(ACCESS_TOKEN)"
   }
   ```

This means the examples verify both sides of the RBAC rule:
- `users` tokens are accepted for order operations and rejected for product operations.
- `admins` tokens are accepted for product operations and rejected for order operations.

## What To Look For In Contract Test Logs

The most useful thing in the logs is whether Specmatic is using the correct auth header for each secured operation, and whether OAuth-protected endpoints receive a token with the expected role.

### Expected OAuth/RBAC behavior

- `POST /orders` -> `Authorization: Bearer ...` with the `users` role
- `POST /orders/{id}` -> `Authorization: Bearer ...` with the `users` role
- `POST /products` -> `Authorization: Bearer ...` with the `admins` role
- `POST /products/{id}` -> `Authorization: Bearer ...` with the `admins` role

### Other authentication schemes

For endpoints secured with Basic Auth or API key authentication, Specmatic should send the corresponding header configured for that security scheme.
Examples:

```text
Request to http://localhost:8080
    GET /products/10
    Authorization: Basic dXNlcjpwYXNzd29yZA==
```

```text
Request to http://localhost:8080
    DELETE /products/20
    X-API-Key: APIKEY1234
```

### What success looks like

- Specmatic finishes with `Failures: 0`.
- OAuth-protected endpoints do not repeatedly fail with unexpected `401 Unauthorized` or `403 Forbidden` responses.
- The expected forbidden examples return `403 Forbidden`.
- Requests use the authentication scheme expected by the OpenAPI operation.
- In Testcontainers mode, `before` fixtures successfully fetch real tokens from Keycloak.
- `users` tokens are accepted for order endpoints and rejected for product endpoints.
- `admins` tokens are accepted for product endpoints and rejected for order endpoints.

### Common failure signals (and what they usually mean)

- `401 Unauthorized` on an OAuth-protected endpoint:
  - Missing/invalid bearer header
  - In real OAuth modes, token fetch / Keycloak / issuer config problem
- Unexpected `403 Forbidden` on an OAuth-protected endpoint:
  - Token is valid, but does not have the role required by the endpoint
  - `user` token used for an admin-only product endpoint
  - `admin` token used for a user-only order endpoint
  - Role mapping not configured as expected in Keycloak
- `401 Unauthorized` on a Basic Auth endpoint:
  - Missing or malformed Basic Auth header
- `401 Unauthorized` on an API key endpoint:
  - Missing `X-API-Key`
- Connection errors to `localhost:8080` / `order-api:8080` / `keycloak:8080`:
  - App or Keycloak not started yet
  - Wrong `APP_BASE_URL` or `KEYCLOAK_BASE_URL`

### Where Specmatic gets auth values from

OAuth/RBAC examples fetch tokens through `before` fixtures:
- The fixture calls Keycloak's token endpoint.
- The fixture captures `access_token` as `ACCESS_TOKEN`.
- The protected API request uses `Authorization: Bearer $(ACCESS_TOKEN)`.

In the local JUnit contract test, that OAuth fetch is mocked internally.

In the Testcontainers contract test, the fixture fetches a real role-specific OAuth token from Keycloak before the protected API request runs.

Other configured auth values come from [`specmatic.yaml`](specmatic.yaml):
- Basic Auth token value, for example `BASIC_AUTH_TOKEN` with default `dXNlcjpwYXNzd29yZA==`
- API key value, for example `API_KEY` with default `APIKEY1234`

## Run The Application Manually (and test with curl)

This is for manually trying the app with real auth behavior using the `prod` profile.

### 1. Start Keycloak

From the project root:

```shell
docker compose up
```

This starts Keycloak on `http://localhost:8083` and imports the `specmatic` realm.

### 2. Start the Spring Boot application (`prod` profile)

Unix / macOS / PowerShell:

```shell
./gradlew clean bootRun --args='--spring.profiles.active=prod'
```

Windows CMD:

```shell
gradlew.bat clean bootRun --args="--spring.profiles.active=prod"
```

App runs on `http://localhost:8080`.

### 3. Try endpoints with `curl`

#### Fetch OAuth tokens from Keycloak

Fetch a token for a user with the `users` role:

```shell
USER_TOKEN=$(curl -fsS -X POST http://localhost:8083/realms/specmatic/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=order-api" \
  --data-urlencode "username=user1" \
  --data-urlencode "password=password" \
  --data-urlencode "scope=profile email" | jq -r '.access_token')
```

Fetch a token for a user with the `admins` role:

```shell
ADMIN_TOKEN=$(curl -fsS -X POST http://localhost:8083/realms/specmatic/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=order-api" \
  --data-urlencode "username=admin1" \
  --data-urlencode "password=password" \
  --data-urlencode "scope=profile email" | jq -r '.access_token')
```

If `jq` is not installed, copy the `access_token` manually from the JSON response.

If your imported realm uses different sample usernames or passwords, update the `username` and `password` values in the commands above.

#### Create Order endpoint using a `users` token

```shell
curl -i -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "count": 1,
    "productid": 10,
    "status": "pending"
  }'
```

Expected: success response for the order operation with an `id` field in the response body.

#### Update order-by-id endpoint using a `users` token

```shell
curl -i -X POST http://localhost:8080/orders/10 \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "count": 1,
    "productid": 10,
    "status": "fulfilled"
  }'
```

Expected: success response with status code `200`.

#### Create Product endpoint using an `admin` token

```shell
curl -i -X POST http://localhost:8080/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "other",
    "inventory": 100,
    "name": "Test Product"
  }'
```

Expected: success response for the product operation, with `id` field in the response body.

#### Update Product-by-id endpoint using an `admin` token

```shell
curl -i -X POST http://localhost:8080/products/20 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "other",
    "inventory": 100,
    "name": "Test Product"
  }'
```

Expected: success response, with status code `200`.

#### Forbidden RBAC checks

A valid token with the wrong role should return `403 Forbidden`.

Admin token against an order endpoint:

```shell
curl -i -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "count": 1,
    "productid": 10,
    "status": "fulfilled"
  }'
```

Expected: `403 Forbidden`.

User token against a product endpoint:

```shell
curl -i -X POST http://localhost:8080/products \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "other",
    "inventory": 100,
    "name": "Test Product"
  }'
```

Expected: `403 Forbidden`.

#### Basic Auth endpoint

```shell
curl -i -u user:password http://localhost:8080/products/10
```

Expected: success response from the Basic Auth secured endpoint.

#### API key endpoint

```shell
curl -i -X DELETE http://localhost:8080/products/20 \
  -H "X-API-Key: APIKEY1234"
```

Expected: success response, with status code `200`.

## Security Schemes in the OpenAPI Spec

This project uses one OpenAPI spec with multiple security schemes in [`spec/order-api-with-auth.yaml`](spec/order-api-with-auth.yaml):

- `oAuth2AuthCode` (OAuth2)
- `basicAuth` (HTTP Basic)
- `apiKeyAuth` (header API key)

OAuth2-protected operations also use role-based access control in the application:
- `user` role for order operations
- `admin` role for product operations

Specmatic maps security schemes to configured auth values and fixture-generated values. For OAuth/RBAC examples, the value comes from the `before` fixture capture. For the mocked local contract test, the OAuth fetch is mocked internally. For the Testcontainers contract test, the fixture fetches real role-specific OAuth tokens from Keycloak.

## Optional: Inspect Keycloak Realm Setup

You can inspect the imported realm in the Keycloak admin console:

- URL: `http://localhost:8083`
- Admin username: `admin`
- Admin password: `admin`
