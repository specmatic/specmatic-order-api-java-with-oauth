package com.store.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class MockTokenServer {
    private HttpServer server;

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/realms/specmatic/protocol/openid-connect/token", this::tokenResponse);
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void tokenResponse(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String username = extractFormValue(requestBody, "username", "user1");
        String requestedScope = extractFormValue(requestBody, "scope", "");
        String grantedScope = grantedScope(username, requestedScope);
        byte[] response = ("{\"access_token\":\"mock-token-" + username + "-scope-" + grantedScope + "\"}").getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private String extractFormValue(String requestBody, String key, String defaultValue) {
        return Arrays.stream(requestBody.split("&"))
                .map(part -> part.split("=", 2))
                .filter(part -> part.length == 2 && part[0].equals(key))
                .map(part -> URLDecoder.decode(part[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElse(defaultValue);
    }

    private String grantedScope(String username, String requestedScope) {
        if (username.equals("user1") && requestedScope.equals("order:create")) {
            return requestedScope;
        }
        if (username.equals("service_account") && requestedScope.equals("product:create")) {
            return requestedScope;
        }
        return "";
    }
}
