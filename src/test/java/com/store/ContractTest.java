package com.store;

import com.store.model.DB;
import com.store.testsupport.MockTokenServer;
import io.specmatic.enterprise.SpecmaticContractTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class ContractTest implements SpecmaticContractTest {
    private static ConfigurableApplicationContext context;
    private static final MockTokenServer tokenServer = new MockTokenServer();

    @BeforeAll
    public static void setUp() throws java.io.IOException {
        tokenServer.start();
        DB.INSTANCE.resetDB();
        System.setProperty("KEYCLOAK_BASE_URL", tokenServer.baseUrl());
        context = SpringApplication.run(Application.class);
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty("KEYCLOAK_BASE_URL");
        tokenServer.stop();
        if (context != null) {
            context.close();
        }
    }
}

