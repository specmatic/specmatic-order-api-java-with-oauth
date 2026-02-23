# Specmatic Sample: OpenAPI Multiple Security Schemes (OAuth2 + Basic + API Key)

## Architecture

### What this application demonstrates

This sample shows how to contract test a Spring Boot API from a single OpenAPI spec when different endpoints use different authentication schemes:

- `POST` endpoints: OAuth2 bearer token
- `GET` endpoints: Basic Auth
- `DELETE` endpoints: API key (`X-API-Key`)

Spec used in this project:

- [`spec/order-api-with-auth.yaml`](spec/order-api-with-auth.yaml)

### Architecture (Manual Run with Keycloak for OAuth2)

![Architecture diagram: Spring Boot Order API with Keycloak OAuth2 support for POST endpoints, plus Basic Auth and API Key auth for other endpoints](assets/SpecmaticOAuth.gif)

### High-level flow

- `Keycloak` acts as the OAuth2 authorization server (used for `POST` endpoint tokens).
- `Order API` (Spring Boot) is the system under test and enforces:
  - OAuth2 for `POST`
  - Basic Auth for `GET`
  - API Key for `DELETE`
- `Specmatic` reads the OpenAPI spec and sends requests with the appropriate auth headers during contract tests.

Relevant code:

- Prod security config: [`src/main/java/com/store/config/SecurityConfig.kt`](src/main/java/com/store/config/SecurityConfig.kt)
- Test-mode dummy security config: [`src/test/java/com/store/config/DummySecurityConfig.kt`](src/test/java/com/store/config/DummySecurityConfig.kt)
- Dummy auth header validation filter: [`src/test/java/com/store/security/DummySecurityFilter.kt`](src/test/java/com/store/security/DummySecurityFilter.kt)
- Specmatic config (security tokens + base URL): [`specmatic.yaml`](specmatic.yaml)

## Contract Tests (Different Modes)

### How test mode works

For fast local contract tests, the app runs with a dummy test security filter (`test` profile) that checks header presence/format but does not validate real credentials/tokens. This keeps contract testing focused on API behavior and contract conformance.

Specmatic still generates/sends auth headers based on the OpenAPI security schemes and `specmatic.yaml`.

### Mode 1: Local JUnit contract test (fastest)

This starts the Spring app in-process using the `test` profile and runs Specmatic contract tests against it.

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

The test fetches a real OAuth token from Keycloak and passes it to Specmatic.

- Test class: [`src/test/java/com/store/ContractTestUsingTestContainerTest.java`](src/test/java/com/store/ContractTestUsingTestContainerTest.java)
- Command:

```shell
./gradlew test --tests com.store.ContractTestUsingTestContainerTest
```

When to use:

- Validate the app with real Keycloak + JWT issuer configuration
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

## What To Look For In Contract Test Logs

The most useful thing in the logs is whether Specmatic is using the correct auth header for each HTTP method.

### Expected auth header by method

- `POST /products` -> `Authorization: Bearer ...`
- `GET /products/{id}` -> `Authorization: Basic ...`
- `DELETE /products/{id}` -> `X-API-Key: ...`

Examples you should see:

```text
Request to http://localhost:8080
    POST /products
    Authorization: Bearer OAUTH1234
```

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

- Specmatic finishes with `Failures: 0`
- No repeated `401 Unauthorized` responses
- Requests match the operation/method you expect from the spec

### Common failure signals (and what they usually mean)

- `401 Unauthorized` on `POST`:
  - Missing/invalid bearer header
  - In real OAuth modes, token fetch / Keycloak / issuer config problem
- `401 Unauthorized` on `GET`:
  - Missing or malformed Basic Auth header
- `401 Unauthorized` on `DELETE`:
  - Missing `X-API-Key`
- Connection errors to `localhost:8080` / `order-api:8080`:
  - App not started yet or wrong `APP_BASE_URL`

### Where Specmatic gets auth tokens from

Configured in [`specmatic.yaml`](specmatic.yaml):

- `OAUTH_TOKEN` (default `OAUTH1234`)
- `BASIC_AUTH_TOKEN` (default `dXNlcjpwYXNzd29yZA==`)
- `API_KEY` (default `APIKEY1234`)

## Run The Application Manually (and test with curl)

This is for manually trying the app with real auth behavior (`prod` profile).

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

#### GET endpoint (Basic Auth)

```shell
curl -i -u user:password http://localhost:8080/products/10
```

Expected: `200 OK` with product JSON.

#### Fetch OAuth token from Keycloak (for POST endpoints)

```shell
TOKEN=$(curl -fsS -X POST http://localhost:8083/realms/specmatic/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=order-api" \
  --data-urlencode "username=user1" \
  --data-urlencode "password=password" \
  --data-urlencode "scope=profile email" | jq -r '.access_token')
```

If `jq` is not installed, copy the `access_token` manually from the JSON response.

#### POST endpoint (OAuth2 bearer token)

```shell
curl -i -X POST http://localhost:8080/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "New Product",
    "type": "gadget",
    "inventory": 10
  }'
```

Expected: success response for create/update.

#### DELETE endpoint (API key)

```shell
curl -i -X DELETE http://localhost:8080/products/20 \
  -H "X-API-Key: APIKEY1234"
```

Expected: success response (for example `success`).

## Security Schemes in the OpenAPI Spec

This project uses one OpenAPI spec with three security schemes in [`spec/order-api-with-auth.yaml`](spec/order-api-with-auth.yaml):

- `oAuth2AuthCode` (OAuth2)
- `basicAuth` (HTTP Basic)
- `apiKeyAuth` (header API key)

Specmatic maps these to configured tokens in [`specmatic.yaml`](specmatic.yaml). If tokens are not explicitly set, Specmatic can fall back to defaults configured there.

## Optional: Inspect Keycloak Realm Setup

You can inspect the imported realm in the Keycloak admin console:

- URL: `http://localhost:8083`
- Admin username: `admin`
- Admin password: `admin`

Use an incognito/private window if you are already logged in as the sample realm user (`user1`).
