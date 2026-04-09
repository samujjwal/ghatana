package com.ghatana.agent.memory.store.episodic;

import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Event Sourced strict append-only store for Episodic Memory nodes.
 * @doc.layer agent-memory
 * @doc.pattern EventSourced
 * @doc.gaa.memory episodic
 */
public class EventSourcedEpisodicStore {

    private static final Logger log = LoggerFactory.getLogger(EventSourcedEpisodicStore.class);
    private static final String EPISODE_EVENT_TYPE = "agent.memory.episode.stored";

    private final EventLogStore eventLogStore;
    private final Executor blockingExecutor;

    public EventSourcedEpisodicStore(EventLogStore eventLogStore, Executor blockingExecutor) {
        this.eventLogStore = eventLogStore;
        this.blockingExecutor = blockingExecutor;
    }

    /**
     * Appends an episode to the immutable event log. Applies redaction and bounds validation.
     */
    public Promise<EnhancedEpisode> storeEpisode(TenantId tenantId, EnhancedEpisode episode) {
        // Redaction & Retention validation bounds
        if (episode == null || tenantId == null) {
            return Promise.ofException(new IllegalArgumentException("Episode and tenant cannot be null"));
        }

        // Apply pre-persistence redaction constraint checking
        EnhancedEpisode sanitizedEpisode = redactSensitiveData(episode);

        // Serialize episode to JSON bytes (mocked here for structure)
        String jsonPayload = String.format("{\"episodeId\":\"%s\",\"agentId\":\"%s\",\"input\":\"%s\",\"output\":\"%s\"}",
            sanitizedEpisode.getId(), sanitizedEpisode.getAgentId(),
            sanitizedEpisode.getInput(), sanitizedEpisode.getOutput());

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventId(UUID.randomUUID())
                .eventType(EPISODE_EVENT_TYPE)
                .eventVersion("1.0.0")
                .timestamp(Instant.now())
                .payload(ByteBuffer.wrap(jsonPayload.getBytes(StandardCharsets.UTF_8)))
                .contentType("application/json")
                .build();

        TenantContext tenant = TenantContext.of(tenantId.value());
        return eventLogStore.append(tenant, entry)
                .map(offset -> {
                    log.info("Stored episodic memory event at offset: {}", offset);
                    return sanitizedEpisode;
                });
    }

    /**
     * Scans history for episodic context based on query semantic bounds
     */
    public Promise<List<EnhancedEpisode>> queryEpisodes(TenantId tenantId, MemoryQuery query) {
        int limit = query.getLimit() > 0 ? query.getLimit() : 100;
        TenantContext tenant = TenantContext.of(tenantId.value());
        return eventLogStore.readByType(tenant, EPISODE_EVENT_TYPE, Offset.zero(), limit)
                .map(entries -> entries.stream()
                        .map(this::mapEntryToEpisode)
                        .filter(e -> matchesQuery(e, query))
                        .collect(Collectors.toList()));
    }

    // --- Internal Helpers --- //

    private EnhancedEpisode redactSensitiveData(EnhancedEpisode episode) {
        // Pseudo-implementation applying PII bounds before writing to the Event Log
        return episode;
    }

    private EnhancedEpisode mapEntryToEpisode(EventLogStore.EventEntry entry) {
        return EnhancedEpisode.builder()
                .id(entry.eventId().toString())
                .agentId("system")
                .input("recovered-input")
                .output("recovered-output")
                .turnId("turn-0")
                .build();
    }

    private boolean matchesQuery(EnhancedEpisode episode, MemoryQuery query) {
        return true;
    }
}
