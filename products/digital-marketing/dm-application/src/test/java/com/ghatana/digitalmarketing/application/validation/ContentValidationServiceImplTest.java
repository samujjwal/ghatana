package com.ghatana.digitalmarketing.application.validation;

import com.ghatana.digitalmarketing.application.content.ContentItemVersionRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ClaimReference;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.ContentVersionStatus;
import com.ghatana.digitalmarketing.domain.content.DisclosureReference;
import com.ghatana.digitalmarketing.domain.content.GeneratorMetadata;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationFinding;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationResult;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationResult.ValidationOutcome;
import com.ghatana.digitalmarketing.domain.validation.ValidationSeverity;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ContentValidationServiceImpl")
class ContentValidationServiceImplTest extends EventloopTestBase {

    private static final String VERSION_ID = "ver-val-1";
    private static final String ITEM_ID    = "item-val-1";

    private InMemoryContentItemVersionRepository versionRepository;
    private InMemoryValidationResultRepository    resultRepository;
    private RecordingKernelAdapter                kernelAdapter;
    private ContentValidationServiceImpl          service;
    private DmOperationContext                    ctx;

    @BeforeEach
    void setUp() {
        versionRepository = new InMemoryContentItemVersionRepository();
        resultRepository  = new InMemoryValidationResultRepository();
        kernelAdapter     = new RecordingKernelAdapter();
        service           = new ContentValidationServiceImpl(kernelAdapter, versionRepository, resultRepository);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-alice"))
            .correlationId(DmCorrelationId.generate())
            .build();
    }

