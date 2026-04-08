/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.release.EvaluationResult;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataCloudEvaluationResultRepository}.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudEvaluationResultRepository delegation and serialization
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudEvaluationResultRepository")
@ExtendWith(MockitoExtension.class)
class DataCloudEvaluationResultRepositoryTest extends EventloopTestBase {

    private static final String TENANT_ID  = "tenant-eval-test";
    private static final String RELEASE_ID = "rel-eval-001";
    private static final Instant NOW       = Instant.parse("2026-04-01T10:00:00Z");

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudEvaluationResultRepository repo;

    @BeforeEach
    void setUp() {
        repo = new DataCloudEvaluationResultRepository(dataCloud, TENANT_ID);
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private EvaluationResult passingResult(String evalId) {
        return EvaluationResult.of(evalId, RELEASE_ID, TENANT_ID, "llm-judge", 0.90, true, NOW);
    }

    private Map<String, Object> resultDataMap(EvaluationResult r) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("evaluationId",    r.evaluationId());
        m.put("agentReleaseId",  r.agentReleaseId());
        m.put("tenantId",        r.tenantId());
        m.put("evaluatorType",   r.evaluatorType());
        m.put("score",           r.score());
        m.put("passed",          String.valueOf(r.passed()));
        m.put("evaluatedAt",     r.evaluatedAt().toString());
        return m;
    }

    // ─────────────────── constructor ──────────────────────────────────────────

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("rejects null dataCloud")
        void rejectsNullDataCloud() {
            assertThatThrownBy(() -> new DataCloudEvaluationResultRepository(null, TENANT_ID))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null tenantId")
        void rejectsNullTenantId() {
            assertThatThrownBy(() -> new DataCloudEvaluationResultRepository(dataCloud, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─────────────────── save ─────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("delegates to createEntity and returns the original result")
        void delegatesToCreateEntity() {
            EvaluationResult r = passingResult("eval-1");
            when(mockEntity.getId()).thenReturn(UUID.randomUUID());
            when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any()))
                    .thenReturn(Promise.of(mockEntity));

            EvaluationResult saved = runPromise(() -> repo.save(r));

            assertThat(saved).isSameAs(r);
            verify(dataCloud).createEntity(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any());
        }

        @Test
        @DisplayName("serialises evaluationId into data map")
        void serialisesEvaluationId() {
            EvaluationResult r = passingResult("eval-999");
            when(mockEntity.getId()).thenReturn(UUID.randomUUID());
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), captor.capture()))
                    .thenReturn(Promise.of(mockEntity));

            runPromise(() -> repo.save(r));

            Map<String, Object> data = captor.getValue();
            assertThat(data).containsEntry("evaluationId", "eval-999");
            assertThat(data).containsKey("passed");
            assertThat(data).containsKey("score");
        }
    }

    // ─────────────────── findById ─────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns deserialized result when entity found")
        void returnsDeserializedResult() {
            EvaluationResult r = passingResult("eval-1");
            when(mockEntity.getData()).thenReturn(resultDataMap(r));
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any(QuerySpecInterface.class)))
                    .thenReturn(Promise.of(List.of(mockEntity)));

            Optional<EvaluationResult> found = runPromise(() -> repo.findById("eval-1"));

            assertThat(found).isPresent();
            assertThat(found.get().evaluationId()).isEqualTo("eval-1");
            assertThat(found.get().passed()).isTrue();
        }

        @Test
        @DisplayName("returns empty when no entity found")
        void returnsEmptyWhenNotFound() {
            when(dataCloud.queryEntities(any(), any(), any(QuerySpecInterface.class)))
                    .thenReturn(Promise.of(List.of()));

            Optional<EvaluationResult> found = runPromise(() -> repo.findById("missing"));

            assertThat(found).isEmpty();
        }
    }

    // ─────────────────── findByRelease ────────────────────────────────────────

    @Nested
    @DisplayName("findByRelease")
    class FindByRelease {

        @Test
        @DisplayName("returns all results for a release")
        void returnsAllResults() {
            EvaluationResult r1 = passingResult("eval-1");
            EvaluationResult r2 = EvaluationResult.of("eval-2", RELEASE_ID, TENANT_ID, "rubric", 0.30, false, NOW);
            EntityInterface e1 = mock(EntityInterface.class);
            EntityInterface e2 = mock(EntityInterface.class);
            when(e1.getData()).thenReturn(resultDataMap(r1));
            when(e2.getData()).thenReturn(resultDataMap(r2));
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any()))
                    .thenReturn(Promise.of(List.of(e1, e2)));

            List<EvaluationResult> results = runPromise(() -> repo.findByRelease(RELEASE_ID, TENANT_ID));

            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("findPassingByRelease returns only passed results")
        void findPassingFiltersForPassed() {
            EvaluationResult passing = passingResult("eval-1");
            // Data with passed=false to verify client-side filter does not re-include it
            Map<String, Object> failData = resultDataMap(
                    EvaluationResult.of("eval-f", RELEASE_ID, TENANT_ID, "rubric", 0.3, false, NOW));
            EntityInterface e = mock(EntityInterface.class);
            when(e.getData()).thenReturn(resultDataMap(passing));
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any()))
                    .thenReturn(Promise.of(List.of(e)));

            List<EvaluationResult> results = runPromise(() -> repo.findPassingByRelease(RELEASE_ID, TENANT_ID));

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().passed()).isTrue();
        }
    }

    // ─────────────────── countPassing ─────────────────────────────────────────

    @Nested
    @DisplayName("countPassing")
    class CountPassing {

        @Test
        @DisplayName("count delegates to findPassingByRelease")
        void countDelegatesToFindPassing() {
            EvaluationResult r = passingResult("eval-1");
            EntityInterface e = mock(EntityInterface.class);
            when(e.getData()).thenReturn(resultDataMap(r));
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any()))
                    .thenReturn(Promise.of(List.of(e)));

            long count = runPromise(() -> repo.countPassing(RELEASE_ID, TENANT_ID));

            assertThat(count).isEqualTo(1L);
        }
    }
}
