package com.ghatana.digitalmarketing.application.suppression;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.suppression.SuppressionEntry;
import com.ghatana.platform.security.port.HashingPort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("SuppressionServiceImpl")
class SuppressionServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private EphemeralSuppressionRepository repository;
    private SuppressionServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new EphemeralSuppressionRepository();
        HashingPort hashingPort = new MockHashingPort();
        service = new SuppressionServiceImpl(kernelAdapter, repository, hashingPort);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("addSuppression creates active entry and records audit")
    void shouldAddSuppression() {
        SuppressionEntry entry = runPromise(() -> service.addSuppression(
            ctx,
            new SuppressionService.AddSuppressionCommand("a@example.com", "unsubscribe")
        ));

        assertThat(entry.getContactPointHash()).isEqualTo("hash-a@example.com");
        assertThat(entry.isActive()).isTrue();
        assertThat(kernelAdapter.auditActions()).contains("suppression-add");
    }

    @Test
    @DisplayName("addSuppression is idempotent for existing active email")
    void shouldReturnExistingOnDuplicateAdd() {
        SuppressionEntry first = runPromise(() -> service.addSuppression(
            ctx,
            new SuppressionService.AddSuppressionCommand("b@example.com", "unsubscribe")
        ));

        SuppressionEntry second = runPromise(() -> service.addSuppression(
            ctx,
            new SuppressionService.AddSuppressionCommand("b@example.com", "manual")
        ));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getContactPointHash()).isEqualTo("hash-b@example.com");
    }

    @Test
    @DisplayName("removeSuppression deactivates entry")
    void shouldRemoveSuppression() {
        runPromise(() -> service.addSuppression(
            ctx,
            new SuppressionService.AddSuppressionCommand("c@example.com", "unsubscribe")
        ));

        SuppressionEntry removed = runPromise(() -> service.removeSuppression(ctx, "c@example.com"));

        assertThat(removed.isActive()).isFalse();
        assertThat(kernelAdapter.auditActions()).contains("suppression-remove");
    }

    @Test
    @DisplayName("removeSuppression throws when email not suppressed")
    void shouldThrowWhenRemovingMissingSuppression() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.removeSuppression(ctx, "missing@example.com")));
    }

    @Test
    @DisplayName("isSuppressed returns true only for active entries")
    void shouldCheckSuppressionState() {
        runPromise(() -> service.addSuppression(
            ctx,
            new SuppressionService.AddSuppressionCommand("d@example.com", "unsubscribe")
        ));

        boolean beforeRemoval = runPromise(() -> service.isSuppressed(ctx, "d@example.com"));
        runPromise(() -> service.removeSuppression(ctx, "d@example.com"));
        boolean afterRemoval = runPromise(() -> service.isSuppressed(ctx, "d@example.com"));

        assertThat(beforeRemoval).isTrue();
        assertThat(afterRemoval).isFalse();
    }

    private static final class EphemeralSuppressionRepository implements SuppressionRepository {
        private final ConcurrentHashMap<String, SuppressionEntry> store = new ConcurrentHashMap<>();

        @Override
        public Promise<SuppressionEntry> save(SuppressionEntry entry) {
            store.put(entry.getWorkspaceId().getValue() + ":" + entry.getContactPointHash(), entry);
            return Promise.of(entry);
        }

        public Promise<Optional<SuppressionEntry>> findActiveByEmail(DmWorkspaceId workspaceId, String email) {
            SuppressionEntry found = store.get(workspaceId.getValue() + ":" + email);
            if (found != null && found.isActive()) {
                return Promise.of(Optional.of(found));
            }
            return Promise.of(Optional.empty());
        }

        public Promise<Optional<SuppressionEntry>> findActiveByContactPointHash(DmWorkspaceId workspaceId, String contactPointHash) {
            SuppressionEntry found = store.get(workspaceId.getValue() + ":" + contactPointHash);
            if (found != null && found.isActive()) {
                return Promise.of(Optional.of(found));
            }
            return Promise.of(Optional.empty());
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final List<String> auditActions = new java.util.concurrent.CopyOnWriteArrayList<>();

        List<String> auditActions() {
            return auditActions;
        }

        @Override
        public void start() {
            // no-op
        }

        @Override
        public void stop() {
            // no-op
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(true);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
            DmOperationContext context,
            String operationType,
            String subjectId,
            String description
        ) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
            DmOperationContext context,
            String entityId,
            String action,
            Map<String, Object> attributes
        ) {
            auditActions.add(action);
            return Promise.of("audit-1");
        }

        @Override
        public Promise<Double> evaluateRisk(
            DmOperationContext context,
            String entityId,
            String riskModelId,
            Map<String, Object> factors
        ) {
            return Promise.of(0.0);
        }

        @Override
        public Promise<Void> notifyUser(
            DmOperationContext context,
            String recipientId,
            String template,
            Map<String, String> attributes
        ) {
            return Promise.of(null);
        }

        @Override
        public Promise<Boolean> isFeatureEnabled(DmOperationContext context, String flagKey) {
            return Promise.of(true);
        }
    }

    private static final class MockHashingPort implements HashingPort {
        @Override
        public Promise<String> hashContactPoint(String contactPoint) {
            return Promise.of("hash-" + contactPoint);
        }

        @Override
        public Promise<String> hash(String data) {
            return Promise.of("hash-" + data);
        }

        @Override
        public Promise<Boolean> verifyContactPoint(String contactPoint, String expectedHash) {
            return Promise.of(expectedHash.equals("hash-" + contactPoint));
        }

        @Override
        public String getAlgorithm() {
            return "MockHash";
        }
    }
}
