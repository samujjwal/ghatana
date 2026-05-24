package com.ghatana.digitalmarketing.application.privacy;

import com.ghatana.digitalmarketing.application.contact.ContactRepository;
import com.ghatana.digitalmarketing.application.suppression.SuppressionRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.contact.ConsentStatus;
import com.ghatana.digitalmarketing.domain.contact.Contact;
import com.ghatana.digitalmarketing.domain.suppression.SuppressionEntry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.consent.ConsentPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DataSubjectRequestServiceImpl")
class DataSubjectRequestServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private EphemeralContactRepository contactRepository;
    private TestConsentPlugin consentPlugin;
    private DataSubjectRequestServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        contactRepository = new EphemeralContactRepository();
        consentPlugin = new TestConsentPlugin();
        service = new DataSubjectRequestServiceImpl(kernelAdapter, contactRepository, new NoopSuppressionRepository(), consentPlugin);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("deleteContactData returns deleted=true when authorized and contact exists")
    void deleteContactDataAuthorized() {
        contactRepository.saveSync(contact("contact-1", "hash-1"));

        Boolean deleted = runPromise(() -> service.deleteContactData(ctx, "contact-1"));

        assertThat(deleted).isTrue();
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("contact-deletion");
        assertThat(kernelAdapter.lastAuditAttributes)
            .containsEntry("contactId", "contact-1")
            .containsEntry("deleted", "true");
    }

    @Test
    @DisplayName("deleteContactData invokes consentPlugin.deleteAllForSubject for DSAR erasure (KERNEL-P0-2)")
    void deleteContactDataErasesConsentRecords() {
        contactRepository.saveSync(contact("contact-3", "hash-3"));
        consentPlugin.setDeleteCount(5);

        Boolean deleted = runPromise(() -> service.deleteContactData(ctx, "contact-3"));

        assertThat(deleted).isTrue();
        assertThat(consentPlugin.getLastSubjectId()).isEqualTo("contact-3");
        assertThat(kernelAdapter.lastAuditAttributes)
            .containsEntry("consentRecordsErased", "5");
    }

    @Test
    @DisplayName("deleteContactData still completes DSAR when consent plugin finds 0 records")
    void deleteContactDataErasesConsentRecordsZero() {
        contactRepository.saveSync(contact("contact-4", "hash-4"));
        consentPlugin.setDeleteCount(0);

        Boolean deleted = runPromise(() -> service.deleteContactData(ctx, "contact-4"));

        assertThat(deleted).isTrue();
        assertThat(consentPlugin.getLastSubjectId()).isEqualTo("contact-4");
        assertThat(kernelAdapter.lastAuditAttributes)
            .containsEntry("consentRecordsErased", "0");
    }

    @Test
    @DisplayName("deleteContactData fails with SecurityException when unauthorized")
    void deleteContactDataUnauthorized() {
        kernelAdapter.authorized = false;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.deleteContactData(ctx, "contact-1")));
    }

    @Test
    @DisplayName("deleteContactDataByEmailHash returns 0 when no contact matches")
    void deleteByEmailHashNoMatch() {
        Integer deletedCount = runPromise(() -> service.deleteContactDataByEmailHash(ctx, "missing-hash"));
        assertThat(deletedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("deleteContactDataByEmailHash returns 1 when contact matches")
    void deleteByEmailHashMatch() {
        contactRepository.saveSync(contact("contact-2", "hash-2"));

        Integer deletedCount = runPromise(() -> service.deleteContactDataByEmailHash(ctx, "hash-2"));

        assertThat(deletedCount).isEqualTo(1);
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("contact-deletion");
    }

    @Test
    @DisplayName("recordDsarRequest writes audit entry")
    void recordDsarRequest() {
        runPromise(() -> service.recordDsarRequest(ctx, "delete", "subject-1"));

        assertThat(kernelAdapter.lastAuditAction).isEqualTo("dsar-request");
        assertThat(kernelAdapter.lastAuditAttributes)
            .containsEntry("requestType", "delete")
            .containsEntry("subjectId", "subject-1");
    }

    private static Contact contact(String id, String emailHash) {
        Instant now = Instant.now();
        return Contact.builder()
            .id(id)
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .emailHash(emailHash)
            .encryptedEmail("enc-" + emailHash)
            .displayName("Test")
            .consentStatus(ConsentStatus.GRANTED)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("tester")
            .build();
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private boolean authorized = true;
        private String lastAuditAction;
        private Map<String, Object> lastAuditAttributes;

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(authorized);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(DmOperationContext context, String operationType, String subjectId, String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext context, String entityId, String action, Map<String, Object> attributes) {
            this.lastAuditAction = action;
            this.lastAuditAttributes = attributes;
            return Promise.of("audit-1");
        }
    }

    private static final class NoopSuppressionRepository implements SuppressionRepository {
        @Override
        public Promise<SuppressionEntry> save(SuppressionEntry entry) {
            return Promise.of(entry);
        }

        @Override
        public Promise<Optional<SuppressionEntry>> findActiveByContactPointHash(DmWorkspaceId workspaceId, String contactPointHash) {
            return Promise.of(Optional.empty());
        }
    }

    private static final class EphemeralContactRepository implements ContactRepository {
        private final java.util.Map<String, Contact> byId = new java.util.HashMap<>();
        private final java.util.Map<String, Contact> byHash = new java.util.HashMap<>();

        void saveSync(Contact contact) {
            byId.put(contact.getId(), contact);
            byHash.put(contact.getEmailHash(), contact);
        }

        @Override
        public Promise<Contact> save(Contact contact) {
            saveSync(contact);
            return Promise.of(contact);
        }

        @Override
        public Promise<Optional<Contact>> findById(DmWorkspaceId workspaceId, String contactId) {
            return Promise.of(Optional.ofNullable(byId.get(contactId)));
        }

        @Override
        public Promise<Optional<Contact>> findByEmailHash(DmWorkspaceId workspaceId, String emailHash) {
            return Promise.of(Optional.ofNullable(byHash.get(emailHash)));
        }

        @Override
        public Promise<Optional<Contact>> findByEmail(DmWorkspaceId workspaceId, String email) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<Contact>> listMarketingEligible(DmWorkspaceId workspaceId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Integer> countMarketingEligible(DmWorkspaceId workspaceId) {
            return Promise.of(0);
        }

        @Override
        public Promise<Boolean> deleteById(DmWorkspaceId workspaceId, String contactId) {
            Contact removed = byId.remove(contactId);
            if (removed != null) {
                byHash.remove(removed.getEmailHash());
                return Promise.of(true);
            }
            return Promise.of(false);
        }
    }

    private static final class TestConsentPlugin implements ConsentPlugin {
        private int deleteCount = 0;
        private String lastSubjectId;

        void setDeleteCount(int count) {
            this.deleteCount = count;
        }

        String getLastSubjectId() {
            return lastSubjectId;
        }

        @Override
        public com.ghatana.platform.plugin.PluginMetadata metadata() {
            return com.ghatana.platform.plugin.PluginMetadata.builder()
                .id("test-consent")
                .name("Test Consent Plugin")
                .version("1.0.0")
                .build();
        }

        @Override
        public com.ghatana.platform.plugin.PluginState getState() {
            return com.ghatana.platform.plugin.PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(com.ghatana.platform.plugin.PluginContext context) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }

        @Override
        public Promise<ConsentRecord> recordConsent(String subjectId, String purpose, ConsentAction action) {
            return Promise.of(new ConsentRecord("test-id", subjectId, purpose, ConsentStatus.GRANTED, action, "test-basis", Instant.now(), Instant.now().plusSeconds(3600), null, null));
        }

        @Override
        public Promise<Boolean> verifyConsent(String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<Void> revokeConsent(String consentId) {
            return Promise.complete();
        }

        @Override
        public Promise<java.util.List<ConsentRecord>> getConsentHistory(String subjectId) {
            return Promise.of(java.util.List.of());
        }

        @Override
        public Promise<ConsentStatus> getCurrentConsent(String subjectId, String purpose) {
            return Promise.of(ConsentStatus.GRANTED);
        }

        @Override
        public Promise<Integer> deleteAllForSubject(String subjectId) {
            this.lastSubjectId = subjectId;
            return Promise.of(deleteCount);
        }
    }
}
