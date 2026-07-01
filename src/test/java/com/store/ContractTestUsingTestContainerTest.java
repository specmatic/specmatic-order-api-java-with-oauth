package com.store;

import com.store.model.DB;
import com.store.testsupport.KeycloakTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("prod")
@EnabledIf(value = "isNonCIOrLinux", disabledReason = "Run only on Linux in CI; all platforms allowed locally")
public class ContractTestUsingTestContainerTest {
    public static boolean isNonCIOrLinux() {
        return !"true".equals(System.getenv("CI")) || System.getProperty("os.name").toLowerCase().contains("linux");
    }

    private static final GenericContainer<?> testContainer = new GenericContainer<>("specmatic/enterprise:latest")
            .withCommand("test")
            .withEnv("APP_BASE_URL", "http://host.docker.internal:8080")
            .withEnv("KEYCLOAK_USER_USERNAME", "user1")
            .withEnv("KEYCLOAK_USER_PASSWORD", "password")
            .withEnv("KEYCLOAK_ADMIN_USERNAME", "admin1")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "password")
            .withFileSystemBind("./spec", "/usr/src/app/spec", BindMode.READ_ONLY)
            .withFileSystemBind("./specmatic.yaml", "/usr/src/app/specmatic.yaml", BindMode.READ_ONLY)
            .withFileSystemBind("./build/reports/specmatic", "/usr/src/app/build/reports/specmatic", BindMode.READ_WRITE)
            .waitingFor(Wait.forLogMessage(".*Tests run:.*", 1))
            .withExtraHost("host.docker.internal", "host-gateway")
            .withLogConsumer(outputFrame -> System.out.print(outputFrame.getUtf8String()));

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        KeycloakTestSupport.startIfNeeded();
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakTestSupport::issuerUri);
    }

    @BeforeAll
    public static void setup() {
        DB.INSTANCE.resetDB();
        KeycloakTestSupport.startIfNeeded();
        testContainer.withEnv("KEYCLOAK_BASE_URL", KeycloakTestSupport.containerBaseUrl());
    }

    @Test
    public void specmaticContractTest() {
        testContainer.start();
        boolean hasSucceeded = testContainer.getLogs().contains("Failures: 0");
        assertThat(hasSucceeded).withFailMessage("Contract tests have failures").isTrue();
        Integer exitCode;
        try (var waitContainerCmd = testContainer.getDockerClient()
                .waitContainerCmd(testContainer.getContainerId())) {
            exitCode = waitContainerCmd.start().awaitStatusCode();
        }

        assertThat(exitCode).withFailMessage("Some contract test checks have failed and the specmatic test container exited with " + exitCode + " exit code").isZero();
    }
}
