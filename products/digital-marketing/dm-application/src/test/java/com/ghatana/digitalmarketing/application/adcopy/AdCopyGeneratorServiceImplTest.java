package com.ghatana.digitalmarketing.application.adcopy;

import com.ghatana.digitalmarketing.application.content.ContentItemRepository;
import com.ghatana.digitalmarketing.application.content.ContentItemService;
import com.ghatana.digitalmarketing.application.content.ContentItemServiceImpl;
import com.ghatana.digitalmarketing.application.content.ContentItemVersionRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentItem;
import com.ghatana.digitalmarketing.domain.content.ContentItemType;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.ContentVersionStatus;
import com.ghatana.digitalmarketing.domain.content.GoogleAdCopySection;
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

@DisplayName("AdCopyGeneratorServiceImpl")
class AdCopyGeneratorServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private EphemeralContentItemRepository itemRepository;
    private EphemeralContentItemVersionRepository versionRepository;
    private AdCopyGeneratorServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter     = new RecordingKernelAdapter();
        itemRepository    = new EphemeralContentItemRepository();
        versionRepository = new EphemeralContentItemVersionRepository();
        ContentItemService contentItemService =
            new ContentItemServiceImpl(kernelAdapter, itemRepository, versionRepository);
        service = new AdCopyGeneratorServiceImpl(kernelAdapter, contentItemService);

        ContentItem item = ContentItem.builder()
            .itemId("item-ad-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .title("Ad Copy Item")
            .itemType(ContentItemType.AD)
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
            .isThrownBy(() -> new AdCopyGeneratorServiceImpl(null, fakeContentItemService()));
    }

    @Test
    @DisplayName("constructor rejects null contentItemService")
    void shouldRejectNullContentItemService() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new AdCopyGeneratorServiceImpl(kernelAdapter, null));
    }

    // -------------------------------------------------------------------------
    // generateAdCopyDraft — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateAdCopyDraft creates a DRAFT version with all 6 sections")
    void shouldGenerateDraftWithAllSections() {
        ContentVersion version = runPromise(() ->
            service.generateAdCopyDraft(ctx, minimalCommand()));

        assertThat(version).isNotNull();
        assertThat(version.getStatus()).isEqualTo(ContentVersionStatus.DRAFT);
        assertThat(version.getContentBlocks()).hasSize(6);

        List<String> blockIds = version.getContentBlocks().stream()
            .map(b -> b.blockId())
            .collect(Collectors.toList());
        for (GoogleAdCopySection section : GoogleAdCopySection.values()) {
            assertThat(blockIds).contains(section.name());
        }
    }

    @Test
    @DisplayName("generateAdCopyDraft records audit action")
    void shouldRecordAuditOnGenerate() {
        runPromise(() -> service.generateAdCopyDraft(ctx, minimalCommand()));

        assertThat(kernelAdapter.lastAuditAction).isEqualTo("ad-copy-draft-generated");
    }

    @Test
    @DisplayName("Headlines are truncated to at most 30 characters")
    void headlinesShouldBeTruncatedToThirtyChars() {
        AdCopyGeneratorService.GenerateAdCopyCommand cmd = new AdCopyGeneratorService.GenerateAdCopyCommand(
            "item-ad-1", "strat-1",
            "VeryLongBrandNameThatExceedsLimit",
            "VeryLongOfferNameThatAlsoExceedsTheThirtyCharacterLimit",
            "VeryLongServiceAreaThatExceedsLimit",
            "https://example.com",
            "professional", List.of(), List.of(), List.of()
        );

        ContentVersion version = runPromise(() -> service.generateAdCopyDraft(ctx, cmd));

        String headlinesText = version.getContentBlocks().stream()
            .filter(b -> b.blockId().equals(GoogleAdCopySection.HEADLINES.name()))
            .findFirst()
            .map(b -> b.bodyText())
            .orElse("");

        // Split by separator and check each segment
        for (String segment : headlinesText.split(" \\| ")) {
            assertThat(segment.length()).isLessThanOrEqualTo(30);
        }
    }

    @Test
    @DisplayName("Descriptions are truncated to at most 90 characters per line")
    void descriptionsShouldBeTruncatedToNinetyChars() {
        AdCopyGeneratorService.GenerateAdCopyCommand cmd = new AdCopyGeneratorService.GenerateAdCopyCommand(
            "item-ad-1", "strat-1",
            "LongBrand",
            "VeryLongOfferThatWillProduceDescriptionTextExceedingNinetyCharactersWhenCombinedWithMoreDetails",
            "LongAreaName",
            "https://example.com",
            "professional", List.of(), List.of(), List.of()
        );

        ContentVersion version = runPromise(() -> service.generateAdCopyDraft(ctx, cmd));

        String descriptionsText = version.getContentBlocks().stream()
            .filter(b -> b.blockId().equals(GoogleAdCopySection.DESCRIPTIONS.name()))
            .findFirst()
            .map(b -> b.bodyText())
            .orElse("");

        for (String line : descriptionsText.split("\n")) {
            if (!line.isBlank()) {
                assertThat(line.length()).isLessThanOrEqualTo(90);
            }
        }
    }

    @Test
    @DisplayName("COMPLIANCE_NOTES contains UNVERIFIED_CLAIM_WARNING when claim IDs are present")
    void shouldIncludeUnverifiedClaimWarningWhenClaimIdsPresent() {
        AdCopyGeneratorService.GenerateAdCopyCommand cmd = new AdCopyGeneratorService.GenerateAdCopyCommand(
            "item-ad-1", "strat-1", "Acme", "Services", "Denver",
            "https://example.com", "professional", List.of(), List.of(),
            List.of("claim-1", "claim-2")
        );

        ContentVersion version = runPromise(() -> service.generateAdCopyDraft(ctx, cmd));

        String complianceText = version.getContentBlocks().stream()
            .filter(b -> b.blockId().equals(GoogleAdCopySection.COMPLIANCE_NOTES.name()))
            .findFirst()
            .map(b -> b.bodyText())
            .orElse("");

        assertThat(complianceText).contains(AdCopyGeneratorServiceImpl.UNVERIFIED_CLAIM_WARNING);
        assertThat(complianceText).contains("claim-1").contains("claim-2");
    }

    @Test
    @DisplayName("COMPLIANCE_NOTES does NOT contain claim warning when no claim IDs")
    void shouldNotIncludeClaimWarningWhenNoClaimIds() {
        ContentVersion version = runPromise(() -> service.generateAdCopyDraft(ctx, minimalCommand()));

        String complianceText = version.getContentBlocks().stream()
            .filter(b -> b.blockId().equals(GoogleAdCopySection.COMPLIANCE_NOTES.name()))
            .findFirst()
            .map(b -> b.bodyText())
            .orElse("");

        assertThat(complianceText).doesNotContain(AdCopyGeneratorServiceImpl.UNVERIFIED_CLAIM_WARNING);
    }

    @Test
    @DisplayName("generateAdCopyDraft with keyword themes uses them in KEYWORD_THEMES section")
    void shouldUseProvidedKeywordThemes() {
        AdCopyGeneratorService.GenerateAdCopyCommand cmd = new AdCopyGeneratorService.GenerateAdCopyCommand(
            "item-ad-1", "strat-1", "Acme", "Plumbing", "Denver",
            "https://example.com", "professional",
            List.of("emergency plumber", "drain cleaning"), List.of(), List.of()
        );

        ContentVersion version = runPromise(() -> service.generateAdCopyDraft(ctx, cmd));

        String keywordsText = version.getContentBlocks().stream()
            .filter(b -> b.blockId().equals(GoogleAdCopySection.KEYWORD_THEMES.name()))
            .findFirst()
            .map(b -> b.bodyText())
            .orElse("");

        assertThat(keywordsText).contains("emergency plumber").contains("drain cleaning");
    }

    @Test
    @DisplayName("generateAdCopyDraft without keyword themes generates default themes")
    void shouldGenerateDefaultKeywordThemesWhenNoneProvided() {
        ContentVersion version = runPromise(() -> service.generateAdCopyDraft(ctx, minimalCommand()));

        String keywordsText = version.getContentBlocks().stream()
            .filter(b -> b.blockId().equals(GoogleAdCopySection.KEYWORD_THEMES.name()))
            .findFirst()
            .map(b -> b.bodyText())
            .orElse("");

        assertThat(keywordsText).isNotBlank();
    }

    @Test
    @DisplayName("generateAdCopyDraft with claim IDs creates claim references")
    void shouldCreateClaimReferences() {
        AdCopyGeneratorService.GenerateAdCopyCommand cmd = new AdCopyGeneratorService.GenerateAdCopyCommand(
            "item-ad-1", "strat-1", "Acme", "Services", "Denver",
            "https://example.com", "", List.of(), List.of(),
            List.of("claim-1", "claim-2")
        );

        ContentVersion version = runPromise(() -> service.generateAdCopyDraft(ctx, cmd));

        assertThat(version.getClaimReferences()).hasSize(2);
        assertThat(version.getClaimReferences().get(0).claimId()).isEqualTo("claim-1");
    }

    // -------------------------------------------------------------------------
    // generateAdCopyDraft — authorization failure
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateAdCopyDraft throws SecurityException when not authorised")
    void shouldThrowSecurityExceptionWhenNotAuthorised() {
        kernelAdapter.denyAll = true;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generateAdCopyDraft(ctx, minimalCommand())));
    }

    // -------------------------------------------------------------------------
    // generateAdCopyDraft — null validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateAdCopyDraft null ctx throws NullPointerException")
    void generateDraftRejectsNullCtx() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.generateAdCopyDraft(null, minimalCommand())));
    }

    @Test
    @DisplayName("generateAdCopyDraft null command throws NullPointerException")
    void generateDraftRejectsNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.generateAdCopyDraft(ctx, null)));
    }

    // -------------------------------------------------------------------------
    // GenerateAdCopyCommand validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GenerateAdCopyCommand rejects blank itemId")
    void commandRejectsBlankItemId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new AdCopyGeneratorService.GenerateAdCopyCommand(
                "", "strat-1", "Brand", "Offer", "Area", "https://example.com",
                "", List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("GenerateAdCopyCommand rejects blank strategyId")
    void commandRejectsBlankStrategyId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new AdCopyGeneratorService.GenerateAdCopyCommand(
                "item-1", "", "Brand", "Offer", "Area", "https://example.com",
                "", List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("GenerateAdCopyCommand rejects blank brandDisplayName")
    void commandRejectsBlankBrandDisplayName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new AdCopyGeneratorService.GenerateAdCopyCommand(
                "item-1", "strat-1", "", "Offer", "Area", "https://example.com",
                "", List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("GenerateAdCopyCommand rejects blank primaryOffer")
    void commandRejectsBlankPrimaryOffer() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new AdCopyGeneratorService.GenerateAdCopyCommand(
                "item-1", "strat-1", "Brand", "", "Area", "https://example.com",
                "", List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("GenerateAdCopyCommand rejects blank serviceArea")
    void commandRejectsBlankServiceArea() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new AdCopyGeneratorService.GenerateAdCopyCommand(
                "item-1", "strat-1", "Brand", "Offer", "", "https://example.com",
                "", List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("GenerateAdCopyCommand rejects blank landingPageUrl")
    void commandRejectsBlankLandingPageUrl() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new AdCopyGeneratorService.GenerateAdCopyCommand(
                "item-1", "strat-1", "Brand", "Offer", "Area", "",
                "", List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("GenerateAdCopyCommand accepts null voiceTone")
    void commandAcceptsNullVoiceTone() {
        AdCopyGeneratorService.GenerateAdCopyCommand cmd =
            new AdCopyGeneratorService.GenerateAdCopyCommand(
                "item-1", "strat-1", "Brand", "Offer", "Area", "https://example.com",
                null, List.of(), List.of(), List.of());
        assertThat(cmd.voiceTone()).isNull();
    }

    @Test
    @DisplayName("GenerateAdCopyCommand accepts empty lists")
    void commandAcceptsEmptyLists() {
        AdCopyGeneratorService.GenerateAdCopyCommand cmd =
            new AdCopyGeneratorService.GenerateAdCopyCommand(
                "item-1", "strat-1", "Brand", "Offer", "Area", "https://example.com",
                "", List.of(), List.of(), List.of());
        assertThat(cmd.keywordThemes()).isEmpty();
        assertThat(cmd.negativeKeywords()).isEmpty();
        assertThat(cmd.claimIds()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // getLatestApproved
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getLatestApproved returns the approved version")
    void shouldReturnApprovedVersion() {
        ContentVersion draft = runPromise(() -> service.generateAdCopyDraft(ctx, minimalCommand()));
        ContentVersion approved = draft.submitForReview().approve("approver", Instant.now());
        versionRepository.store(approved);

        ContentVersion found = runPromise(() -> service.getLatestApproved(ctx, "item-ad-1"));

        assertThat(found.getVersionId()).isEqualTo(approved.getVersionId());
        assertThat(found.isApproved()).isTrue();
    }

    @Test
    @DisplayName("getLatestApproved throws NoSuchElementException when no approved version exists")
    void shouldThrowWhenNoApprovedVersion() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(ctx, "item-ad-1")));
    }

    @Test
    @DisplayName("getLatestApproved throws SecurityException when not authorised")
    void shouldThrowSecurityOnGetLatestApproved() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(ctx, "item-ad-1")));
    }

    @Test
    @DisplayName("getLatestApproved throws IllegalArgumentException on blank itemId")
    void shouldThrowOnBlankItemId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(ctx, "")));
    }

    @Test
    @DisplayName("getLatestApproved null ctx throws NullPointerException")
    void getLatestApprovedRejectsNullCtx() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(null, "item-ad-1")));
    }

    // -------------------------------------------------------------------------
    // truncate utility
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("truncate returns text unchanged when within limit")
    void truncateDoesNotChangeShortText() {
        assertThat(AdCopyGeneratorServiceImpl.truncate("Hello", 10)).isEqualTo("Hello");
    }

    @Test
    @DisplayName("truncate clips text to maxLen")
    void truncateClipsToMaxLen() {
        assertThat(AdCopyGeneratorServiceImpl.truncate("Hello World", 5)).isEqualTo("Hello");
    }

    @Test
    @DisplayName("truncate with maxLen 0 returns empty string")
    void truncateZeroReturnsEmpty() {
        assertThat(AdCopyGeneratorServiceImpl.truncate("Hello", 0)).isEqualTo("");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AdCopyGeneratorService.GenerateAdCopyCommand minimalCommand() {
        return new AdCopyGeneratorService.GenerateAdCopyCommand(
            "item-ad-1", "strat-1", "Acme Corp", "Plumbing Services",
            "Denver, CO", "https://acme.example.com",
            "professional", List.of(), List.of(), List.of()
        );
    }

    private ContentItemService fakeContentItemService() {
        return new ContentItemService() {
            @Override
            public Promise<ContentItem> createItem(DmOperationContext c, CreateContentItemCommand cmd) {
                return Promise.of(null);
            }

            @Override
            public Promise<ContentVersion> createVersion(DmOperationContext c, CreateContentVersionCommand cmd) {
                return Promise.of(null);
            }

            @Override
            public Promise<ContentVersion> getLatestApproved(DmOperationContext c, String itemId) {
                return Promise.of(null);
            }

            @Override
            public Promise<ContentVersion> approveVersion(DmOperationContext c, String versionId) {
                return Promise.of(null);
            }

            @Override
            public Promise<List<ContentVersion>> getVersionHistory(DmOperationContext c, String itemId) {
                return Promise.of(List.of());
            }
        };
    }

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        boolean denyAll = false;
        String lastAuditAction;

        @Override public void start() {}
        @Override public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) {
            return Promise.of(!denyAll);
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext ctx, String entityId,
                String action, Map<String, Object> attributes) {
            this.lastAuditAction = action;
            return Promise.of("audit-id");
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(DmOperationContext ctx, String operationType,
                String subjectId, String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<Double> evaluateRisk(DmOperationContext context, String entityId,
                String riskModelId, Map<String, Object> factors) {
            return Promise.of(0.0);
        }

        @Override
        public Promise<Void> notifyUser(DmOperationContext context, String recipientId,
                String template, Map<String, String> attributes) {
            return Promise.of(null);
        }

        @Override
        public Promise<Boolean> isFeatureEnabled(DmOperationContext context, String flagKey) {
            return Promise.of(true);
        }
    }

    private static final class EphemeralContentItemRepository implements ContentItemRepository {
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
            return Promise.of(Optional.ofNullable(store.get(itemId)));
        }
    }

    private static final class EphemeralContentItemVersionRepository implements ContentItemVersionRepository {
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
                    .filter(v -> v.getItemId().equals(itemId) && v.isApproved())
                    .max((a, b) -> Integer.compare(a.getVersionNumber(), b.getVersionNumber()))
            );
        }

        @Override
        public Promise<List<ContentVersion>> findByItemId(DmWorkspaceId workspaceId, String itemId) {
            return Promise.of(
                store.values().stream()
                    .filter(v -> v.getItemId().equals(itemId))
                    .sorted((a, b) -> Integer.compare(a.getVersionNumber(), b.getVersionNumber()))
                    .collect(Collectors.toList())
            );
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
