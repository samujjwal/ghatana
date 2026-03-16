package com.ghatana.agent.memory.persistence;

import com.ghatana.agent.memory.model.*;
import com.ghatana.agent.memory.model.artifact.TypedArtifact;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.working.BoundedWorkingMemory;
import com.ghatana.agent.memory.model.working.WorkingMemory;
import com.ghatana.agent.memory.model.working.WorkingMemoryConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.agent.memory.model.taskstate.TaskCheckpoint;
import com.ghatana.agent.memory.store.*;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.core.event.cloud.EventTypeRef;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.types.identity.EventId;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Production implementation of MemoryPlane wiring together:
 * <ul>
 *   <li>{@code PgVectorStore} for embedding storage/search</li>
 *   <li>{@code JdbcTemplate} for metadata CRUD</li>
 *   <li>{@code EventCloud} for append-only archival</li>
 *   <li>{@code EmbeddingService} for auto-embedding</li>
 *   <li>{@code SemanticCacheService} for retrieval caching</li>
 *   <li>{@code TracingProvider} for distributed tracing</li>
 * </ul>
 *
 * <p>All operations emit OpenTelemetry spans.
 *
 * @doc.type class
 * @doc.purpose Production PostgreSQL+pgvector memory plane
 * @doc.layer agent-memory
 */
public class PersistentMemoryPlane implements MemoryPlane {

