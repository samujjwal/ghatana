package com.ghatana.digitalmarketing.application.landingpage;

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
import com.ghatana.digitalmarketing.domain.content.LandingPageSection;
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

@DisplayName("LandingPageGeneratorServiceImpl")
class LandingPageGeneratorServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private EphemeralContentItemRepository itemRepository;
    private EphemeralContentItemVersionRepository versionRepository;
    private LandingPageGeneratorServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter     = new RecordingKernelAdapter();
        itemRepository    = new EphemeralContentItemRepository();
        versionRepository = new EphemeralContentItemVersionRepository();
        ContentItemService contentItemService =
            new ContentItemServiceImpl(kernelAdapter, itemRepository, versionRepository);
        service = new LandingPageGeneratorServiceImpl(kernelAdapter, contentItemService);

        // Pre-seed a content item the generator will version under
        ContentItem item = ContentItem.builder()
            .itemId("item-lp-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .title("Landing Page")
            .itemType(ContentItemType.LANDING_PAGE)
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
            .isThrownBy(() -> new LandingPageGeneratorServiceImpl(null, fakeContentItemService()));
    }

    @Test
    @DisplayName("constructor rejects null contentItemService")
    void shouldRejectNullContentItemService() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new LandingPageGeneratorServiceImpl(kernelAdapter, null));
    }

    // -------------------------------------------------------------------------
    // generateDraft — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateDraft creates a DRAFT version with all 7 sections")
    void shouldGenerateDraftWithAllSections() {
        ContentVersion version = runPromise(() ->
            service.generateDraft(ctx, minimalCommand()));

        assertThat(version).isNotNull();
        assertThat(version.getStatus()).isEqualTo(ContentVersionStatus.DRAFT);
        assertThat(version.getContentBlocks()).hasSize(7);

        List<String> blockIds = version.getContentBlocks().stream()
            .map(b -> b.blockId())
            .collect(Collectors.toList());
        for (LandingPageSection section : LandingPageSection.values()) {
            assertThat(blockIds).contains(section.name());
        }
    }

    @Test
    @DisplayName("generateDraft records audit action")
    void shouldRecordAuditOnGenerate() {
        runPromise(() -> service.generateDraft(ctx, minimalCommand()));

        assertThat(kernelAdapter.lastAuditAction).isEqualTo("landing-page-draft-generated");
    }

    @Test
    @DisplayName("generateDraft with proof points embeds them in PROOF section")
    void shouldEmbedProofPointsWhenProvided() {
        LandingPageGeneratorService.GenerateLandingPageCommand cmd =
            new LandingPageGeneratorService.GenerateLandingPageCommand(
                "item-lp-1", "strat-1", "Acme Plumbing", "professional",
                "Plumbing Services", "Fast and reliable", "Denver, CO",
                List.of("5-star rated", "200+ happy clients"),
                List.of("Results may vary."), List.of()
            );

        ContentVersion version = runPromise(() -> service.generateDraft(ctx, cmd));

        String proofBody = version.getContentBlocks().stream()
            .filter(b -> b.blockId().equals(LandingPageSection.PROOF.name()))
            .findFirst()
            .map(b -> b.bodyText())
            .orElse("");

        assertThat(proofBody).contains("5-star rated").contains("200+ happy clients");
    }

    @Test
    @DisplayName("generateDraft without proof points throws IllegalStateException")
    void shouldThrowWhenNoProofPoints() {
        LandingPageGeneratorService.GenerateLandingPageCommand cmd =
            new LandingPageGeneratorService.GenerateLandingPageCommand(
                "item-lp-1", "strat-1", "Acme", "", "Services", "",
                "Denver", List.of(), List.of("Results may vary."), List.of()
            );

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.generateDraft(ctx, cmd)))
            .withMessageContaining("Cannot generate landing page without proof points");
    }

    @Test
    @DisplayName("generateDraft with disclosures embeds them in DISCLAIMER section")
    void shouldEmbedDisclosuresInDisclaimerSection() {
        LandingPageGeneratorService.GenerateLandingPageCommand cmd =
            new LandingPageGeneratorService.GenerateLandingPageCommand(
                "item-lp-1", "strat-1", "Acme", "", "Services", "",
                "Denver", List.of("5-star rated"), List.of("Results may vary.", "T&C apply."), List.of()
            );

        ContentVersion version = runPromise(() -> service.generateDraft(ctx, cmd));

        String disclaimerBody = version.getContentBlocks().stream()
            .filter(b -> b.blockId().equals(LandingPageSection.DISCLAIMER.name()))
            .findFirst()
            .map(b -> b.bodyText())
            .orElse("");

        assertThat(disclaimerBody).contains("Results may vary.").contains("T&C apply.");
    }

    @Test
    @DisplayName("generateDraft without disclosures throws IllegalStateException")
    void shouldThrowWhenNoDisclosures() {
        LandingPageGeneratorService.GenerateLandingPageCommand cmd =
            new LandingPageGeneratorService.GenerateLandingPageCommand(
                "item-lp-1", "strat-1", "Acme", "", "Services", "",
                "Denver", List.of("5-star rated"), List.of(), List.of()
            );

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.generateDraft(ctx, cmd)))
            .withMessageContaining("Cannot generate landing page without disclosure texts");
    }

    @Test
    @DisplayName("generateDraft with claim IDs creates claim references")
    void shouldCreateClaimReferencesForClaimIds() {
        LandingPageGeneratorService.GenerateLandingPageCommand cmd =
            new LandingPageGeneratorService.GenerateLandingPageCommand(
                "item-lp-1", "strat-1", "Acme", "", "Services", "",
                "Denver", List.of("5-star rated"), List.of("Results may vary."), List.of("claim-1", "claim-2")
            );

        ContentVersion version = runPromise(() -> service.generateDraft(ctx, cmd));

        assertThat(version.getClaimReferences()).hasSize(2);
        assertThat(version.getClaimReferences().get(0).claimId()).isEqualTo("claim-1");
    }

    @Test
    @DisplayName("generateDraft hero section contains brand name and service area")
    void shouldContainBrandNameAndServiceAreaInHeroSection() {
        LandingPageGeneratorService.GenerateLandingPageCommand cmd =
            new LandingPageGeneratorService.GenerateLandingPageCommand(
                "item-lp-1", "strat-1", "CleanSweep", "friendly",
                "Cleaning Services", "Deep cleaning specialists",
                "Portland, OR", List.of("5-star rated"), List.of("Results may vary."), List.of()
            );

        ContentVersion version = runPromise(() -> service.generateDraft(ctx, cmd));

        String heroBody = version.getContentBlocks().stream()
            .filter(b -> b.blockId().equals(LandingPageSection.HERO.name()))
            .findFirst()
            .map(b -> b.bodyText())
            .orElse("");

        assertThat(heroBody).contains("CleanSweep").contains("Portland, OR");
    }

    @Test
    @DisplayName("generateDraft OFFER section references evidence verification")
    void shouldReferenceEvidenceVerificationInOfferSection() {
        ContentVersion version = runPromise(() -> service.generateDraft(ctx, minimalCommand()));

        String offerBody = version.getContentBlocks().stream()
            .filter(b -> b.blockId().equals(LandingPageSection.OFFER.name()))
            .findFirst()
            .map(b -> b.bodyText())
            .orElse("");

        assertThat(offerBody).contains("verified against evidence during content review");
        assertThat(offerBody).doesNotContain("[");
    }

    @Test
    @DisplayName("generateDraft does not emit bracketed placeholder text in any block")
    void shouldNotContainBracketedPlaceholdersInAnyBlock() {
        ContentVersion version = runPromise(() -> service.generateDraft(ctx, minimalCommand()));

        for (var block : version.getContentBlocks()) {
            assertThat(block.bodyText())
                .withFailMessage("Block %s contains bracketed placeholder: %s", block.blockId(), block.bodyText())
                .doesNotContain("[");
        }
    }

    // -------------------------------------------------------------------------
    // generateDraft — authorization failure
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateDraft throws SecurityException when actor is not authorised")
    void shouldThrowSecurityExceptionWhenNotAuthorised() {
        kernelAdapter.denyAll = true;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generateDraft(ctx, minimalCommand())));
    }

    // -------------------------------------------------------------------------
    // generateDraft — command validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GenerateLandingPageCommand rejects blank itemId")
    void commandRejectsBlankItemId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new LandingPageGeneratorService.GenerateLandingPageCommand(
                "", "strat-1", "Brand", "", "Offer", "", "Area",
                List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("GenerateLandingPageCommand rejects blank strategyId")
    void commandRejectsBlankStrategyId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new LandingPageGeneratorService.GenerateLandingPageCommand(
                "item-1", "", "Brand", "", "Offer", "", "Area",
                List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("GenerateLandingPageCommand rejects blank brandDisplayName")
    void commandRejectsBlankBrandDisplayName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new LandingPageGeneratorService.GenerateLandingPageCommand(
                "item-1", "strat-1", "", "", "Offer", "", "Area",
                List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("GenerateLandingPageCommand rejects blank primaryOffer")
    void commandRejectsBlankPrimaryOffer() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new LandingPageGeneratorService.GenerateLandingPageCommand(
                "item-1", "strat-1", "Brand", "", "", "", "Area",
                List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("GenerateLandingPageCommand rejects blank serviceArea")
    void commandRejectsBlankServiceArea() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new LandingPageGeneratorService.GenerateLandingPageCommand(
                "item-1", "strat-1", "Brand", "", "Offer", "", "",
                List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("generateDraft null ctx throws NullPointerException")
    void generateDraftRejectsNullCtx() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.generateDraft(null, minimalCommand())));
    }

    @Test
    @DisplayName("generateDraft null command throws NullPointerException")
    void generateDraftRejectsNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.generateDraft(ctx, null)));
    }

    // -------------------------------------------------------------------------
    // getLatestApproved
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getLatestApproved returns the approved version")
    void shouldReturnApprovedVersion() {
        // generate and manually approve
        ContentVersion draft = runPromise(() -> service.generateDraft(ctx, minimalCommand()));
        ContentVersion approved = draft.submitForReview().approve("approver", Instant.now());
        versionRepository.store(approved);

        ContentVersion found = runPromise(() -> service.getLatestApproved(ctx, "item-lp-1"));

        assertThat(found.getVersionId()).isEqualTo(approved.getVersionId());
        assertThat(found.isApproved()).isTrue();
    }

    @Test
    @DisplayName("getLatestApproved throws NoSuchElementException when no approved version exists")
    void shouldThrowWhenNoApprovedVersion() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(ctx, "item-lp-1")));
    }

    @Test
    @DisplayName("getLatestApproved throws SecurityException when not authorised")
    void shouldThrowSecurityOnGetLatestApproved() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(ctx, "item-lp-1")));
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
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(null, "item-lp-1")));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LandingPageGeneratorService.GenerateLandingPageCommand minimalCommand() {
        return new LandingPageGeneratorService.GenerateLandingPageCommand(
            "item-lp-1", "strat-1", "Acme Corp", "professional",
            "Plumbing", "Fast reliable plumbing", "Denver, CO",
            List.of("5-star rated"), List.of("Results may vary."), List.of()
        );
    }

    private ContentItemService fakeContentItemService() {
        return new ContentItemService() {
            @Override
            public Promise<ContentItem> createItem(DmOperationContext ctx, CreateContentItemCommand cmd) {
                return Promise.of(null);
            }

            @Override
            public Promise<ContentVersion> createVersion(DmOperationContext ctx, CreateContentVersionCommand cmd) {
                return Promise.of(null);
            }

            @Override
            public Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId) {
                return Promise.of(null);
            }

            @Override
            public Promise<ContentVersion> approveVersion(DmOperationContext ctx, String versionId) {
                return Promise.of(null);
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
