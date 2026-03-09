package com.ghatana.eventlog.adapters.file;

import com.ghatana.contracts.event.v1.EventProto;
import com.ghatana.contracts.event.v1.GetEventRequestProto;
import com.ghatana.contracts.event.v1.GetEventResponseProto;
import com.ghatana.contracts.event.v1.IngestBatchRequestProto;
import com.ghatana.contracts.event.v1.IngestBatchResponseProto;
import com.ghatana.contracts.event.v1.IngestErrorCodeProto;
import com.ghatana.contracts.event.v1.IngestErrorProto;
import com.ghatana.contracts.event.v1.IngestRequestProto;
import com.ghatana.contracts.event.v1.IngestResponseProto;
import com.ghatana.contracts.event.v1.QueryEventsRequestProto;
import com.ghatana.contracts.event.v1.QueryEventsResponseProto;
import com.ghatana.eventlog.RetentionPolicy;
import com.ghatana.eventlog.ports.EventStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * File-based implementation of {@link EventStorePort} that stores events as files on disk.
 * <p>
 * This implementation provides durable storage of events with support for retention policies.
 * Events are stored in a directory structure organized by tenant and date.
 */
public final class FileBasedEventStore implements EventStorePort {
    private static final Logger log = LoggerFactory.getLogger(FileBasedEventStore.class);
    private static final String EVENT_FILE_EXTENSION = ".event";

    private final Path baseDir;
    private final ConcurrentHashMap<String, EventProto> memoryCache;

