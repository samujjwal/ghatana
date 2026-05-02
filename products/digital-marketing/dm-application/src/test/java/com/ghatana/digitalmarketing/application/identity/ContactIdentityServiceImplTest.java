package com.ghatana.digitalmarketing.application.identity;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.identity.ContactIdentityProfile;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ContactIdentityServiceImpl")
class ContactIdentityServiceImplTest extends EventloopTestBase {

    private ContactIdentityServiceImpl service;
    private InMemoryRepository repository;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        service = new ContactIdentityServiceImpl(new AllowingKernelAdapter(), repository = new InMemoryRepository());
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("upserts and retrieves identity profile")
    void shouldUpsertAndGetIdentity() {
        ContactIdentityProfile upserted = runPromise(() -> service.upsertIdentity(
            ctx,
            "contact-1",
            new ContactIdentityService.UpsertIdentityCommand("+1555123", "en-US", "crm-1", Map.of("segment", "hot"))
        ));

        ContactIdentityProfile retrieved = runPromise(() -> service.getIdentity(ctx, "contact-1"));

        assertThat(upserted.getContactId()).isEqualTo("contact-1");
        assertThat(retrieved.getAttributes()).containsEntry("segment", "hot");
    }

    @Test
    @DisplayName("fails when identity profile missing")
    void shouldFailWhenMissing() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getIdentity(ctx, "missing")));
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatThrownBy(() -> new ContactIdentityServiceImpl(null, repository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ContactIdentityServiceImpl(new AllowingKernelAdapter(), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("denies unauthorized identity operations")
    void shouldDenyUnauthorizedOperations() {
        ContactIdentityServiceImpl deniedService = new ContactIdentityServiceImpl(new DenyKernelAdapter(), repository);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.upsertIdentity(
                ctx,
                "contact-1",
                new ContactIdentityService.UpsertIdentityCommand(null, null, "", Map.of())
            )));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.getIdentity(ctx, "contact-1")));
    }

    private static final class InMemoryRepository implements ContactIdentityRepository {
        private final ConcurrentHashMap<String, ContactIdentityProfile> store = new ConcurrentHashMap<>();

        @Override
        public Promise<ContactIdentityProfile> save(ContactIdentityProfile profile) {
            store.put(profile.getWorkspaceId().getValue() + ":" + profile.getContactId(), profile);
            return Promise.of(profile);
        }

        @Override
        public Promise<Optional<ContactIdentityProfile>> findByContactId(DmWorkspaceId workspaceId, String contactId) {
            return Promise.of(Optional.ofNullable(store.get(workspaceId.getValue() + ":" + contactId)));
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
