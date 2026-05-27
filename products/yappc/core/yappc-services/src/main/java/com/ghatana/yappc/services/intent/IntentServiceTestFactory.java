package com.ghatana.yappc.services.intent;

import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.intent.IntentAnalysis;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dev/test-only factory for IntentService instances with explicit no-op collaborators.
 */
public final class IntentServiceTestFactory {

    private IntentServiceTestFactory() {
    }

    public static IntentService create(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics
    ) {
        return new IntentServiceImpl(aiService, auditLogger, metrics, new NoopIntentRepository(), new NoopIntentEvidenceService());
    }

    private static final class NoopIntentRepository implements IntentRepository {

        @Override
        public Promise<IntentVersionRecord> saveVersion(IntentSpec spec, IntentPersistenceContext context) {
            return Promise.of(new IntentVersionRecord(
                    context.projectId() + ":" + spec.id() + ":v1",
                    context.tenantId(),
                    context.workspaceId(),
                    context.projectId(),
                    spec.id(),
                    1,
                    spec,
                    context.actorId(),
                    Instant.now(),
                    null,
                    context.evidenceIds(),
                    context.metadata()));
        }

        @Override
        public Promise<Optional<IntentVersionRecord>> findLatest(String tenantId, String workspaceId, String projectId, String intentId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<IntentVersionRecord>> history(String tenantId, String workspaceId, String projectId, String intentId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Long> count(String tenantId) {
            return Promise.of(0L);
        }
    }

    private static final class NoopIntentEvidenceService implements IntentEvidenceService {

        @Override
        public Promise<String> recordCapture(IntentInput input, IntentSpec spec) {
            return Promise.of((String) null);
        }

        @Override
        public Promise<String> recordAnalysis(IntentSpec spec, IntentAnalysis analysis, Map<String, Object> groundingMetadata) {
            return Promise.of((String) null);
        }
    }
}