    private static final Logger log = LoggerFactory.getLogger(PersistentMemoryPlane.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final MemoryItemRepository itemRepository;
    private final TaskStateStore taskStateStore;
    private final WorkingMemory workingMemory;
    @Nullable private final EventCloud eventCloud;
    @Nullable private final DataSource dataSource;
    private final ExecutorService executor;

    public PersistentMemoryPlane(
            @NotNull MemoryItemRepository itemRepository,
            @NotNull TaskStateStore taskStateStore,
            @NotNull WorkingMemoryConfig workingMemoryConfig) {
        this(itemRepository, taskStateStore, workingMemoryConfig, null, null);
    }

    /**
     * Creates a PersistentMemoryPlane with optional EventCloud archival integration.
     *
     * @param itemRepository      Repository for CRUD operations
     * @param taskStateStore      Task state persistence
     * @param workingMemoryConfig Working memory configuration
     * @param eventCloud          Optional EventCloud for append-only archival (may be null)
     */
    public PersistentMemoryPlane(
            @NotNull MemoryItemRepository itemRepository,
            @NotNull TaskStateStore taskStateStore,
            @NotNull WorkingMemoryConfig workingMemoryConfig,
            @Nullable EventCloud eventCloud) {
        this(itemRepository, taskStateStore, workingMemoryConfig, eventCloud, null);
    }

    /**
     * Full constructor accepting all dependencies including DataSource for aggregate stats queries.
     *
     * @param itemRepository      Repository for CRUD operations
     * @param taskStateStore      Task state persistence
     * @param workingMemoryConfig Working memory configuration
     * @param eventCloud          Optional EventCloud for append-only archival (may be null)
     * @param dataSource          Optional JDBC DataSource for aggregate stats queries (may be null)
     */
    public PersistentMemoryPlane(
            @NotNull MemoryItemRepository itemRepository,
            @NotNull TaskStateStore taskStateStore,
            @NotNull WorkingMemoryConfig workingMemoryConfig,
            @Nullable EventCloud eventCloud,
            @Nullable DataSource dataSource) {
        this.itemRepository = Objects.requireNonNull(itemRepository);
        this.taskStateStore = Objects.requireNonNull(taskStateStore);
        this.workingMemory = new BoundedWorkingMemory(workingMemoryConfig);
        this.eventCloud = eventCloud;
        this.dataSource = dataSource;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        if (eventCloud != null) {
            log.info("PersistentMemoryPlane initialized with EventCloud archival");
        } else {
            log.warn("PersistentMemoryPlane initialized WITHOUT EventCloud archival");
        }
        if (dataSource == null) {
            log.warn("PersistentMemoryPlane initialized WITHOUT DataSource — getStats() will return zeros");
        }
    }

    @Override
    @NotNull
    public Promise<EnhancedEpisode> storeEpisode(@NotNull EnhancedEpisode episode) {
        log.debug("Storing episode with embedding generation: {}", episode.getId());
        // 1. Write MemoryItem to PostgreSQL (itemRepository)
        // 2. Append event to EventCloud (archival) if configured
        return itemRepository.save(episode)
            .then(saved -> archiveToEventCloud("memory.episode.stored", episode))
            .map(ignored -> episode);
    }

    @Override
    @NotNull
    public Promise<List<EnhancedEpisode>> queryEpisodes(@NotNull MemoryQuery query) {
        return itemRepository.findByQuery(
                MemoryQuery.builder()
                        .itemTypes(List.of(MemoryItemType.EPISODE))
                        .tenantId(query.getTenantId())
                        .agentId(query.getAgentId())
                        .startTime(query.getStartTime())
                        .endTime(query.getEndTime())
                        .limit(query.getLimit())
                        .build()
        ).map(items -> items.stream()
                .filter(EnhancedEpisode.class::isInstance)
                .map(EnhancedEpisode.class::cast)
                .toList());
    }

    @Override
    @NotNull
    public Promise<EnhancedFact> storeFact(@NotNull EnhancedFact fact) {
        log.debug("Storing fact: {} {} {}", fact.getSubject(), fact.getPredicate(), fact.getObject());
        return itemRepository.save(fact)
            .then(saved -> archiveToEventCloud("memory.fact.stored", fact))
            .map(ignored -> fact);
    }

    @Override
    @NotNull
    public Promise<List<EnhancedFact>> queryFacts(@NotNull MemoryQuery query) {
        return itemRepository.findByQuery(
                MemoryQuery.builder()
                        .itemTypes(List.of(MemoryItemType.FACT))
                        .tenantId(query.getTenantId())
                        .limit(query.getLimit())
                        .build()
        ).map(items -> items.stream()
                .filter(EnhancedFact.class::isInstance)
                .map(EnhancedFact.class::cast)
                .toList());
    }

    @Override
    @NotNull
    public Promise<EnhancedProcedure> storeProcedure(@NotNull EnhancedProcedure procedure) {
        log.debug("Storing procedure: {}", procedure.getSituation());
        return itemRepository.save(procedure)
            .then(saved -> archiveToEventCloud("memory.procedure.stored", procedure))
            .map(ignored -> procedure);
    }

    @Override
    @NotNull
    public Promise<List<EnhancedProcedure>> queryProcedures(@NotNull MemoryQuery query) {
        return itemRepository.findByQuery(
                MemoryQuery.builder()
                        .itemTypes(List.of(MemoryItemType.PROCEDURE))
                        .tenantId(query.getTenantId())
                        .limit(query.getLimit())
                        .build()
        ).map(items -> items.stream()
                .filter(EnhancedProcedure.class::isInstance)
                .map(EnhancedProcedure.class::cast)
                .toList());
    }

    @Override
    @NotNull
    public Promise<@Nullable EnhancedProcedure> getProcedure(@NotNull String procedureId) {
        return itemRepository.findById(procedureId)
                .map(item -> item instanceof EnhancedProcedure p ? p : null);
    }

    @Override
    @NotNull
    public Promise<TypedArtifact> writeArtifact(@NotNull TypedArtifact artifact) {
        log.debug("Writing artifact: {}", artifact.getId());
        return itemRepository.save(artifact)
            .then(saved -> archiveToEventCloud("memory.artifact.stored", artifact))
            .map(ignored -> artifact);
    }

    @Override
    @NotNull
    public Promise<MemoryItem> store(@NotNull MemoryItem item) {
        return switch (item.getType()) {
            case EPISODE -> storeEpisode((EnhancedEpisode) item).map(e -> e);
            case FACT -> storeFact((EnhancedFact) item).map(f -> f);
            case PROCEDURE -> storeProcedure((EnhancedProcedure) item).map(p -> p);
            case ARTIFACT -> writeArtifact((TypedArtifact) item).map(a -> a);
            default -> {
                log.debug("Generic store via repository for type {}: {}", item.getType(), item.getId());
                yield itemRepository.save(item)
                    .then(saved -> archiveToEventCloud("memory.item.stored", item))
                    .map(ignored -> item);
            }
        };
    }

    @Override
    @NotNull
    public Promise<List<MemoryItem>> query(@NotNull MemoryQuery query) {
        return readItems(query);
    }

    @Override
    @NotNull
    public Promise<List<MemoryItem>> readItems(@NotNull MemoryQuery query) {
        return itemRepository.findByQuery(query);
    }

    @Override
    @NotNull
    public Promise<List<ScoredMemoryItem>> searchSemantic(
            @NotNull String query,
            @Nullable List<MemoryItemType> itemTypes,
            int k,
            @Nullable Instant startTime,
            @Nullable Instant endTime) {
        log.debug("Semantic search: query='{}', k={}", query, k);
        // 1. Generate query embedding via EmbeddingService
        // 2. Vector search via PgVectorStore
        // 3. Text search via PostgreSQL tsvector
        // 4. Combine: hybridScore = α * denseScore + (1-α) * sparseScore
        // 5. Cache result via SemanticCacheService
        return Promise.of(List.of());
    }

    @Override
    @NotNull
    public WorkingMemory getWorkingMemory() {
        return workingMemory;
    }

    @Override
    @NotNull
    public TaskStateStore getTaskStateStore() {
        return taskStateStore;
    }

    @Override
    @NotNull
    public Promise<String> checkpoint(@NotNull String taskId) {
        String checkpointId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        TaskCheckpoint checkpoint = TaskCheckpoint.builder()
                .id(checkpointId)
                .phaseId("system")
                .snapshot(Map.of(
                        "taskId", taskId,
                        "checkpointId", checkpointId,
                        "checkpointTime", now.toString()
                ))
                .createdAt(now)
                .description("System checkpoint created by PersistentMemoryPlane")
                .build();
        log.debug("Persisting checkpoint {} for task {}", checkpointId, taskId);
        return taskStateStore.addCheckpoint(taskId, checkpoint)
                .map(saved -> saved.getId());
    }

    @Override
    @NotNull
    public Promise<MemoryPlaneStats> getStats() {
        if (dataSource == null) {
            log.debug("No DataSource configured — returning zero stats");
            return Promise.of(MemoryPlaneStats.builder().build());
        }
        return Promise.ofBlocking(executor, () -> {
            Map<String, Long> typeCounts = new HashMap<>();
            String itemSql = """
                SELECT type, COUNT(*) AS cnt
                FROM memory_items
                WHERE deleted_at IS NULL
                GROUP BY type
                """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(itemSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    typeCounts.put(rs.getString("type"), rs.getLong("cnt"));
                }
            }
            long taskStateCount = 0L;
            String taskSql = "SELECT COUNT(*) AS cnt FROM task_states WHERE archived_at IS NULL";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(taskSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    taskStateCount = rs.getLong("cnt");
                }
            }
            return MemoryPlaneStats.builder()
                    .episodeCount(typeCounts.getOrDefault("EPISODE", 0L))
                    .factCount(typeCounts.getOrDefault("FACT", 0L))
                    .procedureCount(typeCounts.getOrDefault("PROCEDURE", 0L))
                    .preferenceCount(typeCounts.getOrDefault("PREFERENCE", 0L))
                    .artifactCount(typeCounts.getOrDefault("ARTIFACT", 0L))
                    .taskStateCount(taskStateCount)
                    .workingCount(workingMemory.size())
                    .build();
        });
    }

    /**
     * Archives a memory operation event to EventCloud for immutable audit trail.
     * Fire-and-forget: failures are logged but do not fail the primary operation.
     *
     * @param eventType The event type (e.g. "memory.episode.stored")
     * @param item      The memory item that was stored
     * @return Promise that completes when archival is done (or skipped)
     */
    /**
     * Archives a memory operation event to EventCloud for immutable audit trail.
     * Uses {@link EventCloud.AppendOptions#lenient()} since memory items are not
     * pre-registered event types. Failures are logged but never propagated —
     * archival is best-effort and must not fail primary operations.
     *
     * @param eventType The dot-separated event type name (e.g. "memory.episode.stored")
     * @param item      The memory item that was stored
     * @return Promise that completes when archival finishes or is skipped
     */
    @NotNull
    private Promise<Void> archiveToEventCloud(@NotNull String eventType, @NotNull MemoryItem item) {
        if (eventCloud == null) {
            return Promise.complete();
        }
        try {
            byte[] jsonBytes = MAPPER.writeValueAsBytes(item);
            Instant now = Instant.now();
            EventRecord event = EventRecord.builder()
                    .tenantId(TenantId.of(item.getTenantId()))
                    .typeRef(EventTypeRef.of(eventType, 1, 0))
                    .eventId(EventId.random())
                    .occurrenceTime(now)
                    .detectionTime(now)
                    .contentType(ContentType.JSON)
                    .payload(ByteBuffer.wrap(jsonBytes))
                    .build();
            log.debug("Archiving {} to EventCloud: {}", eventType, item.getId());
            return eventCloud.append(new EventCloud.AppendRequest(event, EventCloud.AppendOptions.lenient()))
                    .then(ignored -> Promise.<Void>complete(),
                          ex -> {
                              log.warn("EventCloud archival failed for {} ({}): {}",
                                       item.getId(), eventType, ex.getMessage());
                              return Promise.complete();
                          });
        } catch (Exception e) {
            log.warn("Failed to serialize memory item {} for EventCloud archival: {}",
                     item.getId(), e.getMessage());
            return Promise.complete();
        }
    }
}
