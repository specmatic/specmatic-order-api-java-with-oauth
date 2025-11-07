# Specmatic Sample Application to demonstrate OpenAPI OAuth2 security scheme support
![Specmatic Sample Application to demonstrate OpenAPI OAuth2 security scheme support](assets/SpecmaticOAuth.gif)

Spec: [OpenAPI Specification](https://github.com/specmatic/specmatic-order-contracts/blob/main/io/specmatic/examples/store/openapi/api_order_with_oauth_v3.yaml) representing endpoints exposed by this application.

This project demonstrates how to leverage OpenAPI specifications as a Contract Test with Specmatic when the specification includes [OAuth2 security schemes](https://spec.openapis.org/oas/v3.0.1#security-scheme-object) to protect the endpoints.
We have used below tools to demonstrate the same.
- Spring Boot
- Keycloak
- Spring Security
- Specmatic
- Open API Specification

The system under test here is a service that implements the OpenAPI specification and acts an OAuth2 resource server.

## Running the application:
In this mode, we'll run Keycloak locally which we will leverage as our OAuth authorization server.  
We use a Spring Security Configuration (refer ```src/main/java/com/store/config/SecurityConfig.kt```) which secures every endpoint with scope : 'email'.  
This means, to access any endpoint, the request must contain a token with the 'email' scope in the Authorization header.  
The application validates the token received in every request by calling the ```spring.security.oauth2.resourceserver.jwt.issuer-uri``` url defined in the ```application.properties``` file.  

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
./mvnw clean spring-boot:run -Dspring-boot.run.profiles=prod
```

2. For Windows CMD Prompt:
```shell
mvnw.cmd clean spring-boot:run -Dspring-boot.run.profiles=prod
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

#### Make request with OAuth token
Click on the ```Send``` button in Postman. You should see the following response:  
```json
{
"name": "XYZ Phone",
"type": "gadget",
"inventory": 6,
"id": 10
}
```

You may want to debug through the application to understand and explore the flow better.

## Running contract tests:
Within the context of contract tests, certain concerns such as authentication and authorization are orthogonal because the priority is to verify if the application is adhering to the specification itself.
However, we do want to make sure that security schemes defined in the specification are also validated at least to the extent that appropriate fields such as headers are populated.

Including other aspects such as having to run a keycloak server locally or pointing to a remote server, impedes the ability to isolate our System Under Test and thereby getting quick feedback.

So in test mode, application uses a dummy security configuration ((refer ```src/main/java/com/store/config/DummySecurityConfig.kt```)) which only checks if the Authorization header contains a string which starts with "Bearer ".
The actual token is not validated.

Specmatic will use the Order API's open api specification, read the security scheme, generate a token and send it in the appropriate header while making a request.
From the open api spec we can see that the service uses the ```oAuth2AuthCode``` security scheme, which is defined as follows:
```yaml
  securitySchemes:
    oAuth2AuthCode:
      type: oauth2
      description: keycloak based oauth security example
      flows:
        authorizationCode:
          authorizationUrl: http://localhost:8083/realms/specmatic/protocol/openid-connect/auth
          tokenUrl: http://localhost:8083/realms/specmatic/protocol/openid-connect/token

security:
  - oAuth2AuthCode: []
```

Specmatic will check if an Open API security scheme named ```oAuth2AuthCode``` is defined in the ```security``` section in specmatic.json.  
If it finds it, it will pick up the token defined for the theme and use it to run tests.  
If no such theme is defined in specmatic.json, Specmatic will auto-generate a random string to be sent as the token.  

We can see that we do have the ```oAuth2AuthCode``` security scheme defined in specmatic.json:
```json
{
  "security": {
    "OpenAPI": {
      "securitySchemes": {
        "oAuth2AuthCode": {
          "type": "oauth2",
          "token": "OAUTH1234"
        }
      }
    }
  }
}
```

#### Running Tests
Go to the ContractTest class at ```src/test/java/com/store/ContractTest```  
Run the contract tests (Ctrl+Shift+R)  
All tests should pass.  
You should see the Authorization header set for every test as:
```
Request to http://localhost:8080 at 2023-9-27 6:59:20.607
    GET /products/10
    Authorization: Bearer OAUTH1234
    Accept-Charset: UTF-8
    Accept: */*
```
