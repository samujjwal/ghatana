package com.ghatana.yappc.infrastructure.datacloud.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @doc.type class
 * @doc.purpose Verifies fail-closed tenant enforcement across YAPPC Data Cloud repositories
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("YAPPC Data Cloud repository tenant enforcement")
class YappcDataCloudRepositoryTenantEnforcementTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    private AutoCloseable mocks;
    private ProjectRepository projectRepository;
    private TaskRepository taskRepository;
    private PhaseStateRepository phaseStateRepository;
    private AgentStateRepository agentStateRepository;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        YappcEntityMapper mapper = new YappcEntityMapper(objectMapper);
        projectRepository = new ProjectRepository(client, mapper);
        taskRepository = new TaskRepository(client, mapper);
        phaseStateRepository = new PhaseStateRepository(client, mapper);
        agentStateRepository = new AgentStateRepository(client);
    }

    @AfterEach
    void tearDown() throws Exception {
        runBlocking(TenantContext::clear);
        TenantContext.clear();
        mocks.close();
    }

    @Test
    @DisplayName("missing tenant context fails closed for every repository")
    void missingTenantContextFailsClosedForEveryRepository() {
        TenantContext.clear();
        runBlocking(TenantContext::clear);

        for (RepositoryCall call : repositoryCalls()) {
            assertThatThrownBy(call.action()::run)
                    .as(call.name())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("tenant");
        }
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("default tenant context fails closed for every repository")
    void defaultTenantContextFailsClosedForEveryRepository() {
        TenantContext.setCurrentTenantId("default-tenant");
        runBlocking(() -> TenantContext.setCurrentTenantId("default-tenant"));

        for (RepositoryCall call : repositoryCalls()) {
            assertThatThrownBy(call.action()::run)
                    .as(call.name())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("default-tenant");
        }
        verifyNoInteractions(client);
    }

    private List<RepositoryCall> repositoryCalls() {
        UUID id = UUID.randomUUID();
        return List.of(
                new RepositoryCall("ProjectRepository", () -> projectRepository.findAll()),
                new RepositoryCall("TaskRepository", () -> taskRepository.findAll()),
                new RepositoryCall("PhaseStateRepository", () -> phaseStateRepository.findAll()),
                new RepositoryCall("AgentStateRepository", () -> agentStateRepository.findById(id))
        );
    }

    private record RepositoryCall(String name, Runnable action) {
    }
}
