package com.ghatana.digitalmarketing.infra.transparency;

import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionStatus;
import com.ghatana.digitalmarketing.domain.transparency.AiActionType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("EphemeralAiActionLogRepository")
class EphemeralAiActionLogRepositoryTest extends EventloopTestBase {

    private EphemeralAiActionLogRepository repository;

    private static final String WS_1 = "ws-1";
    private static final String WS_2 = "ws-2";
    private static final String CORR = "corr-abc";
    private static final String ENTITY = "entity-xyz";

    @BeforeEach
    void setUp() {
        repository = new EphemeralAiActionLogRepository();
    }

    @Test
    @DisplayName("save returns the saved entry")
    void shouldReturnSavedEntry() {
        AiActionLogEntry entry = buildEntry(WS_1, "a-1", CORR, ENTITY);
        AiActionLogEntry saved = runPromise(() -> repository.save(entry));
        assertThat(saved).isSameAs(entry);
    }

    @Test
    @DisplayName("findById returns saved entry within same workspace")
    void shouldFindSavedEntry() {
        runPromise(() -> repository.save(buildEntry(WS_1, "a-1", CORR, ENTITY)));

        Optional<AiActionLogEntry> found = runPromise(() -> repository.findById(WS_1, "a-1"));

        assertThat(found).isPresent();
        assertThat(found.get().actionId()).isEqualTo("a-1");
    }

    @Test
    @DisplayName("findById returns empty for missing entry")
    void shouldReturnEmptyForMissing() {
        Optional<AiActionLogEntry> found = runPromise(() -> repository.findById(WS_1, "no-such"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("workspace isolation: entry from ws-1 is not visible from ws-2")
    void shouldIsolateWorkspaces() {
        runPromise(() -> repository.save(buildEntry(WS_1, "a-1", CORR, ENTITY)));

        Optional<AiActionLogEntry> found = runPromise(() -> repository.findById(WS_2, "a-1"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByWorkspace with null filters returns all entries in workspace up to limit")
    void shouldReturnAllInWorkspaceWithNullFilters() {
        runPromise(() -> repository.save(buildEntry(WS_1, "a-1", "corr-1", "e-1")));
        runPromise(() -> repository.save(buildEntry(WS_1, "a-2", "corr-2", "e-2")));
        runPromise(() -> repository.save(buildEntry(WS_2, "a-3", "corr-3", "e-3")));

        List<AiActionLogEntry> result = runPromise(() -> repository.findByWorkspace(WS_1, null, null, 10));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> e.workspaceId().equals(WS_1));
    }

    @Test
    @DisplayName("findByWorkspace with correlationId filter narrows results")
    void shouldFilterByCorrelationId() {
        runPromise(() -> repository.save(buildEntry(WS_1, "a-1", "corr-A", ENTITY)));
        runPromise(() -> repository.save(buildEntry(WS_1, "a-2", "corr-B", ENTITY)));

        List<AiActionLogEntry> result = runPromise(() -> repository.findByWorkspace(WS_1, "corr-A", null, 10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).correlationId()).isEqualTo("corr-A");
    }

    @Test
    @DisplayName("findByWorkspace with relatedEntityId filter narrows results")
    void shouldFilterByRelatedEntityId() {
        runPromise(() -> repository.save(buildEntry(WS_1, "a-1", CORR, "entity-1")));
        runPromise(() -> repository.save(buildEntry(WS_1, "a-2", CORR, "entity-2")));

        List<AiActionLogEntry> result = runPromise(() -> repository.findByWorkspace(WS_1, null, "entity-1", 10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).relatedEntityId()).isEqualTo("entity-1");
    }

    @Test
    @DisplayName("findByWorkspace respects limit parameter")
    void shouldRespectLimit() {
        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            runPromise(() -> repository.save(buildEntry(WS_1, "a-" + idx, CORR, ENTITY)));
        }

        List<AiActionLogEntry> result = runPromise(() -> repository.findByWorkspace(WS_1, null, null, 3));

        assertThat(result).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("findByWorkspace with limit=0 returns all matching entries")
    void shouldReturnAllWhenLimitIsZero() {
        runPromise(() -> repository.save(buildEntry(WS_1, "a-1", CORR, ENTITY)));
        runPromise(() -> repository.save(buildEntry(WS_1, "a-2", CORR, ENTITY)));

        List<AiActionLogEntry> result = runPromise(() -> repository.findByWorkspace(WS_1, null, null, 0));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("save rejects null entry")
    void shouldRejectNullEntry() {
        assertThatNullPointerException().isThrownBy(() -> repository.save(null));
    }

    @Test
    @DisplayName("findById rejects null arguments")
    void shouldRejectNullFindByIdArgs() {
        assertThatNullPointerException().isThrownBy(() -> repository.findById(null, "a-1"));
        assertThatNullPointerException().isThrownBy(() -> repository.findById(WS_1, null));
    }

    @Test
    @DisplayName("findByWorkspace rejects null workspaceId")
    void shouldRejectNullFindByWorkspaceArg() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.findByWorkspace(null, null, null, 10));
    }

    private static AiActionLogEntry buildEntry(
            String workspaceId, String actionId, String correlationId, String relatedEntityId) {
        return new AiActionLogEntry(
            actionId,
            workspaceId,
            correlationId,
            AiActionType.RECOMMENDATION_GENERATED,
            AiActionStatus.PROPOSED,
            "ai-agent",
            true,
            0.92,
            List.of(),
            List.of("policy-1"),
            "Test summary",
            "Test details",
            relatedEntityId,
            Instant.now(),
            0L
        );
    }
}
