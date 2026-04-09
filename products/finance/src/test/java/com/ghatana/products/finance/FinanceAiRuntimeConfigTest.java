package com.ghatana.products.finance;

import com.ghatana.finance.ai.FinanceAiPersistenceTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies finance AI runtime config resolution from kernel context values
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceAiRuntimeConfigTest {

    @Test
    void defaultsToDisabledWhenNoJdbcUrlPresent() {
        FinanceAiRuntimeConfig config = FinanceAiRuntimeConfig.fromContext(
            new FinanceAiPersistenceTestSupport.RuntimeConfigKernelContext(Map.of())
        );

        assertFalse(config.isPersistenceEnabled());
        assertTrue(config.getDataSourceConfig().isEmpty());
    }

    @Test
    void enablesPersistenceWhenJdbcUrlPresent() {
        FinanceAiRuntimeConfig config = FinanceAiRuntimeConfig.fromContext(
            new FinanceAiPersistenceTestSupport.RuntimeConfigKernelContext(Map.of(
                "finance.ai.database.jdbc-url", "jdbc:postgresql://localhost:5432/finance",
                "finance.ai.database.username", "ghatana",
                "finance.ai.database.password", "secret"
            ))
        );

        assertTrue(config.isPersistenceEnabled());
        assertEquals("jdbc:postgresql://localhost:5432/finance", config.getDataSourceConfig().orElseThrow().jdbcUrl());
        assertEquals(20, config.getDataSourceConfig().orElseThrow().maximumPoolSize());
    }

    @Test
    void rejectsEnabledPersistenceWithoutCredentials() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            FinanceAiRuntimeConfig.fromContext(
                new FinanceAiPersistenceTestSupport.RuntimeConfigKernelContext(Map.of(
                    "finance.ai.persistence.enabled", true,
                    "finance.ai.database.jdbc-url", "jdbc:postgresql://localhost:5432/finance"
                ))
            )
        );

        assertTrue(exception.getMessage().contains("finance.ai.database.username"));
    }
}
