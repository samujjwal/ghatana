package com.ghatana.refactorer.consistency;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Thread-safe ledger for tracking all actions taken during a session.
 * Supports querying actions by file and detecting regressions.
 
 * @doc.type class
 * @doc.purpose Handles action ledger operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class ActionLedger {
    private static final Logger logger = LogManager.getLogger(ActionLedger.class);
    private final Map<String, List<ActionRecord>> actionsByFile = new ConcurrentHashMap<>();
    private static final int DEFAULT_TTL_HOURS = 24;

    /**
     * Records an action in the ledger.
     *
     * @param file    The file path where the action was taken
     * @param rule    The rule ID that triggered the action
     * @param context Additional context about the action
     * @param success Whether the action was successful
     * @return The created ActionRecord
     */
    public ActionRecord logAction(Path file, String rule, String context, boolean success) {
        ActionRecord record = new ActionRecord(
            Instant.now(),
            file.toAbsolutePath().toString(),
            rule,
            context,
            success
        );
        
        actionsByFile.computeIfAbsent(
            file.toString(), 
            k -> new CopyOnWriteArrayList<>()
        ).add(record);
        
        return record;
    }

    /**
     * Gets all actions for a specific file.
     *
     * @param filePath The file path to query
     * @return List of actions for the file, or empty list if none
     */
    public List<ActionRecord> getActionsForFile(Path filePath) {
        return actionsByFile.getOrDefault(filePath.toString(), List.of());
    }

    /**
     * Checks if a file has regressed for a specific rule.
     *
     * @param filePath The file path to check
     * @param rule     The rule ID to check
     * @return true if the file has regressed for the given rule
     */
    public boolean hasRegressed(Path filePath, String rule) {
        return getActionsForFile(filePath).stream()
                .anyMatch(record -> rule.equals(record.rule()) && !record.success());
    }

    /**
     * Cleans up old records based on TTL.
     * 
     * @param ttlHours Records older than this many hours will be removed
     */
    public void cleanupOldRecords(int ttlHours) {
        Instant cutoff = Instant.now().minusSeconds(ttlHours * 3600L);
        
        actionsByFile.forEach(
                (file, records) -> {
                    List<ActionRecord> filtered =
                            records.stream()
                                    .filter(record -> record.timestamp().isAfter(cutoff))
                                    .collect(Collectors.toList());

                    if (filtered.isEmpty()) {
                        actionsByFile.remove(file);
                    } else {
                        actionsByFile.put(file, new CopyOnWriteArrayList<>(filtered));
                    }
                });
    }

    /**
     * Cleans up old records using the default TTL.
     */
    public void cleanupOldRecords() {
        cleanupOldRecords(DEFAULT_TTL_HOURS);
    }

    /**
     * Immutable record of an action taken during a session.
     */
    public record ActionRecord(
        Instant timestamp,
        String filePath,
        String rule,
        String context,
        boolean success
    ) {
        @Override
        public String toString() {
            return String.format(
                "ActionRecord[%s, %s, %s, success=%b]",
                timestamp,
                filePath,
                rule,
                success
            );
        }
    }
}
