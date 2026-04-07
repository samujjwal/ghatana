package com.ghatana.products.finance;

import com.ghatana.finance.ai.FinanceAiPersistenceTestSupport;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * @doc.type class
 * @doc.purpose Verifies finance product module starts a persistent AI runtime when DB config is present
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@ExtendWith(MockitoExtension.class)
class FinanceProductModulePersistenceIntegrationTest extends EventloopTestBase {

    @Mock
    private KernelContext context;

    private PostgreSQLContainer<?> postgres;

    @BeforeEach
    void setUp() {
        postgres = FinanceAiPersistenceTestSupport.startPostgres();

        lenient().when(context.hasDependency(any())).thenReturn(false);
        lenient().when(context.getOptionalConfig(anyString(), eq(Boolean.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return "finance.ai.persistence.enabled".equals(key) ? Optional.of(Boolean.TRUE) : Optional.empty();
        });
        lenient().when(context.getOptionalConfig(anyString(), eq(String.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return switch (key) {
                case "finance.ai.database.jdbc-url" -> Optional.of(postgres.getJdbcUrl());
                case "finance.ai.database.username" -> Optional.of(postgres.getUsername());
                case "finance.ai.database.password" -> Optional.of(postgres.getPassword());
                case "finance.ai.database.pool-name" -> Optional.of("finance-product-module-pool");
                default -> Optional.empty();
            };
        });
        lenient().when(context.getOptionalConfig(anyString(), eq(Integer.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return switch (key) {
                case "finance.ai.database.minimum-idle" -> Optional.of(1);
                case "finance.ai.database.maximum-pool-size" -> Optional.of(4);
                default -> Optional.empty();
            };
        });
        lenient().when(context.getOptionalConfig(anyString(), eq(Long.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return switch (key) {
                case "finance.ai.database.connection-timeout-ms" -> Optional.of(30_000L);
                case "finance.ai.database.idle-timeout-ms" -> Optional.of(60_000L);
                case "finance.ai.database.max-lifetime-ms" -> Optional.of(120_000L);
                default -> Optional.empty();
            };
        });
    }

    @AfterEach
    void tearDown() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void startsPersistentAiRuntimeFromProductModuleConfig() throws Exception {
        FinanceProductModule module = new FinanceProductModule();
        module.initialize(context);
        runPromise(module::start);

        FinanceAiRuntimeService runtimeService = extractAiRuntime(module);
        runtimeService.registerModel(new com.ghatana.kernel.ai.ModelGovernanceService.ModelRegistration(
            "fraud-detection-v11",
            "Finance Fraud Model",
            "11.0.0",
            "classification",
            Map.of("jurisdiction", "NP")
        ));

        assertNotNull(runtimeService.getModelMetadata("fraud-detection-v11"));
        assertEquals("NP", runtimeService.getModelMetadata("fraud-detection-v11").getAttributes().get("jurisdiction"));

        runPromise(module::stop);
    }

    private static FinanceAiRuntimeService extractAiRuntime(FinanceProductModule module) throws Exception {
        Field field = FinanceProductModule.class.getDeclaredField("aiRuntimeService");
        field.setAccessible(true);
        return (FinanceAiRuntimeService) field.get(module);
    }
}