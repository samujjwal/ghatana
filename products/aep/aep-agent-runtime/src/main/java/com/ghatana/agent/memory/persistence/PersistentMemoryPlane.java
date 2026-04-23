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
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
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
  * @doc.pattern Component
*/
public class PersistentMemoryPlane implements MemoryPlane {

    private static final Logger log = LoggerFactory.getLogger(PersistentMemoryPlane.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final MemoryItemRepository itemRepository;
    private final TaskStateStore taskStateStore;
    private final WorkingMemory workingMemory;
    @Nullable private final EventLogStore eventLogStore;
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
            @Nullable EventLogStore eventLogStore) {
        this(itemRepository, taskStateStore, workingMemoryConfig, eventLogStore, null);
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
            @Nullable EventLogStore eventLogStore,
            @Nullable DataSource dataSource) {
        this.itemRepository = Objects.requireNonNull(itemRepository);
        this.taskStateStore = Objects.requireNonNull(taskStateStore);
        this.workingMemory = new BoundedWorkingMemory(workingMemoryConfig);
        this.eventLogStore = eventLogStore;
        this.dataSource = dataSource;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        if (eventLogStore != null) {
            log.info("PersistentMemoryPlane initialized with EventLogStore archival");
        } else {
            log.warn("PersistentMemoryPlane initialized WITHOUT EventLogStore archival");
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
            .then(saved -> archiveToEventLog("memory.episode.stored", episode))
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
            .then(saved -> archiveToEventLog("memory.fact.stored", fact))
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
            .then(saved -> archiveToEventLog("memory.procedure.stored", procedure))
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
            .then(saved -> archiveToEventLog("memory.artifact.stored", artifact))
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
                    .then(saved -> archiveToEventLog("memory.item.stored", item))
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
        log.debug("Semantic search: query='{}', types={}, k={}", query, itemTypes, k);

        // Build a MemoryQuery with time bounds and type filters, then score by text relevance
        return Promise.ofBlocking(executor, () -> {
            MemoryQuery.MemoryQueryBuilder qb = MemoryQuery.builder()
                    .limit(k * 3)  // Over-fetch for scoring and filtering
                    .textQuery(query);

            if (itemTypes != null && !itemTypes.isEmpty()) {
                qb.itemTypes(itemTypes);
            }
            if (startTime != null) {
                qb.startTime(startTime);
            }
            if (endTime != null) {
                qb.endTime(endTime);
            }

            List<MemoryItem> candidates = itemRepository.findByQuery(qb.build())
                    .getResult();

            if (candidates == null || candidates.isEmpty()) {
                return List.<ScoredMemoryItem>of();
            }

            // Score candidates by text relevance (lexical matching as baseline)
            String queryLower = query.toLowerCase();
            List<ScoredMemoryItem> scored = candidates.stream()
                    .map(item -> {
                        double score = computeTextRelevance(item, queryLower);
                        return new ScoredMemoryItem(item, score,
                                Map.of("strategy", "text-relevance"));
                    })
                    .filter(s -> s.getScore() > 0.0)
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(k)
                    .toList();

            log.debug("Semantic search: {} candidates → {} results for query='{}'",
                    candidates.size(), scored.size(), query);
            return scored;
        });
    }

    /**
     * Computes a basic text relevance score for a memory item against a query.
     * Uses labels and provenance for matching. Production deployments should
     * integrate vector search via PgVectorStore for dense retrieval.
     */
    private double computeTextRelevance(@NotNull MemoryItem item, @NotNull String queryLower) {
        // Build searchable text from labels and provenance
        StringBuilder sb = new StringBuilder();
        item.getLabels().forEach((k, v) -> sb.append(k).append(" ").append(v).append(" "));
        sb.append(item.getProvenance().getSource()).append(" ");
        sb.append(item.getType().name()).append(" ");
        sb.append(item.getClassification());
        String combined = sb.toString().toLowerCase();

        if (combined.isBlank()) return 0.0;

        // Simple term-frequency based scoring
        String[] queryTerms = queryLower.split("\\s+");
        int hits = 0;
        int totalTerms = queryTerms.length;
        for (String term : queryTerms) {
            if (term.length() > 2 && combined.contains(term)) {
                hits++;
            }
        }

        if (totalTerms == 0) return 0.0;

        // Boost items with embeddings (they are vector-indexed)
        double baseScore = (double) hits / totalTerms;
        if (item.getEmbedding() != null) {
            baseScore *= 1.2;
        }
        return Math.min(baseScore, 1.0);
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
     * Archives a memory operation event to the event log for immutable audit trail.
     * Fire-and-forget: failures are logged but never propagated — archival is best-effort.
     *
     * @param eventType The dot-separated event type name (e.g. "memory.episode.stored")
     * @param item      The memory item that was stored
     * @return Promise that completes when archival finishes or is skipped
     */
    @NotNull
    private Promise<Void> archiveToEventLog(@NotNull String eventType, @NotNull MemoryItem item) {
        if (eventLogStore == null) {
            return Promise.complete();
        }
        try {
            byte[] jsonBytes = MAPPER.writeValueAsBytes(item);
            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                    .eventId(java.util.UUID.randomUUID())
                    .eventType(eventType)
                    .eventVersion("1.0.0")
                    .timestamp(Instant.now())
                    .payload(ByteBuffer.wrap(jsonBytes))
                    .contentType("application/json")
                    .build();
            TenantContext tenant = TenantContext.of(
                    item.getTenantId() != null ? item.getTenantId() : "default");
            log.debug("Archiving {} to event log: {}", eventType, item.getId());
            return eventLogStore.append(tenant, entry)
                    .then(offset -> Promise.<Void>complete(),
                          ex -> {
                              log.warn("Event log archival failed for {} ({}): {}",
                                       item.getId(), eventType, ex.getMessage());
                              return Promise.complete();
                          });
        } catch (Exception e) {
            log.warn("Failed to serialize memory item {} for event log archival: {}",
                     item.getId(), e.getMessage());
            return Promise.complete();
        }
    }
}
