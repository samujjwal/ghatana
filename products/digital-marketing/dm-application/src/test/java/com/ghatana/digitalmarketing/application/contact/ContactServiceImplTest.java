package com.ghatana.digitalmarketing.application.contact;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.contact.Contact;
import com.ghatana.digitalmarketing.domain.contact.ConsentStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("ContactServiceImpl")
class ContactServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryContactRepository repository;
    private ContactServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository    = new InMemoryContactRepository();
        service       = new ContactServiceImpl(kernelAdapter, repository);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-42"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();

        kernelAdapter.setDefaultAuthorization(true);
    }

    @Test
    @DisplayName("constructor throws on null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new ContactServiceImpl(null, repository));
        assertThatNullPointerException()
            .isThrownBy(() -> new ContactServiceImpl(kernelAdapter, null));
    }

    @Test
    @DisplayName("registerContact creates contact with UNKNOWN consent and records audit")
    void shouldRegisterContact() {
        Contact contact = runPromise(() -> service.registerContact(
            ctx,
            new ContactService.RegisterContactCommand("alice@example.com", "Alice")));

        assertThat(contact.getId()).isNotBlank();
        assertThat(contact.getEmail()).isEqualTo("alice@example.com");
        assertThat(contact.getDisplayName()).isEqualTo("Alice");
        assertThat(contact.getConsentStatus()).isEqualTo(ConsentStatus.UNKNOWN);
        assertThat(contact.isSuppressed()).isFalse();
        assertThat(contact.isMarketingEligible()).isFalse();
        assertThat(kernelAdapter.auditActions()).contains("register");
    }

    @Test
    @DisplayName("registerContact command rejects blank email")
    void shouldRejectBlankEmail() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new ContactService.RegisterContactCommand("", "Name"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new ContactService.RegisterContactCommand(null, "Name"));
    }

    @Test
    @DisplayName("registerContact rejects when not authorized")
    void shouldDenyRegisterWhenNotAuthorized() {
        kernelAdapter.setAuthorization("contacts/*", "write", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.registerContact(
                ctx,
                new ContactService.RegisterContactCommand("bob@example.com", null))));
    }

    @Test
    @DisplayName("grantConsent transitions contact to GRANTED and marks eligible")
    void shouldGrantConsent() {
        Contact contact = runPromise(() -> service.registerContact(
            ctx,
            new ContactService.RegisterContactCommand("alice@example.com", "Alice")));

        Contact consented = runPromise(() -> service.grantConsent(ctx, contact.getId(), "marketing-email"));

        assertThat(consented.getConsentStatus()).isEqualTo(ConsentStatus.GRANTED);
        assertThat(consented.getConsentPurpose()).isEqualTo("marketing-email");
        assertThat(consented.isMarketingEligible()).isTrue();
        assertThat(kernelAdapter.auditActions()).contains("consent-granted");
    }

    @Test
    @DisplayName("withdrawConsent sets WITHDRAWN and suppresses contact")
    void shouldWithdrawConsent() {
        Contact contact = runPromise(() -> service.registerContact(
            ctx,
            new ContactService.RegisterContactCommand("bob@example.com", "Bob")));
        runPromise(() -> service.grantConsent(ctx, contact.getId(), "marketing-email"));

        Contact withdrawn = runPromise(() -> service.withdrawConsent(ctx, contact.getId()));

        assertThat(withdrawn.getConsentStatus()).isEqualTo(ConsentStatus.WITHDRAWN);
        assertThat(withdrawn.isSuppressed()).isTrue();
        assertThat(withdrawn.isMarketingEligible()).isFalse();
        assertThat(kernelAdapter.auditActions()).contains("consent-withdrawn");
    }

    @Test
    @DisplayName("grantConsent throws when contact not found")
    void shouldThrowWhenContactNotFound() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.grantConsent(ctx, "missing", "marketing-email")));
    }

    @Test
    @DisplayName("withdrawConsent throws when contact not found")
    void shouldThrowWhenWithdrawingMissingContact() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.withdrawConsent(ctx, "missing")));
    }

    @Test
    @DisplayName("getContact returns existing contact")
    void shouldGetContact() {
        Contact created = runPromise(() -> service.registerContact(
            ctx,
            new ContactService.RegisterContactCommand("carol@example.com", "Carol")));

        Contact found = runPromise(() -> service.getContact(ctx, created.getId()));
        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("getContact enforces read authorization")
    void shouldDenyReadWhenNotAuthorized() {
        Contact created = runPromise(() -> service.registerContact(
            ctx,
            new ContactService.RegisterContactCommand("dave@example.com", null)));

        kernelAdapter.setAuthorization("contacts/" + created.getId(), "read", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getContact(ctx, created.getId())));
    }

    @Test
    @DisplayName("hasConsent returns false for UNKNOWN consent contact")
    void shouldReturnFalseForUnknownConsent() {
        Contact created = runPromise(() -> service.registerContact(
            ctx,
            new ContactService.RegisterContactCommand("eve@example.com", null)));

        boolean result = runPromise(() -> service.hasConsent(ctx, created.getId(), "marketing-email"));
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasConsent returns true for GRANTED consent with matching purpose")
    void shouldReturnTrueForGrantedConsent() {
        Contact created = runPromise(() -> service.registerContact(
            ctx,
            new ContactService.RegisterContactCommand("frank@example.com", null)));
        runPromise(() -> service.grantConsent(ctx, created.getId(), "marketing-email"));

        boolean result = runPromise(() -> service.hasConsent(ctx, created.getId(), "marketing-email"));
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasConsent returns false after consent is withdrawn")
    void shouldReturnFalseAfterWithdrawal() {
        Contact created = runPromise(() -> service.registerContact(
            ctx,
            new ContactService.RegisterContactCommand("grace@example.com", null)));
        runPromise(() -> service.grantConsent(ctx, created.getId(), "marketing-email"));
        runPromise(() -> service.withdrawConsent(ctx, created.getId()));

        boolean result = runPromise(() -> service.hasConsent(ctx, created.getId(), "marketing-email"));
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasConsent returns false for non-existent contact")
    void shouldReturnFalseForMissingContact() {
        boolean result = runPromise(() -> service.hasConsent(ctx, "missing", "marketing-email"));
        assertThat(result).isFalse();
    }

    // -----------------------------------------------------------------------
    // Test doubles
    // -----------------------------------------------------------------------

    private static final class InMemoryContactRepository implements ContactRepository {
        private final ConcurrentHashMap<String, Contact> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Contact> save(Contact contact) {
            store.put(contact.getWorkspaceId().getValue() + ":" + contact.getId(), contact);
            return Promise.of(contact);
        }

        @Override
        public Promise<Optional<Contact>> findById(DmWorkspaceId workspaceId, String contactId) {
            return Promise.of(Optional.ofNullable(
                store.get(workspaceId.getValue() + ":" + contactId)));
        }

        @Override
        public Promise<Optional<Contact>> findByEmail(DmWorkspaceId workspaceId, String email) {
            return Promise.of(store.values().stream()
                .filter(c -> c.getWorkspaceId().equals(workspaceId) && c.getEmail().equals(email))
                .findFirst());
        }

        @Override
        public Promise<List<Contact>> listMarketingEligible(DmWorkspaceId workspaceId) {
            List<Contact> result = store.values().stream()
                .filter(c -> c.getWorkspaceId().equals(workspaceId) && c.isMarketingEligible())
                .toList();
            return Promise.of(result);
        }

        @Override
        public Promise<Integer> countMarketingEligible(DmWorkspaceId workspaceId) {
            int count = (int) store.values().stream()
                .filter(c -> c.getWorkspaceId().equals(workspaceId) && c.isMarketingEligible())
                .count();
            return Promise.of(count);
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final ConcurrentHashMap<String, Boolean> decisionMap = new ConcurrentHashMap<>();
        private volatile boolean defaultAuthorization = true;
        private final List<String> auditActions = new ArrayList<>();

        void setDefaultAuthorization(boolean allowed) {
            defaultAuthorization = allowed;
        }

        void setAuthorization(String resource, String action, boolean allowed) {
            decisionMap.put(resource + "|" + action, allowed);
        }

        List<String> auditActions() {
            return auditActions;
        }

        @Override
        public void start() { }

        @Override
        public void stop() { }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(decisionMap.getOrDefault(resource + "|" + action, defaultAuthorization));
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
                String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext context,
                String entityId,
                String action,
                Map<String, Object> attributes) {
            auditActions.add(action);
            return Promise.of("audit-1");
        }
    }
}
