package com.ghatana.digitalmarketing.application.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentItem;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository contract for {@link ContentItem} persistence.
 *
 * @doc.type interface
 * @doc.purpose DMOS content item persistence contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ContentItemRepository {

    Promise<ContentItem> save(ContentItem item);

    Promise<Optional<ContentItem>> findById(DmWorkspaceId workspaceId, String itemId);
}
