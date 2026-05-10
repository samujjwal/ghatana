package com.ghatana.digitalmarketing.infra.command;

import com.ghatana.digitalmarketing.application.command.ExternalIdMappingRepository.ExternalIdMapping;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("InMemoryExternalIdMappingRepository")
class InMemoryExternalIdMappingRepositoryTest extends EventloopTestBase {

    private InMemoryExternalIdMappingRepository repository;
    private DmOperationContext context;

    @BeforeEach
    void setUp() {
        repository = new InMemoryExternalIdMappingRepository();
        context = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("workspace-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .build();
    }

    @Test
    @DisplayName("save then findExternalId and findInternalId returns mapped values")
    void savesAndFindsBothDirections() {
        ExternalIdMapping mapping = mapping("int-1", "ext-1", "google-ads");

        runPromise(() -> repository.save(context, mapping));

        Optional<String> externalId = runPromise(() -> repository.findExternalId(context, "int-1", "google-ads"));
        Optional<String> internalId = runPromise(() -> repository.findInternalId(context, "ext-1", "google-ads"));

        assertThat(externalId).contains("ext-1");
        assertThat(internalId).contains("int-1");
    }

    @Test
    @DisplayName("delete removes mapping from both directions")
    void deletesBothDirections() {
        ExternalIdMapping mapping = mapping("int-2", "ext-2", "meta-ads");
        runPromise(() -> repository.save(context, mapping));

        runPromise(() -> repository.delete(context, "int-2", "meta-ads"));

        Optional<String> externalId = runPromise(() -> repository.findExternalId(context, "int-2", "meta-ads"));
        Optional<String> internalId = runPromise(() -> repository.findInternalId(context, "ext-2", "meta-ads"));

        assertThat(externalId).isEmpty();
        assertThat(internalId).isEmpty();
    }

    @Test
    @DisplayName("find operations return empty when mapping is missing")
    void returnsEmptyForMissingMappings() {
        assertThat(runPromise(() -> repository.findExternalId(context, "missing", "google-ads"))).isEmpty();
        assertThat(runPromise(() -> repository.findInternalId(context, "missing", "google-ads"))).isEmpty();
    }

    @Test
    @DisplayName("methods reject null arguments")
    void rejectsNullArguments() {
        ExternalIdMapping mapping = mapping("int-3", "ext-3", "google-ads");

        assertThatNullPointerException().isThrownBy(() -> repository.save(null, mapping));
        assertThatNullPointerException().isThrownBy(() -> repository.save(context, null));

        assertThatNullPointerException().isThrownBy(() -> repository.findExternalId(null, "int-3", "google-ads"));
        assertThatNullPointerException().isThrownBy(() -> repository.findExternalId(context, null, "google-ads"));
        assertThatNullPointerException().isThrownBy(() -> repository.findExternalId(context, "int-3", null));

        assertThatNullPointerException().isThrownBy(() -> repository.findInternalId(null, "ext-3", "google-ads"));
        assertThatNullPointerException().isThrownBy(() -> repository.findInternalId(context, null, "google-ads"));
        assertThatNullPointerException().isThrownBy(() -> repository.findInternalId(context, "ext-3", null));

        assertThatNullPointerException().isThrownBy(() -> repository.delete(null, "int-3", "google-ads"));
        assertThatNullPointerException().isThrownBy(() -> repository.delete(context, null, "google-ads"));
        assertThatNullPointerException().isThrownBy(() -> repository.delete(context, "int-3", null));
    }

    private static ExternalIdMapping mapping(String internalId, String externalId, String externalSystem) {
        return new ExternalIdMapping(
            "map-" + internalId,
            internalId,
            externalId,
            externalSystem,
            "campaign",
            "tenant-1",
            "workspace-1",
            "corr-1",
            Instant.now(),
            "user-1"
        );
    }
}
