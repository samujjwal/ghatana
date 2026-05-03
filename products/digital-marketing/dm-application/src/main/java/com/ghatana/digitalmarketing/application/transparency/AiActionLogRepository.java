package com.ghatana.digitalmarketing.application.transparency;

import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository contract for DMOS AI action log entries.
 *
 * @doc.type interface
 * @doc.purpose Persistence SPI for transparency timeline
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AiActionLogRepository {

    Promise<AiActionLogEntry> save(AiActionLogEntry entry);

    Promise<Optional<AiActionLogEntry>> findById(String workspaceId, String actionId);

    Promise<List<AiActionLogEntry>> findByWorkspace(
        String workspaceId,
        String correlationId,
        String relatedEntityId,
        int limit
    );
}
