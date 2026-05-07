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

    private final AepRegistryModule module = new AepRegistryModule(); 

    @Test
    @DisplayName("uses no-op stores when no database is configured")
    void usesNoopStoresWithoutDataSource() { 
        AgentExecutionHistoryStore historyStore = module.agentExecutionHistoryStore(null, Runnable::run); 
        AgentMemoryPlaneClient memoryClient = module.agentMemoryPlaneClient(null); 

        assertThat(historyStore).isInstanceOf(NoopAgentExecutionHistoryStore.class); 
        assertThat(memoryClient).isInstanceOf(AgentMemoryPlaneClient.Noop.class); 
    }

    @Test
    @DisplayName("creates durable stores when a database is configured")
    void createsDurableStoresWithDataSource() throws Exception { 
        DataSource dataSource = mock(DataSource.class); 
        Executor executor = Runnable::run;

        AgentExecutionHistoryStore historyStore = module.agentExecutionHistoryStore(dataSource, executor); 
        AgentMemoryPlaneClient memoryClient = module.agentMemoryPlaneClient(dataSource); 

        assertThat(historyStore).isNotInstanceOf(NoopAgentExecutionHistoryStore.class); 
        assertThat(memoryClient).isNotInstanceOf(AgentMemoryPlaneClient.Noop.class); 
        assertThat(readMemoryPlaneField(memoryClient, "memoryPlane").getSimpleName()) 
            .isEqualTo("PersistentMemoryPlane");
    }

    @Test
    @DisplayName("fails fast when an unsupported pipeline registry mode is selected")
    void failsFastWhenUnsupportedPipelineRegistryModeSelected() { 
        assertThatIllegalStateException() 
            .isThrownBy(() -> AepRegistryModule.createPipelineRegistryClient(EnvConfig.fromMap(Map.of()), "noop")) 
            .withMessageContaining("Unsupported AEP_PIPELINE_REGISTRY_MODE='noop'")
            .withMessageContaining("AEP requires AEP_PIPELINE_REGISTRY_MODE=datacloud");
    }

    @Test
    @DisplayName("fails fast when datacloud mode is selected without a base URL")
    void failsFastWhenDatacloudModeMissingBaseUrl() { 
        assertThatIllegalStateException() 
            .isThrownBy(() -> AepRegistryModule.createPipelineRegistryClient(EnvConfig.fromMap(Map.of()), "datacloud")) 
            .withMessageContaining("AEP_DC_BASE_URL");
    }

    @Test
    @DisplayName("creates Data Cloud pipeline registry client when base URL is configured")
    void createsDataCloudPipelineRegistryClientWhenConfigured() { 
        PipelineRegistryClient client = AepRegistryModule.createPipelineRegistryClient( 
            EnvConfig.fromMap(Map.of("dc.base.url", "https://datacloud.internal")), 
            "datacloud"
        );

        assertThat(client).isInstanceOf(DataCloudPipelineRegistryClientImpl.class); 
    }

    private static Class<?> readMemoryPlaneField(AgentMemoryPlaneClient client, String fieldName) 
            throws NoSuchFieldException, IllegalAccessException {
        Field field = AgentMemoryPlaneClient.class.getDeclaredField(fieldName); 
        field.setAccessible(true); 
        Object value = field.get(client); 
        assertThat(value).isNotNull(); 
        return value.getClass(); 
    }
}