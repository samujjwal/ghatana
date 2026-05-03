package com.ghatana.digitalmarketing.infra.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentItem;
import com.ghatana.digitalmarketing.domain.content.ContentItemType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("InMemoryContentItemRepository")
class InMemoryContentItemRepositoryTest extends EventloopTestBase {

    private InMemoryContentItemRepository repository;

    private static final DmWorkspaceId WS_1 = DmWorkspaceId.of("ws-1");
    private static final DmWorkspaceId WS_2 = DmWorkspaceId.of("ws-2");

    @BeforeEach
    void setUp() {
        repository = new InMemoryContentItemRepository();
    }

    @Test
    @DisplayName("save returns the saved item")
    void shouldReturnSavedItem() {
        ContentItem item = buildItem(WS_1, "item-1");
        ContentItem saved = runPromise(() -> repository.save(item));
        assertThat(saved).isSameAs(item);
    }

    @Test
    @DisplayName("findById returns saved item within same workspace")
    void shouldFindSavedItem() {
        runPromise(() -> repository.save(buildItem(WS_1, "item-1")));

        Optional<ContentItem> found = runPromise(() -> repository.findById(WS_1, "item-1"));

        assertThat(found).isPresent();
        assertThat(found.get().getItemId()).isEqualTo("item-1");
    }

    @Test
    @DisplayName("findById returns empty for missing item")
    void shouldReturnEmptyForMissing() {
        Optional<ContentItem> found = runPromise(() -> repository.findById(WS_1, "missing"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("workspace isolation: item saved in ws-1 is not visible from ws-2")
    void shouldIsolateWorkspaces() {
        runPromise(() -> repository.save(buildItem(WS_1, "item-1")));

        Optional<ContentItem> found = runPromise(() -> repository.findById(WS_2, "item-1"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save overwrites an existing item with the same id")
    void shouldOverwriteExistingItem() {
        runPromise(() -> repository.save(buildItem(WS_1, "item-1")));
        runPromise(() -> repository.save(buildItem(WS_1, "item-1")));

        Optional<ContentItem> found = runPromise(() -> repository.findById(WS_1, "item-1"));
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("save rejects null")
    void shouldRejectNullItem() {
        assertThatNullPointerException().isThrownBy(() -> repository.save(null));
    }

    @Test
    @DisplayName("findById rejects null arguments")
    void shouldRejectNullArgs() {
        assertThatNullPointerException().isThrownBy(() -> repository.findById(null, "item-1"));
        assertThatNullPointerException().isThrownBy(() -> repository.findById(WS_1, null));
    }

    private static ContentItem buildItem(DmWorkspaceId workspaceId, String itemId) {
        return ContentItem.builder()
            .itemId(itemId)
            .workspaceId(workspaceId)
            .title("Test Item " + itemId)
            .itemType(ContentItemType.AD)
            .createdAt(Instant.now())
            .createdBy("test-user")
            .build();
    }
}
