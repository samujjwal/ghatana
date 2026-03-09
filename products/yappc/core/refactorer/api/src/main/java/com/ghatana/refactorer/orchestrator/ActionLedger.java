/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Logging handled via System.out/err

/**
 * Thread-safe append-only ledger for tracking actions taken by the polyfix tool. Each action is
 * recorded as a JSON object in a new line.
 
 * @doc.type class
 * @doc.purpose Handles action ledger operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class ActionLedger {

    private static final Logger log = LoggerFactory.getLogger(ActionLedger.class);
    private final Path path;
    private final Lock writeLock = new ReentrantLock();

    /**
     * Creates a new ActionLedger that writes to the specified file. Parent directories will be
     * created if they don't exist.
     *
     * @param path Path to the ledger file
     * @throws IllegalArgumentException if path is null
     */
    public ActionLedger(Path path) {
        this.path = Objects.requireNonNull(path, "Ledger path cannot be null");
    }

    /**
     * Initializes the ledger by ensuring the parent directory exists. This method should be called
     * before any write operations.
     *
     * @throws IOException if the parent directory cannot be created
     */
    public void initialize() throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Appends a new action to the ledger in a thread-safe manner.
     *
     * @param repo The repository identifier
     * @param file The file being modified
     * @param ruleId The ID of the rule being applied
     * @param diffHash Hash of the diff being applied
     * @param outcome The outcome of the action (e.g., "applied", "skipped", "failed")
     * @throws IllegalStateException if the action cannot be recorded
     */
    public void append(String repo, String file, String ruleId, String diffHash, String outcome) {
        Objects.requireNonNull(repo, "Repository cannot be null");
        Objects.requireNonNull(file, "File cannot be null");
        Objects.requireNonNull(ruleId, "Rule ID cannot be null");
        Objects.requireNonNull(diffHash, "Diff hash cannot be null");
        Objects.requireNonNull(outcome, "Outcome cannot be null");

        String line =
                String.format(
                        "{\"ts\":\"%s\",\"repo\":\"%s\",\"file\":\"%s\",\"ruleId\":\"%s\",\"diffHash\":\"%s\",\"outcome\":\"%s\"}%n",
                        Instant.now().toString(),
                        escape(repo),
                        escape(file),
                        escape(ruleId),
                        escape(diffHash),
                        escape(outcome));

        writeLock.lock();
        try {
            ensureParentDirectoryExists();
            Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Recorded action in ledger: {}", line.trim());
        } catch (IOException e) {
            String errorMsg = "Failed to write to ledger file: " + path;
            log.error("{}", errorMsg);
            e.printStackTrace();
            throw new IllegalStateException(errorMsg, e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Ensures that the parent directory of the ledger file exists.
     *
     * @throws IllegalStateException if the directory cannot be created
     */
    private void ensureParentDirectoryExists() {
        Path parent = path.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                String errorMsg =
                        String.format(
                                "Failed to create ledger directory %s: %s", parent, e.getMessage());
                log.error("{}", errorMsg);
                e.printStackTrace();
                throw new IllegalStateException(errorMsg, e);
            }
        }
    }

    /**
     * Escapes special characters in a string for JSON output.
     *
     * @param s The string to escape
     * @return The escaped string
     */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