    /**
     * Creates a new file-based event store.
     *
     * @param baseDir The base directory where event files will be stored
     * @throws IllegalArgumentException if baseDir is null or not a directory
     */
    public FileBasedEventStore(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir cannot be null");
        this.memoryCache = new ConcurrentHashMap<>();
        
        // Ensure base directory exists
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create base directory: " + baseDir, e);
        }
    }
    
    @Override
    public IngestResponseProto append(IngestRequestProto request) {
        Objects.requireNonNull(request, "request cannot be null");
        EventProto event = request.getEvent();
        String eventId = event.getId().getValue();
        
        // Check for duplicate
        if (memoryCache.containsKey(eventId)) {
            log.debug("Duplicate event detected: {}", eventId);
            return IngestResponseProto.newBuilder()
                .setEvent(event)
                .setDuplicate(true)
                .build();
        }
        
        // Store in memory cache
        memoryCache.put(eventId, event);
        
        // Write to disk asynchronously
        try {
            Path eventFile = getEventFilePath(event);
            Files.createDirectories(eventFile.getParent());
            Files.write(eventFile, event.toByteArray());
            
            log.debug("Stored event: {} at {}", eventId, eventFile);
            
            return IngestResponseProto.newBuilder()
                .setEvent(event)
                .setDuplicate(false)
                .build();
                
        } catch (IOException e) {
            log.error("Failed to store event: {}", eventId, e);
            // Remove from cache on failure
            memoryCache.remove(eventId);
            
            return IngestResponseProto.newBuilder()
                .setEvent(event)
                .setDuplicate(false)
                .build();
        }
    }

    @Override
    public IngestBatchResponseProto appendBatch(IngestBatchRequestProto request) {
        Objects.requireNonNull(request, "request cannot be null");
        
        IngestBatchResponseProto.Builder builder = IngestBatchResponseProto.newBuilder();
        int successCount = 0;
        int duplicateCount = 0;
        
        for (EventProto event : request.getEventsList()) {
            try {
                IngestResponseProto response = append(IngestRequestProto.newBuilder()
                    .setEvent(event)
                    .build());
                
                builder.addEvents(event);
                
                if (response.getDuplicate()) {
                    duplicateCount++;
                } else {
                    successCount++;
                }
                
                // IngestResponseProto doesn't expose per-event errors. Errors are handled via exceptions above.
                
            } catch (Exception e) {
                log.error("Failed to process event in batch: {}", event.getId(), e);
                builder.addErrors(IngestErrorProto.newBuilder()
                    .setCode(IngestErrorCodeProto.INGEST_ERROR_UNSPECIFIED)
                    .setMessage("Failed to process event: " + e.getMessage())
                    .build());
            }
        }
        
        int errorCount = request.getEventsCount() - successCount - duplicateCount;
        
        return builder
            .setSuccessCount(successCount)
            .setDuplicateCount(duplicateCount)
            .setErrorCount(errorCount)
            .build();
    }

    @Override
    public GetEventResponseProto get(GetEventRequestProto request) {
        Objects.requireNonNull(request, "request cannot be null");
        String eventId = request.getEventId();
        
        // Check memory cache first
        EventProto cachedEvent = memoryCache.get(eventId);
        if (cachedEvent != null) {
            return GetEventResponseProto.newBuilder()
                .setEvent(cachedEvent)
                .build();
        }
        
        // If not in cache, try to load from disk
        try {
            Path eventFile = findEventFile(eventId);
            if (eventFile != null && Files.exists(eventFile)) {
                EventProto event = EventProto.parseFrom(Files.readAllBytes(eventFile));
                // Update cache
                memoryCache.put(eventId, event);
                
                return GetEventResponseProto.newBuilder()
                    .setEvent(event)
                    .build();
            }
            
            // Event not found
            // Not found: return empty response (no event)
            return GetEventResponseProto.newBuilder().build();
                
        } catch (Exception e) {
            log.error("Failed to retrieve event: {}", eventId, e);
            // Internal error: return empty response (consider surfacing via exception in future)
            return GetEventResponseProto.newBuilder().build();
        }
    }

    @Override
    public QueryEventsResponseProto query(QueryEventsRequestProto request) {
        Objects.requireNonNull(request, "request cannot be null");
        
        // For simplicity, we'll just return all events in memory that match the filter
        // A real implementation would use an index for efficient querying
        List<EventProto> results = memoryCache.values().stream()
            .filter(event -> {
                // Apply filters from request
                if (!request.getTypePrefix().isEmpty() && !event.getType().startsWith(request.getTypePrefix())) {
                    return false;
                }
                if (!request.getTenantId().isEmpty() && !event.getTenantId().equals(request.getTenantId())) {
                    return false;
                }
                // Add more filters as needed
                return true;
            })
            .collect(Collectors.toList());
        
        log.debug("Query returned {} events matching criteria", results.size());
        
        return QueryEventsResponseProto.newBuilder()
            .addAllEvents(results)
            .setTotalCount(results.size())
            .build();
    }

    // Helper methods
    
    /**
     * Gets the file path for storing an event.
     * The path is structured as: {baseDir}/{tenant_id}/{yyyy-MM-dd}/{event_id}.event
     */
    private Path getEventFilePath(EventProto event) {
        String dateStr = Instant.ofEpochSecond(event.getDetectedAt().getSeconds())
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toString();
            
        return baseDir.resolve(event.getTenantId())
            .resolve(dateStr)
            .resolve(event.getId() + EVENT_FILE_EXTENSION);
    }
    
    /**
     * Finds the file containing the event with the given ID.
     * This is inefficient for large numbers of events and should be optimized with an index.
     */
    private Path findEventFile(String eventId) throws IOException {
        // In a real implementation, we'd use an index to find the file
        // For now, we'll do a simple directory walk
        final Path[] found = { null };
        
        Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals(eventId + EVENT_FILE_EXTENSION)) {
                    found[0] = file;
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        return found[0];
    }
    
    /**
     * Purges events older than the specified retention policy.
     * 
     * @param policy The retention policy specifying how long to keep events
     */
    public void purgeOlderThan(RetentionPolicy policy) {
        if (baseDir == null || policy == null || policy.maxAge().isZero()) {
            log.debug("purgeOlderThan: nothing to do (baseDir={}, policy={})", baseDir, policy);
            return;
        }
        
        Instant threshold = Instant.now().minus(policy.maxAge());
        List<Path> candidates = new ArrayList<>();
        try {
            if (Files.isDirectory(baseDir)) {
                Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.lastModifiedTime().toInstant().isBefore(threshold)) {
                            candidates.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            log.warn("purgeOlderThan scan failed: {}", e.toString());
        }
        if (!candidates.isEmpty()) {
            log.info("purgeOlderThan would remove {} files older than {}. Example: {}", candidates.size(), threshold, candidates.get(0));
        } else {
            log.debug("purgeOlderThan: no candidates found before {}", threshold);
        }
    }

    public void purgeOverSize(RetentionPolicy policy) {
        if (baseDir == null || policy == null || policy.maxBytes() <= 0) {
            log.debug("purgeOverSize: nothing to do (baseDir={}, policy={})", baseDir, policy);
            return;
        }
        try {
            if (!Files.isDirectory(baseDir)) {
                return;
            }
            List<Path> files = new ArrayList<>();
            Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    files.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
            long total = 0L;
            for (Path f : files) {
                total += safeSize(f);
            }
            if (total <= policy.maxBytes()) {
                log.debug("purgeOverSize: total={} <= maxBytes={}, nothing to do", total, policy.maxBytes());
                return;
            }
            files.sort(Comparator.comparingLong(p -> lastModified(p)));
            long target = total - policy.maxBytes();
            long reclaimed = 0L;
            List<Path> toRemove = new ArrayList<>();
            for (Path f : files) {
                long s = safeSize(f);
                toRemove.add(f);
                reclaimed += s;
                if (reclaimed >= target) {
                    break;
                }
            }
            if (!toRemove.isEmpty()) {
                log.info("purgeOverSize would remove {} oldest files to reclaim ~{} bytes. Example: {}", toRemove.size(), reclaimed, toRemove.get(0));
            }
        } catch (IOException e) {
            log.warn("purgeOverSize scan failed: {}", e.toString());
        }
    }

    private static long safeSize(Path f) {
        try {
            return Files.size(f);
        } catch (IOException e) {
            return 0L;
        }
    }
    private static long lastModified(Path f) {
        try {
            return Files.getLastModifiedTime(f).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }
}
