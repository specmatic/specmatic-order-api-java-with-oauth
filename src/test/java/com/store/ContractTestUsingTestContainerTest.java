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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("prod")
@EnabledIf(value = "isNonCIOrLinux", disabledReason = "Run only on Linux in CI; all platforms allowed locally")
public class ContractTestUsingTestContainerTest {
    private static final String CONTRACT_BRANCH = contractBranch();

    public static boolean isNonCIOrLinux() {
        return !"true".equals(System.getenv("CI")) || System.getProperty("os.name").toLowerCase().contains("linux");
    }

    private static final GenericContainer<?> testContainer = new GenericContainer<>("specmatic/enterprise:latest")
            .withCommand("test")
            .withEnv("APP_BASE_URL", "https://host.docker.internal:8443")
            .withEnv("ACTUATOR_URL", "https://host.docker.internal:8443/actuator/mappings")
            .withEnv("SPECMATIC_CLIENT_KEYSTORE", "/usr/src/app/certs/specmatic-client.jks")
            .withEnv("SPECMATIC_CLIENT_KEYSTORE_PASSWORD", "changeit")
            .withEnv("SPECMATIC_CLIENT_KEY_PASSWORD", "changeit")
            .withEnv("KEYCLOAK_USER_USERNAME", "user1")
            .withEnv("KEYCLOAK_USER_PASSWORD", "password")
            .withEnv("KEYCLOAK_SERVICE_ACCOUNT_USERNAME", "service_account")
            .withEnv("KEYCLOAK_SERVICE_ACCOUNT_PASSWORD", "SvcAcct-Products!2026")
            .withEnv("GITHUB_HEAD_REF", CONTRACT_BRANCH)
            .withEnv("GITHUB_REF_NAME", CONTRACT_BRANCH)
            .withEnv("filter", "PATH!=/health")
            .withFileSystemBind("./specmatic.yaml", "/usr/src/app/specmatic.yaml", BindMode.READ_ONLY)
            .withFileSystemBind("./contract_examples", "/usr/src/app/contract_examples", BindMode.READ_ONLY)
            .withFileSystemBind("./certs", "/usr/src/app/certs", BindMode.READ_ONLY)
            .withFileSystemBind("./build/reports/specmatic", "/usr/src/app/build/reports/specmatic", BindMode.READ_WRITE)
            .waitingFor(Wait.forLogMessage(".*Tests run:.*", 1))
            .withExtraHost("host.docker.internal", "host-gateway")
            .withLogConsumer(outputFrame -> System.out.print(outputFrame.getUtf8String()));

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        KeycloakTestSupport.startIfNeeded();
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakTestSupport::issuerUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> "order-api");
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

    @Test
    public void requestWithoutClientCertificateIsRejectedDuringTlsHandshake() throws Exception {
        HttpClient client = httpsClient(false);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:8443/health")).GET().build();

        assertThatThrownBy(() -> client.send(request, HttpResponse.BodyHandlers.ofString()))
                .hasRootCauseInstanceOf(SSLHandshakeException.class);
    }

    @Test
    public void validClientCertificateReachesHealthEndpoint() throws Exception {
        HttpClient client = httpsClient(true);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:8443/health")).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("OK");
    }

    private static HttpClient httpsClient(boolean includeClientCertificate) throws Exception {
        KeyStore trustStore = loadKeyStore("./certs/server-truststore.jks");
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        KeyManagerFactory keyManagerFactory = null;
        if (includeClientCertificate) {
            KeyStore clientKeyStore = loadKeyStore("./certs/specmatic-client.jks");
            keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(clientKeyStore, "changeit".toCharArray());
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
                keyManagerFactory == null ? null : keyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers(),
                null
        );
        return HttpClient.newBuilder().sslContext(sslContext).build();
    }

    private static KeyStore loadKeyStore(String path) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream input = new FileInputStream(path)) {
            keyStore.load(input, "changeit".toCharArray());
        }
        return keyStore;
    }

    private static String contractBranch() {
        String headRef = System.getenv("GITHUB_HEAD_REF");
        if (headRef != null && !headRef.isBlank()) {
            return headRef;
        }

        String refName = System.getenv("GITHUB_REF_NAME");
        if (refName != null && !refName.isBlank()) {
            return refName;
        }

        try {
            Process process = new ProcessBuilder("git", "branch", "--show-current").start();
            String branch = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() != 0 || branch.isBlank()) {
                throw new IllegalStateException("Unable to determine the current contract branch");
            }
            return branch;
        } catch (Exception exception) {
            throw new IllegalStateException("Set GITHUB_HEAD_REF or GITHUB_REF_NAME to select the contract branch", exception);
        }
    }
}
