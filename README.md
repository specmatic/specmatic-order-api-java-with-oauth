# Specmatic Sample Application to demonstrate OpenAPI Multiple Security Schemes Support
![Specmatic Sample Application to demonstrate OpenAPI OAuth2 security scheme support](assets/SpecmaticOAuth.gif)

Specs: 
- [POST Endpoints with OAuth2](spec/api_order_post_oauth.yaml) - POST operations protected with OAuth2
- [GET Endpoints with Basic Auth](spec/api_order_get_basic.yaml) - GET operations protected with Basic Authentication
- [DELETE Endpoints with API Key](spec/api_order_delete_apikey.yaml) - DELETE operations protected with API Key

This project demonstrates how to leverage OpenAPI specifications as a Contract Test with Specmatic when the specification includes multiple [security schemes](https://spec.openapis.org/oas/v3.0.1#security-scheme-object) to protect different endpoints based on HTTP methods.

## Security Scheme Configuration

The application implements three different security schemes:
- **OAuth2 (POST endpoints)**: All POST operations require OAuth2 authentication with 'email' scope
- **Basic Authentication (GET endpoints)**: All GET operations require username/password authentication
- **API Key (DELETE endpoints)**: All DELETE operations require an API key in the X-API-Key header

We have used below tools to demonstrate the same.
- Spring Boot
- Keycloak
- Spring Security
- Specmatic
- Open API Specification

The system under test here is a service that implements the OpenAPI specification and acts an OAuth2 resource server.

## Running the application:
In this mode, we'll run Keycloak locally which we will leverage as our OAuth authorization server for POST endpoints.  
We use a Spring Security Configuration (refer ```src/main/java/com/store/config/SecurityConfig.kt```) which implements:
- **POST endpoints**: Secured with OAuth2, requiring a token with the 'email' scope in the Authorization header
- **GET endpoints**: Secured with Basic Authentication, requiring username/password credentials
- **DELETE endpoints**: Secured with API Key authentication, requiring a valid API key in the X-API-Key header

The application validates OAuth2 tokens by calling the ```spring.security.oauth2.resourceserver.jwt.issuer-uri``` url defined in the ```application.properties``` file.  

Here are the steps to the run project in dev mode to take it for a spin.

#### Start Keycloak
From the project root folder, run:

```shell
docker compose up
```
This will start the Keycloak server on localhost:8083 and pre-load with a sample realm called "specmatic".

#### Start the application
From the project root folder, run:

1. For Unix and PowerShell terminal:
```shell
./gradlew clean bootRun --args='--spring.profiles.active=prod'
```

2. For Windows CMD Prompt:
```shell
gradlew.bat clean bootRun --args="--spring.profiles.active=prod"
```

This will start the application on localhost:8080.

#### Generate OAuth token
* Open Postman.  
* Create a new request with method:  ```GET``` and url: ```http://localhost:8080/products/10```  
* Under the authorization tab select type as OAuth 2.0 and click on Get New Access Token button with below details
```
Auth URL = http://localhost:8083/realms/specmatic/protocol/openid-connect/auth
Access Token URL = http://localhost:8083/realms/specmatic/protocol/openid-connect/token
CLient ID = order-api
Client Secret = <blank>
Scope = profile email
```
* And select "Authorize using browser" and click "Get New Access Token"  
* When redirected to browser enter the following user credentials:  
```
username: user1
password: password
```
* Click the Signin button.  
* You should then be able to see the generated access token with a ```Use Token``` button on top right.  
* Click the ```Use Token``` button.  
* This will take you back to your Postman request tab with the oauth token already added to the Authorization header. (The Authorization header might be hidden in the default view, but you can click on the hidden headers to see it)

#### Optional
If you would like to take a look at the realm configuration of the Keycloak server launch admin console with http://localhost8083 in an incognito window (since your regular window you may have already logged in with the realm user credentials above) and login with username: admin and password: admin.

#### Make requests with different authentication methods

##### Testing POST endpoints (OAuth2):
Click on the ```Send``` button in Postman for a POST request. You should see the response for creating/updating resources.

Example POST request to create a product:
```
POST http://localhost:8080/products
Authorization: Bearer <your-oauth-token>
Content-Type: application/json

{
  "name": "New Product",
  "type": "gadget",
  "inventory": 10
}
```

##### Testing GET endpoints (Basic Auth):
Create a new GET request in Postman:
```
GET http://localhost:8080/products/10
```
* Under the Authorization tab, select type as "Basic Auth"
* Enter username: `user` and password: `password`
* Click Send

You should see the following response:  
```json
{
  "name": "XYZ Phone",
  "type": "gadget",
  "inventory": 6,
  "id": 10
}
```

##### Testing DELETE endpoints (API Key):
Create a new DELETE request in Postman:
```
DELETE http://localhost:8080/products/20
```
* Under the Headers tab, add a new header:
  - Key: `X-API-Key`
  - Value: `APIKEY1234`
* Click Send

You should see a success response:
```
success
```

You may want to debug through the application to understand and explore the flow better.

## Running contract tests:
Within the context of contract tests, certain concerns such as authentication and authorization are orthogonal because the priority is to verify if the application is adhering to the specification itself.
However, we do want to make sure that security schemes defined in the specification are also validated at least to the extent that appropriate fields such as headers are populated.

Including other aspects such as having to run a keycloak server locally or pointing to a remote server, impedes the ability to isolate our System Under Test and thereby getting quick feedback.

So in test mode, application uses a dummy security configuration ((refer ```src/test/java/com/store/config/DummySecurityConfig.kt```)) which validates the presence of authentication headers without actually validating the credentials.

Specmatic will use the Order API's OpenAPI specifications, read the security schemes, and generate appropriate authentication headers while making requests.

### Security Schemes in Test Mode

The application now uses three separate OpenAPI specifications, each with its own security scheme:

**1. POST endpoints with OAuth2** (```spec/api_order_post_oauth.yaml```):
```yaml
  securitySchemes:
    oAuth2AuthCode:
      type: oauth2
      description: keycloak based oauth security example
      flows:
        authorizationCode:
          authorizationUrl: http://localhost:8083/realms/specmatic/protocol/openid-connect/auth
          tokenUrl: http://localhost:8083/realms/specmatic/protocol/openid-connect/token
          scopes:
            email: email

security:
  - oAuth2AuthCode: []
```

**2. GET endpoints with Basic Auth** (```spec/api_order_get_basic.yaml```):
```yaml
  securitySchemes:
    basicAuth:
      type: http
      scheme: basic
      description: Basic Authentication with username and password

security:
  - basicAuth: []
```

**3. DELETE endpoints with API Key** (```spec/api_order_delete_apikey.yaml```):
```yaml
  securitySchemes:
    apiKeyAuth:
      type: apiKey
      in: header
      name: X-API-Key
      description: API Key based authentication

security:
  - apiKeyAuth: []
```

Specmatic will check if these security schemes are defined in the ```specmatic.yaml``` configuration.  
If found, it will use the configured values; otherwise, it will auto-generate appropriate authentication headers.

The security schemes are defined in ```specmatic.yaml```:
```yaml
specs:
  - spec:
      id: orderPostOAuthSpec
      securitySchemes:
        oAuth2AuthCode:
          type: oauth2
          token: ${OAUTH_TOKEN:OAUTH1234}
  - spec:
      id: orderGetBasicAuthSpec
      securitySchemes:
        basicAuth:
          type: http
          token: ${BASIC_AUTH_TOKEN:Basic dXNlcjpwYXNzd29yZA==}
  - spec:
      id: orderDeleteApiKeySpec
      securitySchemes:
        apiKeyAuth:
          type: apiKey
          value: ${API_KEY:APIKEY1234}
```

#### Running Tests
Go to the ContractTest class at ```src/test/java/com/store/ContractTest```  
Run the contract tests (Ctrl+Shift+R)  
All tests should pass.  

You should see different authentication headers set based on the HTTP method:

**For POST requests (OAuth2):**
```
Request to http://localhost:8080
    POST /products
    Authorization: Bearer OAUTH1234
    Accept-Charset: UTF-8
    Accept: */*
    Content-Type: application/json
```

**For GET requests (Basic Auth):**
```
Request to http://localhost:8080
    GET /products/10
    Authorization: Basic dXNlcjpwYXNzd29yZA==
    Accept-Charset: UTF-8
    Accept: */*
```

**For DELETE requests (API Key):**
```
Request to http://localhost:8080
    DELETE /products/20
    X-API-Key: APIKEY1234
    Accept-Charset: UTF-8
    Accept: */*
```

#### Running tests with Docker Compose
This mode runs all required components using containers:
- Keycloak (OAuth server)
- Order API (Spring Boot application)
- Specmatic test runner

From the project root, run:

```shell
docker compose -f docker-compose-test.yaml up --build specmatic-test
```

Expected result:
- Compose command exits with code `0`
- Specmatic output ends with `Failures: 0`

Generated test reports:
- `build/reports/specmatic/test/html/index.html`
- `build/reports/specmatic/test/ctrf/ctrf-report.json`

Cleanup after run:

```shell
docker compose -f docker-compose-test.yaml down
```
