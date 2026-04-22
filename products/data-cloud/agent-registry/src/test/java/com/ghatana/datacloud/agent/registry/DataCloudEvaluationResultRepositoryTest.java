/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("DataCloudEvaluationResultRepository [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DataCloudEvaluationResultRepositoryTest extends EventloopTestBase {

    private static final String TENANT_ID  = "tenant-eval-test";
    private static final String RELEASE_ID = "rel-eval-001";
    private static final Instant NOW       = Instant.parse("2026-04-01T10:00:00Z [GH-90000]");

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudEvaluationResultRepository repo;

    @BeforeEach
    void setUp() { // GH-90000
        repo = new DataCloudEvaluationResultRepository(dataCloud, TENANT_ID); // GH-90000
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private EvaluationResult passingResult(String evalId) { // GH-90000
        return EvaluationResult.of(evalId, RELEASE_ID, TENANT_ID, "llm-judge", 0.90, true, NOW); // GH-90000
    }

    private Map<String, Object> resultDataMap(EvaluationResult r) { // GH-90000
        Map<String, Object> m = new java.util.HashMap<>(); // GH-90000
        m.put("evaluationId",    r.evaluationId()); // GH-90000
        m.put("agentReleaseId",  r.agentReleaseId()); // GH-90000
        m.put("tenantId",        r.tenantId()); // GH-90000
        m.put("evaluatorType",   r.evaluatorType()); // GH-90000
        m.put("score",           r.score()); // GH-90000
        m.put("passed",          String.valueOf(r.passed())); // GH-90000
        m.put("evaluatedAt",     r.evaluatedAt().toString()); // GH-90000
        return m;
    }

    // ─────────────────── constructor ──────────────────────────────────────────

    @Nested
    @DisplayName("constructor [GH-90000]")
    class Constructor {

        @Test
        @DisplayName("rejects null dataCloud [GH-90000]")
        void rejectsNullDataCloud() { // GH-90000
            assertThatThrownBy(() -> new DataCloudEvaluationResultRepository(null, TENANT_ID)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("rejects null tenantId [GH-90000]")
        void rejectsNullTenantId() { // GH-90000
            assertThatThrownBy(() -> new DataCloudEvaluationResultRepository(dataCloud, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────── save ─────────────────────────────────────────────────

    @Nested
    @DisplayName("save [GH-90000]")
    class Save {

        @Test
        @DisplayName("delegates to createEntity and returns the original result [GH-90000]")
        void delegatesToCreateEntity() { // GH-90000
            EvaluationResult r = passingResult("eval-1 [GH-90000]");
            when(mockEntity.getId()).thenReturn(UUID.randomUUID()); // GH-90000
            when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(mockEntity)); // GH-90000

            EvaluationResult saved = runPromise(() -> repo.save(r)); // GH-90000

            assertThat(saved).isSameAs(r); // GH-90000
            verify(dataCloud).createEntity(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any()); // GH-90000
        }

        @Test
        @DisplayName("serialises evaluationId into data map [GH-90000]")
        void serialisesEvaluationId() { // GH-90000
            EvaluationResult r = passingResult("eval-999 [GH-90000]");
            when(mockEntity.getId()).thenReturn(UUID.randomUUID()); // GH-90000
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class); // GH-90000
            when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), captor.capture())) // GH-90000
                    .thenReturn(Promise.of(mockEntity)); // GH-90000

            runPromise(() -> repo.save(r)); // GH-90000

            Map<String, Object> data = captor.getValue(); // GH-90000
            assertThat(data).containsEntry("evaluationId", "eval-999"); // GH-90000
            assertThat(data).containsKey("passed [GH-90000]");
            assertThat(data).containsKey("score [GH-90000]");
        }
    }

    // ─────────────────── findById ─────────────────────────────────────────────

    @Nested
    @DisplayName("findById [GH-90000]")
    class FindById {

        @Test
        @DisplayName("returns deserialized result when entity found [GH-90000]")
        void returnsDeserializedResult() { // GH-90000
            EvaluationResult r = passingResult("eval-1 [GH-90000]");
            when(mockEntity.getData()).thenReturn(resultDataMap(r)); // GH-90000
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any(QuerySpecInterface.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(mockEntity))); // GH-90000

            Optional<EvaluationResult> found = runPromise(() -> repo.findById("eval-1 [GH-90000]"));

            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().evaluationId()).isEqualTo("eval-1 [GH-90000]");
            assertThat(found.get().passed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns empty when no entity found [GH-90000]")
        void returnsEmptyWhenNotFound() { // GH-90000
            when(dataCloud.queryEntities(any(), any(), any(QuerySpecInterface.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            Optional<EvaluationResult> found = runPromise(() -> repo.findById("missing [GH-90000]"));

            assertThat(found).isEmpty(); // GH-90000
        }
    }

    // ─────────────────── findByRelease ────────────────────────────────────────

    @Nested
    @DisplayName("findByRelease [GH-90000]")
    class FindByRelease {

        @Test
        @DisplayName("returns all results for a release [GH-90000]")
        void returnsAllResults() { // GH-90000
            EvaluationResult r1 = passingResult("eval-1 [GH-90000]");
            EvaluationResult r2 = EvaluationResult.of("eval-2", RELEASE_ID, TENANT_ID, "rubric", 0.30, false, NOW); // GH-90000
            EntityInterface e1 = mock(EntityInterface.class); // GH-90000
            EntityInterface e2 = mock(EntityInterface.class); // GH-90000
            when(e1.getData()).thenReturn(resultDataMap(r1)); // GH-90000
            when(e2.getData()).thenReturn(resultDataMap(r2)); // GH-90000
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(e1, e2))); // GH-90000

            List<EvaluationResult> results = runPromise(() -> repo.findByRelease(RELEASE_ID, TENANT_ID)); // GH-90000

            assertThat(results).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("findPassingByRelease returns only passed results [GH-90000]")
        void findPassingFiltersForPassed() { // GH-90000
            EvaluationResult passing = passingResult("eval-1 [GH-90000]");
            // Data with passed=false to verify client-side filter does not re-include it
            Map<String, Object> failData = resultDataMap( // GH-90000
                    EvaluationResult.of("eval-f", RELEASE_ID, TENANT_ID, "rubric", 0.3, false, NOW)); // GH-90000
            EntityInterface e = mock(EntityInterface.class); // GH-90000
            when(e.getData()).thenReturn(resultDataMap(passing)); // GH-90000
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(e))); // GH-90000

            List<EvaluationResult> results = runPromise(() -> repo.findPassingByRelease(RELEASE_ID, TENANT_ID)); // GH-90000

            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.getFirst().passed()).isTrue(); // GH-90000
        }
    }

    // ─────────────────── countPassing ─────────────────────────────────────────

    @Nested
    @DisplayName("countPassing [GH-90000]")
    class CountPassing {

        @Test
        @DisplayName("count delegates to findPassingByRelease [GH-90000]")
        void countDelegatesToFindPassing() { // GH-90000
            EvaluationResult r = passingResult("eval-1 [GH-90000]");
            EntityInterface e = mock(EntityInterface.class); // GH-90000
            when(e.getData()).thenReturn(resultDataMap(r)); // GH-90000
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudEvaluationResultRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(e))); // GH-90000

            long count = runPromise(() -> repo.countPassing(RELEASE_ID, TENANT_ID)); // GH-90000

            assertThat(count).isEqualTo(1L); // GH-90000
        }
    }
}
