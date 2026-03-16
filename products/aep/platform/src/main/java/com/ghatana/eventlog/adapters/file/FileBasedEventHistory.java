package com.ghatana.eventlog.adapters.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.aep.integration.events.EventLogAdapter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-backed implementation of {@link EventLogAdapter}.
 *
 * <p>Events are persisted as newline-delimited JSON (NDJSON) to a single file
 * on the local filesystem. Each line is an independent, self-contained JSON
 * object representing one {@link EventLogAdapter.EventRecord}. This format
 * offers:
 * <ul>
 *   <li><b>Durability</b> – events survive process restarts</li>
 *   <li><b>Simplicity</b> – no external infrastructure required</li>
 *   <li><b>Auditability</b> – plain-text file is human-readable</li>
 * </ul>
 *
 * <p><b>Thread safety:</b> Reads and writes are protected by a
 * {@link ReentrantReadWriteLock}. Multiple concurrent readers are allowed;
 * writes are exclusive. This makes the adapter safe for use inside a single
 * JVM but not for concurrent writes from multiple processes.
 *
 * <p><b>Limitations:</b>
 * <ul>
 *   <li>Linear scan for reads – suitable for low-to-medium volume; use a
 *       proper event store for high-throughput scenarios.</li>
 *   <li>No compaction – the file grows indefinitely; callers are responsible
 *       for archival/rotation.</li>
 *   <li>Single-process concurrency only – no inter-process locking.</li>
 * </ul>
 *
 * <p><b>File format:</b> Each line is a JSON object with the fields defined
 * by {@link EventLogAdapter.EventRecord}. Lines are written atomically using
 * {@link StandardOpenOption#APPEND}.
 *
 * @doc.type class
 * @doc.purpose File-backed durable implementation of EventLogAdapter using NDJSON
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class FileBasedEventHistory implements EventLogAdapter {

    private static final ObjectMapper MAPPER = buildObjectMapper();

    private final Path logFile;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a {@code FileBasedEventHistory} writing to the given file path.
     *
     * <p>Parent directories are created if they do not already exist. If the
     * file already exists its contents are preserved (append mode).
     *
     * @param logFile path to the NDJSON event log file
     * @throws UncheckedIOException if the parent directories cannot be created
     */
    public FileBasedEventHistory(Path logFile) {
        this.logFile = logFile;
        try {
            Files.createDirectories(logFile.getParent() == null ? Path.of(".") : logFile.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create parent directories for event log: " + logFile, e);
        }
    }

    /**
     * Appends an event record to the file log.
     *
     * <p>If the supplied record has a {@code null} event ID, a random UUID is
     * assigned. The timestamp is defaulted to {@link Instant#now()} when
     * absent. The record is serialised to a single JSON line and appended to
     * the log file with a trailing newline.
     *
     * @param record the event to persist; must not be {@code null}
     * @return the event ID that was persisted (generated if not supplied)
     * @throws UncheckedIOException if writing to the file fails
     */
    @Override
    public String append(EventRecord record) {
        String eventId = record.eventId() != null ? record.eventId() : UUID.randomUUID().toString();
        Instant timestamp = record.timestamp() != null ? record.timestamp() : Instant.now();

        EventRecord stored = new EventRecord(
                eventId,
                record.tenantId(),
                record.eventType(),
                record.aggregateId(),
                record.payload(),
                record.eventClassName(),
                timestamp
        );

        lock.writeLock().lock();
        try (BufferedWriter writer = Files.newBufferedWriter(
                logFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            writer.write(MAPPER.writeValueAsString(stored));
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append event to file log: " + logFile, e);
        } finally {
            lock.writeLock().unlock();
        }

        return eventId;
    }

    /**
     * Queries events from the file log.
     *
     * <p>The file is scanned linearly from the beginning. If {@code afterEventId}
     * is specified all records up to and including the matching event are skipped;
     * subsequent events matching any of the supplied {@code eventTypes} are
     * returned up to {@code limit}.
     *
     * @param eventTypes   the event types to include; must not be {@code null}
     * @param afterEventId optional cursor – only events <em>after</em> this ID
     *                     are returned; pass {@code null} to read from the start
     * @param limit        maximum number of records to return; must be &gt; 0
     * @return ordered list of matching {@link EventRecord}s (oldest first)
     * @throws UncheckedIOException if reading from the file fails
     */
    @Override
    public List<EventRecord> query(String[] eventTypes, String afterEventId, int limit) {
        if (eventTypes == null || eventTypes.length == 0 || limit <= 0) {
            return List.of();
        }

        List<String> targetTypes = Arrays.asList(eventTypes);
        List<EventRecord> result = new ArrayList<>();

        lock.readLock().lock();
        try {
            if (!Files.exists(logFile)) {
                return List.of();
            }

            boolean cursorPassed = (afterEventId == null);

            try (BufferedReader reader = Files.newBufferedReader(logFile)) {
                String line;
                while ((line = reader.readLine()) != null && result.size() < limit) {
                    if (line.isBlank()) {
                        continue;
                    }
                    EventRecord record = MAPPER.readValue(line, EventRecord.class);
                    if (!cursorPassed) {
                        if (record.eventId().equals(afterEventId)) {
                            cursorPassed = true;
                        }
                        continue;
                    }
                    if (targetTypes.contains(record.eventType())) {
                        result.add(record);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read event log: " + logFile, e);
            }
        } finally {
            lock.readLock().unlock();
        }

        return result;
    }

    /**
     * Returns the path of the underlying event log file.
     *
     * @return absolute or relative path to the log file
     */
    public Path getLogFile() {
        return logFile;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static ObjectMapper buildObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
