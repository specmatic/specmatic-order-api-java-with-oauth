package com.store;

import com.store.model.DB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@EnabledIf(value = "isNonCIOrLinux", disabledReason = "Run only on Linux in CI; all platforms allowed locally")
public class ContractTestUsingTestContainerTest {

    private static final String APPLICATION_HOST = "localhost";
    private static final int APPLICATION_PORT = 8080;
    private static final Map<String, String> TEST_CONTAINER_ENV_VARS = Map.of(
            "SPECMATIC_GENERATIVE_TESTS", "true",
            "endpointsAPI", "http://localhost:8080/actuator/mappings"
    );

    public static boolean isNonCIOrLinux() {
        return !"true".equals(System.getenv("CI")) || System.getProperty("os.name").toLowerCase().contains("linux");
    }

    private static final GenericContainer<?> testContainer = new GenericContainer<>("specmatic/specmatic:latest")
            .withCommand("test", "--host=" + APPLICATION_HOST, "--port=" + APPLICATION_PORT)
            .withEnv(TEST_CONTAINER_ENV_VARS)
            .withFileSystemBind("./specmatic.yaml", "/usr/src/app/specmatic.yaml", BindMode.READ_ONLY)
            .withFileSystemBind("./build/reports/specmatic", "/usr/src/app/build/reports/specmatic", BindMode.READ_WRITE)
            .waitingFor(Wait.forLogMessage(".*Tests run:.*", 1))
            .withNetworkMode("host")
            .withLogConsumer(outputFrame -> System.out.print(outputFrame.getUtf8String()));

    @BeforeAll
    public static void setup() {
        DB.INSTANCE.resetDB();
    }

    @Test
    public void specmaticContractTest() {
        testContainer.start();
        boolean hasSucceeded = testContainer.getLogs().contains("Failures: 0");
        assertThat(hasSucceeded).isTrue();
    }
}
