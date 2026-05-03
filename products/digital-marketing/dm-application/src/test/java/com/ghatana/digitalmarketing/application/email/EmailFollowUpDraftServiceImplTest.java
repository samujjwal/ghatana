package com.ghatana.digitalmarketing.application.email;

import com.ghatana.digitalmarketing.application.content.ContentItemRepository;
import com.ghatana.digitalmarketing.application.content.ContentItemService;
import com.ghatana.digitalmarketing.application.content.ContentItemServiceImpl;
import com.ghatana.digitalmarketing.application.content.ContentItemVersionRepository;
import com.ghatana.digitalmarketing.application.suppression.SuppressionService;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
import com.ghatana.digitalmarketing.domain.content.ContentItem;
import com.ghatana.digitalmarketing.domain.content.ContentItemType;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.ContentVersionStatus;
import com.ghatana.digitalmarketing.domain.content.EmailSection;
import com.ghatana.digitalmarketing.domain.content.GeneratorMetadata;
import com.ghatana.digitalmarketing.domain.suppression.SuppressionEntry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("EmailFollowUpDraftServiceImpl")
class EmailFollowUpDraftServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private FakeSuppressionService suppressionService;
    private InMemoryContentItemRepository itemRepository;
    private InMemoryContentItemVersionRepository versionRepository;
    private EmailFollowUpDraftServiceImpl service;
    private DmOperationContext ctx;

    private static final String ITEM_ID = "item-email-1";
    private static final String CONTACT_ID = "contact-1";

    @BeforeEach
    void setUp() {
        kernelAdapter     = new RecordingKernelAdapter();
        suppressionService = new FakeSuppressionService();
        itemRepository    = new InMemoryContentItemRepository();
        versionRepository = new InMemoryContentItemVersionRepository();

        ContentItemService contentItemService =
            new ContentItemServiceImpl(kernelAdapter, itemRepository, versionRepository);
        service = new EmailFollowUpDraftServiceImpl(
            kernelAdapter, contentItemService, suppressionService);

        ContentItem item = ContentItem.builder()
            .itemId(ITEM_ID)
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .title("Email Follow-Up Item")
            .itemType(ContentItemType.EMAIL)
            .description("")
            .createdAt(Instant.now())
            .createdBy("user-alice")
            .build();
        itemRepository.store(item);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-alice"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("constructor rejects null kernelAdapter")
    void shouldRejectNullKernelAdapter() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new EmailFollowUpDraftServiceImpl(null, fakeContentItemService(), suppressionService));
    }

    @Test
    @DisplayName("constructor rejects null contentItemService")
    void shouldRejectNullContentItemService() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new EmailFollowUpDraftServiceImpl(kernelAdapter, null, suppressionService));
    }

    @Test
    @DisplayName("constructor rejects null suppressionService")
    void shouldRejectNullSuppressionService() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new EmailFollowUpDraftServiceImpl(kernelAdapter, fakeContentItemService(), null));
    }

    // -------------------------------------------------------------------------
    // generateEmailDraft — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateEmailDraft creates a DRAFT version with all 6 sections")
    void shouldGenerateDraftWithAllSections() {
        ContentVersion version = runPromise(() ->
            service.generateEmailDraft(ctx, defaultCommand()));

        assertThat(version).isNotNull();
        assertThat(version.getStatus()).isEqualTo(ContentVersionStatus.DRAFT);

        List<String> blockTypes = version.getContentBlocks().stream()
            .map(b -> b.blockType())
            .collect(Collectors.toList());

        for (EmailSection section : EmailSection.values()) {
            assertThat(blockTypes).contains(section.name());
        }
    }

    @Test
    @DisplayName("generateEmailDraft records audit with correct action")
    void shouldRecordAuditAction() {
        runPromise(() -> service.generateEmailDraft(ctx, defaultCommand()));

        assertThat(kernelAdapter.lastAuditAction).isEqualTo("email-follow-up-draft-generated");
    }

    @Test
    @DisplayName("generateEmailDraft subject line is at most 60 characters")
    void shouldTruncateSubjectLineTo60Chars() {
        String longBrand  = "A".repeat(50);
        String longOffer  = "B".repeat(50);
        EmailFollowUpDraftService.GenerateEmailDraftCommand cmd =
            new EmailFollowUpDraftService.GenerateEmailDraftCommand(
                ITEM_ID, CONTACT_ID, "strat-1",
                longBrand, longOffer,
                "Sender", "reply@example.com",
                null, List.of());

        ContentVersion version = runPromise(() -> service.generateEmailDraft(ctx, cmd));

        String subject = version.getContentBlocks().stream()
            .filter(b -> b.blockType().equals(EmailSection.SUBJECT_LINE.name()))
            .findFirst()
            .orElseThrow()
            .bodyText();

        assertThat(subject.length()).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("generateEmailDraft compliance notes contain UNSUBSCRIBE placeholder")
    void shouldIncludeUnsubscribePlaceholder() {
        ContentVersion version = runPromise(() -> service.generateEmailDraft(ctx, defaultCommand()));

        String compliance = version.getContentBlocks().stream()
            .filter(b -> b.blockType().equals(EmailSection.UNSUBSCRIBE_NOTICE.name()))
            .findFirst()
            .orElseThrow()
            .bodyText();

        assertThat(compliance).contains("UNSUBSCRIBE LINK REQUIRED");
    }

    @Test
    @DisplayName("generateEmailDraft includes unverified claim warning when claimIds provided")
    void shouldIncludeUnverifiedClaimWarningWhenClaimsProvided() {
        EmailFollowUpDraftService.GenerateEmailDraftCommand cmd =
            new EmailFollowUpDraftService.GenerateEmailDraftCommand(
                ITEM_ID, CONTACT_ID, "strat-1",
                "Acme", "Plumbing", "Sender", "reply@example.com",
                null, List.of("claim-1"));

        ContentVersion version = runPromise(() -> service.generateEmailDraft(ctx, cmd));

        String compliance = version.getContentBlocks().stream()
            .filter(b -> b.blockType().equals(EmailSection.COMPLIANCE_NOTES.name()))
            .findFirst()
            .orElseThrow()
            .bodyText();

        assertThat(compliance).contains("CLAIM REVIEW REQUIRED");
    }

    @Test
    @DisplayName("generateEmailDraft does NOT include claim warning when no claims provided")
    void shouldNotIncludeClaimWarningWhenNoClaimsProvided() {
        ContentVersion version = runPromise(() -> service.generateEmailDraft(ctx, defaultCommand()));

        String compliance = version.getContentBlocks().stream()
            .filter(b -> b.blockType().equals(EmailSection.COMPLIANCE_NOTES.name()))
            .findFirst()
            .orElseThrow()
            .bodyText();

        assertThat(compliance).doesNotContain("CLAIM REVIEW REQUIRED");
    }

    @Test
    @DisplayName("generateEmailDraft includes voice tone in body when provided")
    void shouldIncludeVoiceToneInBody() {
        EmailFollowUpDraftService.GenerateEmailDraftCommand cmd =
            new EmailFollowUpDraftService.GenerateEmailDraftCommand(
                ITEM_ID, CONTACT_ID, "strat-1",
                "Acme", "Plumbing", "Sender", "reply@example.com",
                "professional", List.of());

        ContentVersion version = runPromise(() -> service.generateEmailDraft(ctx, cmd));

        String body = version.getContentBlocks().stream()
            .filter(b -> b.blockType().equals(EmailSection.BODY_COPY.name()))
            .findFirst()
            .orElseThrow()
            .bodyText();

        assertThat(body).contains("professional");
    }

    @Test
    @DisplayName("generateEmailDraft builds claim references from provided claimIds")
    void shouldBuildClaimReferences() {
        EmailFollowUpDraftService.GenerateEmailDraftCommand cmd =
            new EmailFollowUpDraftService.GenerateEmailDraftCommand(
                ITEM_ID, CONTACT_ID, "strat-1",
                "Acme", "Plumbing", "Sender", "reply@example.com",
                null, List.of("claim-1", "claim-2"));

        ContentVersion version = runPromise(() -> service.generateEmailDraft(ctx, cmd));

        assertThat(version.getClaimReferences())
            .extracting(c -> c.claimId())
            .containsExactlyInAnyOrder("claim-1", "claim-2");
    }

    @Test
    @DisplayName("generateEmailDraft builds disclosure references")
    void shouldBuildDisclosureReferences() {
        ContentVersion version = runPromise(() -> service.generateEmailDraft(ctx, defaultCommand()));

        assertThat(version.getDisclosureReferences()).isNotEmpty();
        assertThat(version.getDisclosureReferences().get(0).disclosureType())
            .isEqualTo("COMPLIANCE");
    }

    // -------------------------------------------------------------------------
    // generateEmailDraft — consent and suppression checks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateEmailDraft throws SecurityException when not authorised")
    void shouldThrowSecurityExceptionWhenNotAuthorised() {
        kernelAdapter.denyAll = true;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generateEmailDraft(ctx, defaultCommand())));
    }

    @Test
    @DisplayName("generateEmailDraft throws SecurityException when consent not granted")
    void shouldThrowSecurityExceptionWhenConsentNotGranted() {
        kernelAdapter.denyConsent = true;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generateEmailDraft(ctx, defaultCommand())));
    }

    @Test
    @DisplayName("generateEmailDraft throws SecurityException when contact is suppressed")
    void shouldThrowSecurityExceptionWhenContactSuppressed() {
        suppressionService.suppressedEmails.add(CONTACT_ID);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generateEmailDraft(ctx, defaultCommand())));
    }

    // -------------------------------------------------------------------------
    // generateEmailDraft — null / blank guards
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateEmailDraft throws NullPointerException for null ctx")
    void shouldThrowOnNullCtx() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.generateEmailDraft(null, defaultCommand())));
    }

    @Test
    @DisplayName("generateEmailDraft throws NullPointerException for null command")
    void shouldThrowOnNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.generateEmailDraft(ctx, null)));
    }

    @Test
    @DisplayName("GenerateEmailDraftCommand rejects blank itemId")
    void shouldRejectBlankItemId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new EmailFollowUpDraftService.GenerateEmailDraftCommand(
                "  ", CONTACT_ID, "strat-1",
                "Acme", "Offer", "Sender", "reply@example.com",
                null, List.of()));
    }

    @Test
    @DisplayName("GenerateEmailDraftCommand rejects blank contactId")
    void shouldRejectBlankContactId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new EmailFollowUpDraftService.GenerateEmailDraftCommand(
                ITEM_ID, "  ", "strat-1",
                "Acme", "Offer", "Sender", "reply@example.com",
                null, List.of()));
    }

    @Test
    @DisplayName("GenerateEmailDraftCommand rejects blank brandDisplayName")
    void shouldRejectBlankBrandDisplayName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new EmailFollowUpDraftService.GenerateEmailDraftCommand(
                ITEM_ID, CONTACT_ID, "strat-1",
                "  ", "Offer", "Sender", "reply@example.com",
                null, List.of()));
    }

    @Test
    @DisplayName("GenerateEmailDraftCommand rejects blank replyToAddress")
    void shouldRejectBlankReplyToAddress() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new EmailFollowUpDraftService.GenerateEmailDraftCommand(
                ITEM_ID, CONTACT_ID, "strat-1",
                "Acme", "Offer", "Sender", "  ",
                null, List.of()));
    }

    // -------------------------------------------------------------------------
    // getLatestApproved
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getLatestApproved returns approved version")
    void shouldReturnApprovedVersion() {
        // persist an approved version
        ContentVersion approved = buildApprovedVersion();
        versionRepository.store(approved);

        ContentVersion result = runPromise(() ->
            service.getLatestApproved(ctx, ITEM_ID));

        assertThat(result.getVersionId()).isEqualTo(approved.getVersionId());
    }

    @Test
    @DisplayName("getLatestApproved throws NoSuchElementException when none found")
    void shouldThrowWhenNoApprovedVersion() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(ctx, "no-such-item")));
    }

    @Test
    @DisplayName("getLatestApproved throws SecurityException when not authorised")
    void shouldThrowSecurityExceptionOnGetApproved() {
        kernelAdapter.denyAll = true;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(ctx, ITEM_ID)));
    }

    @Test
    @DisplayName("getLatestApproved throws IllegalArgumentException for blank itemId")
    void shouldThrowOnBlankItemId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(ctx, "  ")));
    }

    @Test
    @DisplayName("getLatestApproved throws NullPointerException for null ctx")
    void shouldThrowOnNullCtxForGetApproved() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(null, ITEM_ID)));
    }

    // -------------------------------------------------------------------------
    // truncate utility
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("truncate returns text as-is when within limit")
    void shouldReturnShortTextUnchanged() {
        assertThat(EmailFollowUpDraftServiceImpl.truncate("Hello", 10)).isEqualTo("Hello");
    }

    @Test
    @DisplayName("truncate clips text to maxLen")
    void shouldClipTextToMaxLen() {
        assertThat(EmailFollowUpDraftServiceImpl.truncate("1234567890", 5)).isEqualTo("12345");
    }

    @Test
    @DisplayName("truncate returns empty string when maxLen is zero")
    void shouldReturnEmptyForZeroMaxLen() {
        assertThat(EmailFollowUpDraftServiceImpl.truncate("text", 0)).isEqualTo("");
    }

    @Test
    @DisplayName("truncate returns empty string for null input")
    void shouldReturnEmptyForNullInput() {
        assertThat(EmailFollowUpDraftServiceImpl.truncate(null, 10)).isEqualTo("");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private EmailFollowUpDraftService.GenerateEmailDraftCommand defaultCommand() {
        return new EmailFollowUpDraftService.GenerateEmailDraftCommand(
            ITEM_ID, CONTACT_ID, "strat-1",
            "Acme Corp", "Plumbing Services",
            "Acme Sales", "sales@acme.example.com",
            "professional", List.of());
    }

    private ContentVersion buildApprovedVersion() {
        return ContentVersion.builder()
            .versionId("ver-approved-1")
            .itemId(ITEM_ID)
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .versionNumber(1)
            .status(ContentVersionStatus.APPROVED)
            .contentBlocks(List.of(
                new ContentBlock("SUBJECT_LINE", "SUBJECT_LINE", "Test subject", 0)))
            .claimReferences(List.of())
            .disclosureReferences(List.of())
            .generatorMetadata(new GeneratorMetadata(
                "email-gen-v1.0", "email-prompt-v1.0", "DETERMINISTIC", Instant.now()))
            .createdAt(Instant.now())
            .createdBy("user-alice")
            .build();
    }

    private ContentItemService fakeContentItemService() {
        return new ContentItemService() {
            @Override
            public Promise<com.ghatana.digitalmarketing.domain.content.ContentItem> createItem(
                    DmOperationContext ctx, CreateContentItemCommand command) {
                return Promise.of(null);
            }

            @Override
            public Promise<ContentVersion> createVersion(
                    DmOperationContext ctx, CreateContentVersionCommand command) {
                return Promise.ofException(new UnsupportedOperationException("stub"));
            }

            @Override
            public Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId) {
                return Promise.ofException(new NoSuchElementException("not found"));
            }

            @Override
            public Promise<ContentVersion> approveVersion(DmOperationContext ctx, String versionId) {
                return Promise.ofException(new UnsupportedOperationException("stub"));
            }

            @Override
            public Promise<List<ContentVersion>> getVersionHistory(DmOperationContext ctx, String itemId) {
                return Promise.of(List.of());
            }
        };
    }

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        boolean denyAll     = false;
        boolean denyConsent = false;
        String  lastAuditAction;

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) {
            return Promise.of(!denyAll);
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext ctx, String entityId,
                String action, Map<String, Object> metadata) {
            this.lastAuditAction = action;
            return Promise.of(UUID.randomUUID().toString());
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) {
            return Promise.of(!denyConsent);
        }

        @Override
        public Promise<String> requestApproval(DmOperationContext ctx, String operationType,
                String subjectId, String description) {
            return Promise.of(UUID.randomUUID().toString());
        }
    }

    private static final class FakeSuppressionService implements SuppressionService {
        final java.util.Set<String> suppressedEmails = new java.util.HashSet<>();

        @Override
        public Promise<SuppressionEntry> addSuppression(DmOperationContext ctx,
                AddSuppressionCommand command) {
            suppressedEmails.add(command.email());
            return Promise.of(null);
        }

        @Override
        public Promise<SuppressionEntry> removeSuppression(DmOperationContext ctx, String email) {
            suppressedEmails.remove(email);
            return Promise.of(null);
        }

        @Override
        public Promise<Boolean> isSuppressed(DmOperationContext ctx, String email) {
            return Promise.of(suppressedEmails.contains(email));
        }
    }

    private static final class InMemoryContentItemRepository implements ContentItemRepository {
        private final Map<String, ContentItem> store = new HashMap<>();

        void store(ContentItem item) {
            store.put(item.getItemId(), item);
        }

        @Override
        public Promise<ContentItem> save(ContentItem item) {
            store.put(item.getItemId(), item);
            return Promise.of(item);
        }

        @Override
        public Promise<Optional<ContentItem>> findById(DmWorkspaceId workspaceId, String itemId) {
            return Promise.of(
                Optional.ofNullable(store.get(itemId))
                    .filter(i -> i.getWorkspaceId().equals(workspaceId)));
        }

    }

    private static final class InMemoryContentItemVersionRepository
            implements ContentItemVersionRepository {
        private final Map<String, ContentVersion> store = new HashMap<>();

        void store(ContentVersion version) {
            store.put(version.getVersionId(), version);
        }

        @Override
        public Promise<ContentVersion> save(ContentVersion version) {
            store.put(version.getVersionId(), version);
            return Promise.of(version);
        }

        @Override
        public Promise<Optional<ContentVersion>> findById(DmWorkspaceId workspaceId, String versionId) {
            return Promise.of(Optional.ofNullable(store.get(versionId)));
        }

        @Override
        public Promise<Optional<ContentVersion>> findLatestApproved(DmWorkspaceId workspaceId, String itemId) {
            return Promise.of(
                store.values().stream()
                    .filter(v -> v.getItemId().equals(itemId)
                        && v.getStatus() == ContentVersionStatus.APPROVED)
                    .max((a, b) -> Integer.compare(a.getVersionNumber(), b.getVersionNumber()))
            );
        }

        @Override
        public Promise<List<ContentVersion>> findByItemId(DmWorkspaceId workspaceId, String itemId) {
            return Promise.of(
                store.values().stream()
                    .filter(v -> v.getItemId().equals(itemId))
                    .collect(Collectors.toList()));
        }

        @Override
        public Promise<Optional<ContentVersion>> findLatestByItemId(DmWorkspaceId workspaceId, String itemId) {
            return Promise.of(
                store.values().stream()
                    .filter(v -> v.getItemId().equals(itemId))
                    .max((a, b) -> Integer.compare(a.getVersionNumber(), b.getVersionNumber()))
            );
        }
    }
}
