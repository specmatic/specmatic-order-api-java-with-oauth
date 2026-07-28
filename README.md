# Specmatic Sample: Order API Security

This Spring Boot sample demonstrates an Order API with OAuth 2.0 operation scopes, HTTP Basic authentication, API-key authentication, and mutual TLS (mTLS).

## Contract and application-owned examples

The OpenAPI contract is centrally owned by [`specmatic/labs-contracts`](https://github.com/specmatic/labs-contracts) at `openapi/security/order-api-with-auth.yaml`.

[`specmatic.yaml`](specmatic.yaml) uses that Git source with `matchBranch: true`. Set both ref variables to the application branch when running contract tests locally:

```shell
export GITHUB_REF_NAME="$(git branch --show-current)"
export GITHUB_HEAD_REF="$GITHUB_REF_NAME"
```

This lets Specmatic check out the matching branch in the central contract repository. The application-owned OAuth fixtures live in [`contract_examples`](contract_examples) and are loaded explicitly through `systemUnderTest.service.data.examples`; they are not adjacent to a copied contract.

## Authentication and authorization

- `POST /orders` and `PATCH /orders/{id}` require `order:create`.
- `POST /products` and `PATCH /products/{id}` require `product:create`.
- `GET` operations use HTTP Basic authentication.
- `DELETE` operations use the `X-API-Key` header.
- `GET /health` is public at the HTTP authorization layer.

Keycloak retains `users` and `admins` roles only to decide scope eligibility. The API authorizes JWT `SCOPE_order:create` and `SCOPE_product:create` authorities; it does not consume Keycloak realm roles.

| Principal | Granted scope | Allowed writes |
| --- | --- | --- |
| `user1` | `order:create` | Orders |
| `service_account` | `product:create` | Products |

A valid token without the required operation scope returns `403 Forbidden`.

## HTTPS and mTLS

The API serves HTTPS on port `8443` and requires a client certificate. Demo-only certificate material is checked in under [`certs`](certs) for local and Docker contract testing. It is excluded from the application image and mounted at runtime for Docker Compose.

Even though `/health` is HTTP-public, it still requires mTLS because TLS completes before Spring receives the request.

The repository-local OpenAPI 3.0 contract configuration does not declare a custom mTLS extension. Specmatic receives the client JKS through `systemUnderTest.service.runOptions.openapi.cert`.

## Contract test modes

### Local JUnit contract test

This starts the application in the `test` profile, uses the in-process mock token server, and runs Specmatic against `https://localhost:8443` with the demo client JKS.

```shell
export GITHUB_REF_NAME="$(git branch --show-current)"
export GITHUB_HEAD_REF="$GITHUB_REF_NAME"
./gradlew test --tests com.store.ContractTest
```

The mock token server preserves the fixture flow: the fixture requests a scope, captures `ACCESS_TOKEN`, and the test security filter authorizes that captured token.

### JUnit with Testcontainers

This runs the production security profile, real Keycloak, and Specmatic Enterprise in containers. The test forwards the current branch to the Specmatic container so it selects the matching central contract branch.

```shell
export GITHUB_REF_NAME="$(git branch --show-current)"
export GITHUB_HEAD_REF="$GITHUB_REF_NAME"
./gradlew test --tests com.store.ContractTestUsingTestContainerTest
```

It also verifies that a request without a client certificate fails during the TLS handshake and that a valid client certificate reaches `/health`.

### Docker Compose end-to-end test

The Dockerfile copies a prebuilt application JAR, so build it before starting Compose:

```shell
./gradlew bootJar
export GITHUB_REF_NAME="$(git branch --show-current)"
export GITHUB_HEAD_REF="$GITHUB_REF_NAME"
docker compose -f docker-compose-test.yaml up --build --abort-on-container-exit --exit-code-from specmatic-test
```

Compose starts Keycloak, the Order API, and Specmatic Enterprise. It forwards both GitHub ref variables to the Specmatic container, mounts `certs` into the API, and uses the client JKS plus an HTTPS mTLS health check before running the contract suite.

Reports are written to:

- `build/reports/specmatic/test/html/index.html`
- `build/reports/specmatic/test/ctrf/ctrf-report.json`

Clean up with:

```shell
docker compose -f docker-compose-test.yaml down --remove-orphans
```

`/health` is excluded from generated Specmatic scenarios with `PATH!=/health`; it remains covered by focused application and TLS tests.

## Manual run

Start Keycloak:

```shell
docker compose up
```

Start the API with the production profile:

```shell
./gradlew bootRun --args='--spring.profiles.active=prod'
```

Request an order token from Keycloak:

```shell
curl -fsS -X POST http://localhost:8083/realms/specmatic/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=order-api' \
  --data-urlencode 'username=user1' \
  --data-urlencode 'password=password' \
  --data-urlencode 'scope=order:create'
```

Request a product token with `service_account` using `scope=product:create`. Supply the demo client certificate, private key, and CA when calling the API:

```shell
curl --cacert certs/ca.crt \
  --cert certs/specmatic-client.crt \
  --key certs/specmatic-client.key \
  https://localhost:8443/health
```

The certificate fixtures and their password are public demo material. Do not reuse them outside this sample.