    // -------------------------------------------------------------------------
    // Security
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateVersion throws SecurityException when not authorised")
    void validateVersionDenied() {
        kernelAdapter.denyAll = true;
        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(VERSION_ID, List.of(), List.of());

        assertThatThrownBy(() -> runPromise(() -> service.validateVersion(ctx, command)))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("listResults throws SecurityException when not authorised")
    void listResultsDenied() {
        kernelAdapter.denyAll = true;

        assertThatThrownBy(() -> runPromise(() -> service.listResults(ctx, VERSION_ID)))
            .isInstanceOf(SecurityException.class);
    }

    // -------------------------------------------------------------------------
    // Not found
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateVersion throws NoSuchElementException when version not found")
    void validateVersionNotFound() {
        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(VERSION_ID, List.of(), List.of());

        assertThatThrownBy(() -> runPromise(() -> service.validateVersion(ctx, command)))
            .isInstanceOf(NoSuchElementException.class);
    }

    // -------------------------------------------------------------------------
    // Forbidden terms
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateVersion produces FAIL finding for forbidden term in body text")
    void forbiddenTermProducesFail() {
        ContentVersion version = buildVersion(List.of(
            new ContentBlock("BODY", "BODY_COPY", "Buy now and get FREE money back", 0)),
            List.of(), List.of(someDisclosure()));
        versionRepository.store(version);

        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(
                VERSION_ID, List.of("free money"), List.of());

        ContentValidationResult result = runPromise(() -> service.validateVersion(ctx, command));

        assertThat(result.outcome()).isEqualTo(ValidationOutcome.FAIL);
        assertThat(result.findings()).anyMatch(f ->
            f.severity() == ValidationSeverity.FAIL
            && f.ruleCode().equals(ContentValidationServiceImpl.RULE_FORBIDDEN_TERM));
    }

    @Test
    @DisplayName("validateVersion forbidden term check is case-insensitive")
    void forbiddenTermCaseInsensitive() {
        ContentVersion version = buildVersion(List.of(
            new ContentBlock("B1", "BODY_COPY", "GUARANTEED results every day", 0)),
            List.of(), List.of(someDisclosure()));
        versionRepository.store(version);

        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(
                VERSION_ID, List.of("guaranteed"), List.of());

        ContentValidationResult result = runPromise(() -> service.validateVersion(ctx, command));

        assertThat(result.hasFails()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Required claims
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateVersion produces FAIL when required claim is missing")
    void requiredClaimMissingProducesFail() {
        ContentVersion version = buildVersion(
            List.of(new ContentBlock("B1", "BODY_COPY", "text", 0)),
            List.of(),   // no claims
            List.of(someDisclosure()));
        versionRepository.store(version);

        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(
                VERSION_ID, List.of(), List.of("claim-required-1"));

        ContentValidationResult result = runPromise(() -> service.validateVersion(ctx, command));

        assertThat(result.outcome()).isEqualTo(ValidationOutcome.FAIL);
        assertThat(result.findings()).anyMatch(f ->
            f.ruleCode().equals(ContentValidationServiceImpl.RULE_REQUIRED_CLAIM_MISSING));
    }

    @Test
    @DisplayName("validateVersion passes when required claim is present")
    void requiredClaimPresent() {
        ContentVersion version = buildVersion(
            List.of(new ContentBlock("B1", "BODY_COPY", "text", 0)),
            List.of(new ClaimReference("claim-required-1", "claim text", "VERIFIED")),
            List.of(someDisclosure()));
        versionRepository.store(version);

        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(
                VERSION_ID, List.of(), List.of("claim-required-1"));

        ContentValidationResult result = runPromise(() -> service.validateVersion(ctx, command));

        assertThat(result.findings()).noneMatch(f ->
            f.ruleCode().equals(ContentValidationServiceImpl.RULE_REQUIRED_CLAIM_MISSING));
    }

    // -------------------------------------------------------------------------
    // Unverified claims
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateVersion produces WARN for claim with UNVERIFIED source")
    void unverifiedClaimProducesWarn() {
        ContentVersion version = buildVersion(
            List.of(new ContentBlock("B1", "BODY_COPY", "text", 0)),
            List.of(new ClaimReference("claim-1", "text", "UNVERIFIED")),
            List.of(someDisclosure()));
        versionRepository.store(version);

        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(
                VERSION_ID, List.of(), List.of());

        ContentValidationResult result = runPromise(() -> service.validateVersion(ctx, command));

        assertThat(result.outcome()).isEqualTo(ValidationOutcome.WARN);
        assertThat(result.findings()).anyMatch(f ->
            f.ruleCode().equals(ContentValidationServiceImpl.RULE_UNVERIFIED_CLAIM)
            && f.severity() == ValidationSeverity.WARN);
    }

    // -------------------------------------------------------------------------
    // Disclosures
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateVersion produces WARN when no disclosures attached")
    void noDisclosuresProducesWarn() {
        ContentVersion version = buildVersion(
            List.of(new ContentBlock("B1", "BODY_COPY", "text", 0)),
            List.of(),
            List.of());   // no disclosures
        versionRepository.store(version);

        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(
                VERSION_ID, List.of(), List.of());

        ContentValidationResult result = runPromise(() -> service.validateVersion(ctx, command));

        assertThat(result.findings()).anyMatch(f ->
            f.ruleCode().equals(ContentValidationServiceImpl.RULE_NO_DISCLOSURES));
    }

    @Test
    @DisplayName("validateVersion returns PASS when no issues found")
    void passWhenNoIssues() {
        ContentVersion version = buildVersion(
            List.of(new ContentBlock("B1", "BODY_COPY", "clean text", 0)),
            List.of(new ClaimReference("claim-ok", "claim text", "VERIFIED_SOURCE")),
            List.of(someDisclosure()));
        versionRepository.store(version);

        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(
                VERSION_ID, List.of(), List.of());

        ContentValidationResult result = runPromise(() -> service.validateVersion(ctx, command));

        assertThat(result.outcome()).isEqualTo(ValidationOutcome.PASS);
        assertThat(result.findings()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Persistence and audit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateVersion persists result and records audit event")
    void persistsResultAndAudit() {
        ContentVersion version = buildVersion(
            List.of(new ContentBlock("B1", "BODY_COPY", "text", 0)),
            List.of(),
            List.of(someDisclosure()));
        versionRepository.store(version);

        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(
                VERSION_ID, List.of(), List.of());

        runPromise(() -> service.validateVersion(ctx, command));

        assertThat(resultRepository.findAll()).hasSize(1);
        assertThat(kernelAdapter.auditedActions).contains("content-version-validated");
    }

    // -------------------------------------------------------------------------
    // listResults
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("listResults returns empty when no results exist")
    void listResultsEmpty() {
        List<ContentValidationResult> results = runPromise(() -> service.listResults(ctx, VERSION_ID));

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("listResults returns stored results after validation")
    void listResultsAfterValidation() {
        ContentVersion version = buildVersion(
            List.of(new ContentBlock("B1", "BODY_COPY", "text", 0)),
            List.of(),
            List.of(someDisclosure()));
        versionRepository.store(version);

        ContentValidationService.ValidateContentVersionCommand command =
            new ContentValidationService.ValidateContentVersionCommand(
                VERSION_ID, List.of(), List.of());

        runPromise(() -> service.validateVersion(ctx, command));

        List<ContentValidationResult> results = runPromise(() -> service.listResults(ctx, VERSION_ID));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).versionId()).isEqualTo(VERSION_ID);
    }

    // -------------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateVersion rejects blank versionId")
    void validateVersionRejectsBlankVersionId() {
        assertThatThrownBy(() -> new ContentValidationService.ValidateContentVersionCommand(
            " ", List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("listResults rejects blank versionId")
    void listResultsRejectsBlankVersionId() {
        assertThatThrownBy(() -> runPromise(() -> service.listResults(ctx, " ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ContentVersion buildVersion(
            List<ContentBlock> blocks,
            List<ClaimReference> claims,
            List<DisclosureReference> disclosures) {
        return ContentVersion.builder()
            .versionId(VERSION_ID)
            .itemId(ITEM_ID)
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .versionNumber(1)
            .status(ContentVersionStatus.DRAFT)
            .contentBlocks(blocks)
            .claimReferences(claims)
            .disclosureReferences(disclosures)
            .generatorMetadata(new GeneratorMetadata(
                "v1.0", "p1.0", "DETERMINISTIC", Instant.now()))
            .createdAt(Instant.now())
            .createdBy("user-alice")
            .build();
    }

    private static DisclosureReference someDisclosure() {
        return new DisclosureReference("disc-1", "Terms and conditions apply", "LEGAL");
    }

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        boolean denyAll = false;
        final List<String> auditedActions = new ArrayList<>();

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(
                DmOperationContext ctx, String resource, String action) {
            return Promise.of(!denyAll);
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext ctx, String entityId, String action,
                Map<String, Object> details) {
            auditedActions.add(action);
            return Promise.of("audit-id");
        }

        @Override
        public Promise<Boolean> verifyConsent(
                DmOperationContext ctx, String subjectId, String purpose) {
            return Promise.of(!denyAll);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext ctx, String operationType,
                String subjectId, String description) {
            return Promise.of("approval-id");
        }
    }

    static final class InMemoryContentItemVersionRepository implements ContentItemVersionRepository {
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
            return Promise.of(store.values().stream()
                .filter(v -> v.getItemId().equals(itemId)
                    && v.getStatus() == ContentVersionStatus.APPROVED)
                .findFirst());
        }

        @Override
        public Promise<List<ContentVersion>> findByItemId(DmWorkspaceId workspaceId, String itemId) {
            return Promise.of(store.values().stream()
                .filter(v -> v.getItemId().equals(itemId))
                .toList());
        }

        @Override
        public Promise<Optional<ContentVersion>> findLatestByItemId(DmWorkspaceId workspaceId, String itemId) {
            return Promise.of(store.values().stream()
                .filter(v -> v.getItemId().equals(itemId))
                .max(java.util.Comparator.comparingInt(ContentVersion::getVersionNumber)));
        }
    }

    static final class InMemoryValidationResultRepository implements ContentValidationResultRepository {
        private final List<ContentValidationResult> store = new CopyOnWriteArrayList<>();

        List<ContentValidationResult> findAll() {
            return List.copyOf(store);
        }

        @Override
        public Promise<ContentValidationResult> save(DmWorkspaceId workspaceId, ContentValidationResult result) {
            store.add(result);
            return Promise.of(result);
        }

        @Override
        public Promise<List<ContentValidationResult>> findByVersionId(DmWorkspaceId workspaceId, String versionId) {
            return Promise.of(store.stream()
                .filter(r -> r.versionId().equals(versionId))
                .toList());
        }
    }
}
