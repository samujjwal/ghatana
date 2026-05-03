package com.ghatana.digitalmarketing.infra.content;

import com.ghatana.digitalmarketing.application.content.ContentItemRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentItem;
import io.activej.promise.Promise;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ContentItemRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Keys are composed as {@code "<workspaceId>:<itemId>"} to scope content items
 * within their workspace.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory content item persistence adapter for DMOS local and test deployments
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class InMemoryContentItemRepository implements ContentItemRepository {

    private final ConcurrentHashMap<String, ContentItem> store = new ConcurrentHashMap<>();

    @Override
    public Promise<ContentItem> save(ContentItem item) {
        Objects.requireNonNull(item, "item must not be null");
        store.put(key(item.getWorkspaceId(), item.getItemId()), item);
        return Promise.of(item);
    }

    @Override
    public Promise<Optional<ContentItem>> findById(DmWorkspaceId workspaceId, String itemId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        return Promise.of(Optional.ofNullable(store.get(key(workspaceId, itemId))));
    }

    private static String key(DmWorkspaceId workspaceId, String itemId) {
        return workspaceId.getValue() + ":" + itemId;
    }
}
