package com.ghatana.aep.registry.performance;

import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.pipeline.registry.model.PipelineVersionStatus;
import com.ghatana.pipeline.registry.repository.InMemoryPipelineRepository;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Performance/load tier coverage for aep-registry repository operations.
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AEP Registry Load Tier [GH-90000]")
class AepRegistryLoadTierTest extends EventloopTestBase {

    private static final TenantId TENANT_A = TenantId.of("tenant-a");
    private static final TenantId TENANT_B = TenantId.of("tenant-b");

    @Test
    @DisplayName("loads and lists tenant-scoped pipelines within baseline")
    void shouldHandleTenantScopedLoad() {
        InMemoryPipelineRepository repository = new InMemoryPipelineRepository();

        Instant started = Instant.now();
        for (int i = 0; i < 1_000; i++) {
            final int index = i;
            runPromise(() -> repository.save(buildPipeline("a-" + index, TENANT_A)));
            runPromise(() -> repository.save(buildPipeline("b-" + index, TENANT_B)));
        }

        List<PipelineRegistration> tenantAPipelines = runPromise(() -> repository.findByTenantId(TENANT_A.value()));
        List<PipelineRegistration> tenantBPipelines = runPromise(() -> repository.findByTenantId(TENANT_B.value()));
        long elapsedMillis = Duration.between(started, Instant.now()).toMillis();

        assertThat(tenantAPipelines).hasSize(1_000);
        assertThat(tenantBPipelines).hasSize(1_000);
        assertThat(elapsedMillis).isLessThan(5_000);
    }

    @Test
    @DisplayName("stores and reads version history snapshots within baseline")
    void shouldHandleVersionHistoryLoad() {
        InMemoryPipelineRepository repository = new InMemoryPipelineRepository();
        String pipelineId = "versioned-pipeline";

        Instant started = Instant.now();
        for (int version = 1; version <= 300; version++) {
            PipelineRegistration snapshot = buildPipeline(pipelineId, TENANT_A);
            snapshot.setVersion(version);
            runPromise(() -> repository.saveVersionSnapshot(pipelineId, snapshot));
        }

        List<PipelineRegistration> history = runPromise(() -> repository.findVersionHistory(pipelineId, TENANT_A.value()));
        long elapsedMillis = Duration.between(started, Instant.now()).toMillis();

        assertThat(history).hasSize(300);
        assertThat(history.get(0).getVersion()).isEqualTo(1);
        assertThat(history.get(history.size() - 1).getVersion()).isEqualTo(300);
        assertThat(elapsedMillis).isLessThan(3_000);
    }

    private PipelineRegistration buildPipeline(String id, TenantId tenantId) {
        return PipelineRegistration.builder()
            .id(id)
            .tenantId(tenantId)
            .name("Pipeline " + id)
            .updatedBy("perf-tier")
            .active(true)
            .versionStatus(PipelineVersionStatus.DRAFT)
            .build();
    }
}


