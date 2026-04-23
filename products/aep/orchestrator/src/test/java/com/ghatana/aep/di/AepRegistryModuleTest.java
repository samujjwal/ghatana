package com.ghatana.aep.di;

import com.ghatana.aep.config.EnvConfig;
import com.ghatana.aep.engine.registry.AgentExecutionHistoryStore;
import com.ghatana.aep.engine.registry.AgentMemoryPlaneClient;
import com.ghatana.aep.engine.registry.NoopAgentExecutionHistoryStore;
import com.ghatana.aep.integration.registry.DataCloudPipelineRegistryClientImpl;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AepRegistryModule} persistence wiring.
 *
 * @doc.type class
 * @doc.purpose Verify registry DI enables durable history and memory only when a database is configured
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepRegistryModule")
class AepRegistryModuleTest {

    private final AepRegistryModule module = new AepRegistryModule(); // GH-90000

    @Test
    @DisplayName("uses no-op stores when no database is configured")
    void usesNoopStoresWithoutDataSource() { // GH-90000
        AgentExecutionHistoryStore historyStore = module.agentExecutionHistoryStore(null, Runnable::run); // GH-90000
        AgentMemoryPlaneClient memoryClient = module.agentMemoryPlaneClient(null); // GH-90000

        assertThat(historyStore).isInstanceOf(NoopAgentExecutionHistoryStore.class); // GH-90000
        assertThat(memoryClient).isInstanceOf(AgentMemoryPlaneClient.Noop.class); // GH-90000
    }

    @Test
    @DisplayName("creates durable stores when a database is configured")
    void createsDurableStoresWithDataSource() throws Exception { // GH-90000
        DataSource dataSource = mock(DataSource.class); // GH-90000
        Executor executor = Runnable::run;

        AgentExecutionHistoryStore historyStore = module.agentExecutionHistoryStore(dataSource, executor); // GH-90000
        AgentMemoryPlaneClient memoryClient = module.agentMemoryPlaneClient(dataSource); // GH-90000

        assertThat(historyStore).isNotInstanceOf(NoopAgentExecutionHistoryStore.class); // GH-90000
        assertThat(memoryClient).isNotInstanceOf(AgentMemoryPlaneClient.Noop.class); // GH-90000
        assertThat(readMemoryPlaneField(memoryClient, "memoryPlane").getSimpleName()) // GH-90000
            .isEqualTo("PersistentMemoryPlane");
    }

    @Test
    @DisplayName("fails fast when an unsupported pipeline registry mode is selected")
    void failsFastWhenUnsupportedPipelineRegistryModeSelected() { // GH-90000
        assertThatIllegalStateException() // GH-90000
            .isThrownBy(() -> AepRegistryModule.createPipelineRegistryClient(EnvConfig.fromMap(Map.of()), "noop")) // GH-90000
            .withMessageContaining("Unsupported AEP_PIPELINE_REGISTRY_MODE='noop'")
            .withMessageContaining("AEP requires AEP_PIPELINE_REGISTRY_MODE=datacloud");
    }

    @Test
    @DisplayName("fails fast when datacloud mode is selected without a base URL")
    void failsFastWhenDatacloudModeMissingBaseUrl() { // GH-90000
        assertThatIllegalStateException() // GH-90000
            .isThrownBy(() -> AepRegistryModule.createPipelineRegistryClient(EnvConfig.fromMap(Map.of()), "datacloud")) // GH-90000
            .withMessageContaining("AEP_DC_BASE_URL");
    }

    @Test
    @DisplayName("creates Data Cloud pipeline registry client when base URL is configured")
    void createsDataCloudPipelineRegistryClientWhenConfigured() { // GH-90000
        PipelineRegistryClient client = AepRegistryModule.createPipelineRegistryClient( // GH-90000
            EnvConfig.fromMap(Map.of("dc.base.url", "https://datacloud.internal")), // GH-90000
            "datacloud"
        );

        assertThat(client).isInstanceOf(DataCloudPipelineRegistryClientImpl.class); // GH-90000
    }

    private static Class<?> readMemoryPlaneField(AgentMemoryPlaneClient client, String fieldName) // GH-90000
            throws NoSuchFieldException, IllegalAccessException {
        Field field = AgentMemoryPlaneClient.class.getDeclaredField(fieldName); // GH-90000
        field.setAccessible(true); // GH-90000
        Object value = field.get(client); // GH-90000
        assertThat(value).isNotNull(); // GH-90000
        return value.getClass(); // GH-90000
    }
}