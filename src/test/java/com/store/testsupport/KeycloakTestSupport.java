package com.store.testsupport;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public final class KeycloakTestSupport {
    private static final GenericContainer<?> keycloakContainer = new GenericContainer<>("quay.io/keycloak/keycloak:22.0.5")
            .withCommand("start-dev --import-realm")
            .withFileSystemBind("./keycloak", "/opt/keycloak/data/import", BindMode.READ_ONLY)
            .withEnv("KEYCLOAK_IMPORT", "/opt/keycloak/data/import/specmatic-realm.json")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HOSTNAME", "localhost")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/specmatic/.well-known/openid-configuration").forStatusCode(200))
            .withLogConsumer(outputFrame -> System.out.print(outputFrame.getUtf8String()));

    private KeycloakTestSupport() {
    }

    public static void startIfNeeded() {
        if (!keycloakContainer.isRunning()) {
            keycloakContainer.start();
        }
    }

    public static String baseUrl() {
        return "http://" + keycloakContainer.getHost() + ":" + keycloakContainer.getMappedPort(8080);
    }

    public static String containerBaseUrl() {
        return "http://host.docker.internal:" + keycloakContainer.getMappedPort(8080);
    }

    public static String issuerUri() {
        return baseUrl() + "/realms/specmatic";
    }
}
