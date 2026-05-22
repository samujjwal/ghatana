package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.audit.EventLogAuditService;
import com.ghatana.datacloud.spi.TransactionManager;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Focused production-profile validation tests for DataLifecycleHandler.
 *
 * @doc.type class
 * @doc.purpose Verify governance handler fail-closed startup checks for transaction and critical audit wiring
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataLifecycleHandler production validation")
class DataLifecycleHandlerProductionValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("fails in production profile when transaction manager is missing")
    void failsWhenTransactionManagerMissingInProduction() {
        DataLifecycleHandler handler = new DataLifecycleHandler(
            null,
            objectMapper,
            mock(HttpHandlerSupport.class),
            new EventLogAuditService(mock(EventLogStore.class), objectMapper, true)
        ).withDeploymentProfile("production");

        assertThatThrownBy(handler::validateProductionRequirements)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TransactionManager is required in production/staging/sovereign profiles");
    }

    @Test
    @DisplayName("fails in staging profile when critical audit service is missing")
    void failsWhenCriticalAuditMissingInStaging() {
        DataLifecycleHandler handler = new DataLifecycleHandler(
            null,
            objectMapper,
            mock(HttpHandlerSupport.class),
            null
        ).withDeploymentProfile("staging")
         .withTransactionManager(mock(TransactionManager.class));

        assertThatThrownBy(handler::validateProductionRequirements)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EventLogAuditService is required in production/staging/sovereign profiles");
    }

    @Test
    @DisplayName("passes in sovereign profile when transaction manager and critical audit service are configured")
    void passesWhenProductionDependenciesConfigured() {
        DataLifecycleHandler handler = new DataLifecycleHandler(
            null,
            objectMapper,
            mock(HttpHandlerSupport.class),
            new EventLogAuditService(mock(EventLogStore.class), objectMapper, true)
        ).withDeploymentProfile("sovereign")
         .withTransactionManager(mock(TransactionManager.class));

        assertThatCode(handler::validateProductionRequirements)
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("passes in local profile without transaction manager or critical audit service")
    void passesInLocalProfileWithoutProductionDependencies() {
        DataLifecycleHandler handler = new DataLifecycleHandler(
            null,
            objectMapper,
            mock(HttpHandlerSupport.class),
            null
        ).withDeploymentProfile("local");

        assertThatCode(handler::validateProductionRequirements)
            .doesNotThrowAnyException();
    }
}
