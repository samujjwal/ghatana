package com.ghatana.digitalmarketing.application.consent;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.consent.ConsentProofSnapshot;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConsentProofServiceImpl")
class ConsentProofServiceImplTest extends EventloopTestBase {

    private ConsentProofServiceImpl service;
    private InMemoryConsentProofRepository repository;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryConsentProofRepository();
        service = new ConsentProofServiceImpl(new AllowingKernelAdapter(), repository);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("records and lists consent snapshots")
    void shouldRecordAndListSnapshots() {
        ConsentProofSnapshot snapshot = runPromise(() -> service.recordSnapshot(
            ctx,
            "contact-1",
            "GRANTED",
            "marketing-email",
            "form-submit",
            "proof://abc"
        ));

        List<ConsentProofSnapshot> snapshots = runPromise(() -> service.listSnapshots(ctx, "contact-1"));

        assertThat(snapshot.getContactId()).isEqualTo("contact-1");
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getEvidenceReference()).isEqualTo("proof://abc");
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatThrownBy(() -> new ConsentProofServiceImpl(null, repository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConsentProofServiceImpl(new AllowingKernelAdapter(), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("denies unauthorized consent operations")
    void shouldDenyUnauthorizedOperations() {
        ConsentProofServiceImpl deniedService = new ConsentProofServiceImpl(new DenyKernelAdapter(), repository);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.recordSnapshot(
                ctx,
                "contact-1",
                "GRANTED",
                "marketing-email",
                "form-submit",
                "proof://abc"
            )));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.listSnapshots(ctx, "contact-1")));
    }

    private static final class InMemoryConsentProofRepository implements ConsentProofRepository {
        private final List<ConsentProofSnapshot> store = new CopyOnWriteArrayList<>();

        @Override
        public Promise<ConsentProofSnapshot> save(ConsentProofSnapshot snapshot) {
            store.add(snapshot);
            return Promise.of(snapshot);
        }

        @Override
        public Promise<List<ConsentProofSnapshot>> listByContactId(DmWorkspaceId workspaceId, String contactId) {
            return Promise.of(store.stream()
                .filter(snapshot -> snapshot.getWorkspaceId().equals(workspaceId))
                .filter(snapshot -> snapshot.getContactId().equals(contactId))
                .toList());
        }
    }

    private static final class AllowingKernelAdapter implements DigitalMarketingKernelAdapter {
        @Override public void start() { }
        @Override public void stop() { }
        @Override public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) { return Promise.of(true); }
        @Override public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) { return Promise.of(true); }
        @Override public Promise<String> requestApproval(DmOperationContext context, String operationType, String subjectId, String description) { return Promise.of("approval-1"); }
        @Override public Promise<String> recordAudit(DmOperationContext context, String entityId, String action, Map<String, Object> attributes) { return Promise.of("audit-1"); }
    }

    private static final class DenyKernelAdapter implements DigitalMarketingKernelAdapter {
        @Override public void start() { }
        @Override public void stop() { }
        @Override public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) { return Promise.of(false); }
        @Override public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) { return Promise.of(true); }
        @Override public Promise<String> requestApproval(DmOperationContext context, String operationType, String subjectId, String description) { return Promise.of("approval-1"); }
        @Override public Promise<String> recordAudit(DmOperationContext context, String entityId, String action, Map<String, Object> attributes) { return Promise.of("audit-1"); }
    }
}
