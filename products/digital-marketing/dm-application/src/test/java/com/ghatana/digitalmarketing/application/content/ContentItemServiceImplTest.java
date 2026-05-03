package com.ghatana.digitalmarketing.application.content;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ClaimReference;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
import com.ghatana.digitalmarketing.domain.content.ContentItem;
import com.ghatana.digitalmarketing.domain.content.ContentItemType;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.ContentVersionStatus;
import com.ghatana.digitalmarketing.domain.content.DisclosureReference;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("ContentItemServiceImpl")
class ContentItemServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryContentItemRepository itemRepository;
    private InMemoryContentItemVersionRepository versionRepository;
    private ContentItemServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter     = new RecordingKernelAdapter();
        itemRepository    = new InMemoryContentItemRepository();
        versionRepository = new InMemoryContentItemVersionRepository();
        service = new ContentItemServiceImpl(kernelAdapter, itemRepository, versionRepository);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-alice"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private static ContentItemService.CreateContentItemCommand itemCommand() {
        return new ContentItemService.CreateContentItemCommand(
            "Landing Page", ContentItemType.LANDING_PAGE, "A test page");
    }

    private static ContentItemService.CreateContentVersionCommand versionCommand(String itemId) {
        return new ContentItemService.CreateContentVersionCommand(
            itemId,
            List.of(new ContentBlock("blk-1", "HERO", "Hero text", 0)),
            List.of(new ClaimReference("clm-1", "Top product", "RESEARCH")),
            List.of(new DisclosureReference("dis-1", "Results may vary", "LEGAL")),
            "model-v1",
            "prompt-v2",
            "TEMPLATE"
        );
    }

    // ---- createItem ----

    @Test
    @DisplayName("createItem persists and returns ContentItem with generated id")
    void shouldCreateItem() {
        ContentItem item = runPromise(() -> service.createItem(ctx, itemCommand()));

        assertThat(item.getItemId()).isNotBlank();
        assertThat(item.getTitle()).isEqualTo("Landing Page");
        assertThat(item.getItemType()).isEqualTo(ContentItemType.LANDING_PAGE);
        assertThat(item.getWorkspaceId().getValue()).isEqualTo("ws-1");
        assertThat(item.getCreatedBy()).isEqualTo("user-alice");
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("content-item-created");
    }

    @Test
    @DisplayName("createItem with null description defaults to empty string")
    void shouldDefaultNullDescriptionToEmpty() {
        ContentItemService.CreateContentItemCommand cmd =
            new ContentItemService.CreateContentItemCommand("Ad Copy", ContentItemType.AD, null);
        ContentItem item = runPromise(() -> service.createItem(ctx, cmd));
        assertThat(item.getDescription()).isEmpty();
    }

    @Test
    @DisplayName("createItem with non-null description preserves it")
    void shouldPreserveNonNullDescription() {
        ContentItemService.CreateContentItemCommand cmd =
            new ContentItemService.CreateContentItemCommand("Ad Copy", ContentItemType.AD, "Test description");
        ContentItem item = runPromise(() -> service.createItem(ctx, cmd));
        assertThat(item.getDescription()).isEqualTo("Test description");
    }

    @Test
    @DisplayName("createItem denied throws SecurityException")
    void shouldDenyUnauthorizedCreateItem() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.createItem(ctx, itemCommand())));
    }

    // ---- createVersion ----

    @Test
    @DisplayName("createVersion assigns version number 1 for first version")
    void shouldAssignVersionNumberOne() {
        ContentItem item = runPromise(() -> service.createItem(ctx, itemCommand()));
        ContentVersion version = runPromise(() -> service.createVersion(ctx, versionCommand(item.getItemId())));

        assertThat(version.getVersionNumber()).isEqualTo(1);
        assertThat(version.getItemId()).isEqualTo(item.getItemId());
        assertThat(version.getStatus()).isEqualTo(ContentVersionStatus.DRAFT);
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("content-version-created");
    }

    @Test
    @DisplayName("createVersion increments version number for subsequent versions")
    void shouldIncrementVersionNumber() {
        ContentItem item = runPromise(() -> service.createItem(ctx, itemCommand()));
        runPromise(() -> service.createVersion(ctx, versionCommand(item.getItemId())));
        ContentVersion v2 = runPromise(() -> service.createVersion(ctx, versionCommand(item.getItemId())));

        assertThat(v2.getVersionNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("createVersion denied throws SecurityException")
    void shouldDenyUnauthorizedCreateVersion() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.createVersion(ctx, versionCommand("item-x"))));
    }

    // ---- getLatestApproved ----

    @Test
    @DisplayName("getLatestApproved returns approved version")
    void shouldReturnApprovedVersion() {
        ContentItem item = runPromise(() -> service.createItem(ctx, itemCommand()));
        ContentVersion v = runPromise(() -> service.createVersion(ctx, versionCommand(item.getItemId())));
        runPromise(() -> service.approveVersion(ctx, v.getVersionId()));

        ContentVersion approved = runPromise(() -> service.getLatestApproved(ctx, item.getItemId()));
        assertThat(approved.isApproved()).isTrue();
    }

    @Test
    @DisplayName("getLatestApproved throws NoSuchElementException when none approved")
    void shouldThrowWhenNoApprovedVersion() {
        ContentItem item = runPromise(() -> service.createItem(ctx, itemCommand()));
        runPromise(() -> service.createVersion(ctx, versionCommand(item.getItemId())));
        // version is still DRAFT — no approved version
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(ctx, item.getItemId())));
    }

    @Test
    @DisplayName("getLatestApproved denied throws SecurityException")
    void shouldDenyUnauthorizedGetLatestApproved() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestApproved(ctx, "item-x")));
    }

    // ---- approveVersion ----

    @Test
    @DisplayName("approveVersion transitions DRAFT → APPROVED")
    void shouldApproveDraftVersion() {
        ContentItem item = runPromise(() -> service.createItem(ctx, itemCommand()));
        ContentVersion v = runPromise(() -> service.createVersion(ctx, versionCommand(item.getItemId())));
        ContentVersion approved = runPromise(() -> service.approveVersion(ctx, v.getVersionId()));

        assertThat(approved.getStatus()).isEqualTo(ContentVersionStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo("user-alice");
        assertThat(approved.getApprovedAt()).isNotNull();
        assertThat(kernelAdapter.lastAuditAction).isEqualTo("content-version-approved");
    }

    @Test
    @DisplayName("approveVersion transitions PENDING_REVIEW → APPROVED")
    void shouldApprovePendingReviewVersion() {
        ContentItem item = runPromise(() -> service.createItem(ctx, itemCommand()));
        ContentVersion v = runPromise(() -> service.createVersion(ctx, versionCommand(item.getItemId())));
        // Manually put a PENDING_REVIEW version in the repo
        ContentVersion pending = v.submitForReview();
        versionRepository.store(pending);

        ContentVersion approved = runPromise(() -> service.approveVersion(ctx, pending.getVersionId()));
        assertThat(approved.getStatus()).isEqualTo(ContentVersionStatus.APPROVED);
    }

    @Test
    @DisplayName("approveVersion throws NoSuchElementException for missing version")
    void shouldThrowForMissingVersion() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.approveVersion(ctx, "nonexistent")));
    }

    @Test
    @DisplayName("approveVersion denied throws SecurityException")
    void shouldDenyUnauthorizedApprove() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.approveVersion(ctx, "ver-x")));
    }

    // ---- getVersionHistory ----

    @Test
    @DisplayName("getVersionHistory returns all versions for item")
    void shouldReturnVersionHistory() {
        ContentItem item = runPromise(() -> service.createItem(ctx, itemCommand()));
        runPromise(() -> service.createVersion(ctx, versionCommand(item.getItemId())));
        runPromise(() -> service.createVersion(ctx, versionCommand(item.getItemId())));

        List<ContentVersion> history = runPromise(() -> service.getVersionHistory(ctx, item.getItemId()));
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("getVersionHistory returns empty list when no versions created")
    void shouldReturnEmptyHistoryWhenNone() {
        ContentItem item = runPromise(() -> service.createItem(ctx, itemCommand()));
        List<ContentVersion> history = runPromise(() -> service.getVersionHistory(ctx, item.getItemId()));
        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("getVersionHistory denied throws SecurityException")
    void shouldDenyUnauthorizedGetHistory() {
        kernelAdapter.denyAll = true;
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getVersionHistory(ctx, "item-x")));
    }

    // ---- test doubles ----

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        boolean denyAll = false;
        String lastAuditAction;

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) {
            return Promise.of(!denyAll);
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext ctx, String entityId, String action,
                Map<String, Object> attributes) {
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
    }

    private static final class InMemoryContentItemRepository implements ContentItemRepository {
        private final Map<String, ContentItem> store = new HashMap<>();

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

    private static final class InMemoryContentItemVersionRepository implements ContentItemVersionRepository {
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
