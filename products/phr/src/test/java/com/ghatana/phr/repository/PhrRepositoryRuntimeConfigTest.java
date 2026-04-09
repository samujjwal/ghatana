package com.ghatana.phr.repository;

import com.ghatana.phr.support.PhrPersistenceTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhrRepositoryRuntimeConfigTest {

    @Test
    void defaultsToDisabledWhenNoJdbcUrlPresent() {
        PhrRepositoryRuntimeConfig config = PhrRepositoryRuntimeConfig.fromContext(
            new PhrPersistenceTestSupport.RuntimeConfigKernelContext(Map.of())
        );

        assertFalse(config.isPersistenceEnabled());
        assertTrue(config.getDataSourceConfig().isEmpty());
    }

    @Test
    void rejectsEnabledPersistenceWithoutCredentials() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            PhrRepositoryRuntimeConfig.fromContext(new PhrPersistenceTestSupport.RuntimeConfigKernelContext(
                Map.of(
                    "phr.persistence.enabled", true,
                    "phr.persistence.jdbc-url", "jdbc:postgresql://localhost:5432/phr"
                )
            ))
        );

        assertTrue(exception.getMessage().contains("phr.persistence.username"));
    }

    @Test
    void enablesPersistenceWhenJdbcUrlPresent() {
        PhrRepositoryRuntimeConfig config = PhrRepositoryRuntimeConfig.fromContext(
            new PhrPersistenceTestSupport.RuntimeConfigKernelContext(
                Map.of(
                    "phr.persistence.jdbc-url", "jdbc:postgresql://localhost:5432/phr",
                    "phr.persistence.username", "ghatana",
                    "phr.persistence.password", "password",
                    "phr.persistence.pool-name", "phr-runtime-test"
                )
            )
        );

        assertTrue(config.isPersistenceEnabled());
        assertEquals("jdbc:postgresql://localhost:5432/phr", config.getDataSourceConfig().orElseThrow().jdbcUrl());
        assertEquals("phr-runtime-test", config.getDataSourceConfig().orElseThrow().poolName());
    }
}
