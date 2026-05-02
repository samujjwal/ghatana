package com.ghatana.plugin.audit.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Standard implementation of AuditTrailPlugin.
 *
 * <p>This implementation provides:</p>
 * <ul>
 *   <li>Hash chain verification for tamper-evidence</li>
 *   <li>SHA-256 cryptographic hashing</li>
 *   <li>Export to multiple formats (JSON, CSV, XML)</li>
 *   <li>Configurable retention for regulatory compliance via product-supplied rule packs</li>
 * </ul>
 *
 * <p>For production use, this should be backed by an immutable database like DataCloud.</p>
 *
 * @doc.type class
 * @doc.purpose Standard audit trail implementation
 * @doc.layer platform
 * @doc.pattern Plugin Implementation
 * @since 1.0.0
 */
public class StandardAuditTrailPlugin implements AuditTrailPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(StandardAuditTrailPlugin.class);
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final Map<String, List<AuditEntry>> trails = new ConcurrentHashMap<>();
    private final Map<String, String> lastHashes = new ConcurrentHashMap<>();

    private PluginContext context;
    private PluginState state = PluginState.UNLOADED;
    private MessageDigest digest;

    public StandardAuditTrailPlugin() {
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public PluginMetadata metadata() {
        return PluginMetadata.builder()
            .id("audit-trail-plugin")
            .name("Audit Trail Plugin")
            .version("1.0.0")
            .description("Cross-product audit trail framework")
            .type(PluginType.CUSTOM)
            .author("Ghatana")
            .license("Apache-2.0")
            .capability("audit:log", "audit:verify", "audit:export")
            .properties(Map.of(
                "variant", "standard-in-memory",
                "durability", "non-durable",
                "supportedExportFormats", List.of("JSON", "CSV", "XML"),
                "unsupportedExportFormats", List.of("PDF"),
                "unsupportedExportReason", "PDF export is not implemented in standard variant"
            ))
            .build();
    }

    @Override
    public PluginState getState() {
        return state;
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        this.context = context;
        this.state = PluginState.INITIALIZED;
        LOG.info("AuditTrailPlugin initialized");
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        this.state = PluginState.STARTED;
        LOG.info("AuditTrailPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        this.state = PluginState.STOPPED;
        LOG.info("AuditTrailPlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        trails.clear();
        lastHashes.clear();
        this.state = PluginState.UNLOADED;
        LOG.info("AuditTrailPlugin shutdown");
        return Promise.complete();
    }

    @Override
    public Promise<AuditEntry> logEvent(String entityId, String action, Map<String, Object> details) {
        String entryId = UUID.randomUUID().toString();
        String actorId = details.containsKey("actorId") ? (String) details.get("actorId") : "system";
        Instant now = Instant.now();

        // Get previous hash for chain
        String previousHash = lastHashes.getOrDefault(entityId, "0");

        // Calculate hash for this entry
        String data = entryId + entityId + action + actorId + now.toString() + previousHash;
        String hash = calculateHash(data);

        AuditEntry entry = new AuditEntry(
            entryId,
            entityId,
            action,
            details,
            actorId,
            hash,
            previousHash,
            now
        );

        trails.computeIfAbsent(entityId, k -> new ArrayList<>()).add(entry);
        lastHashes.put(entityId, hash);

        LOG.debug("Audit event logged: {} for entity {} by actor {}",
            action, entityId, actorId);

        return Promise.of(entry);
    }

    @Override
    public Promise<List<AuditEntry>> getTrail(String entityId) {
        List<AuditEntry> trail = trails.getOrDefault(entityId, Collections.emptyList());
        return Promise.of(new ArrayList<>(trail));
    }

    @Override
    public Promise<VerificationResult> verifyIntegrity(String entityId) {
        List<AuditEntry> trail = trails.getOrDefault(entityId, Collections.emptyList());

        if (trail.isEmpty()) {
            return Promise.of(new VerificationResult(
                entityId, true, 0, Collections.emptyList(), Instant.now()
            ));
        }

        List<String> violations = new ArrayList<>();
        boolean valid = true;

        String expectedPreviousHash = "0";

        for (AuditEntry entry : trail) {
            // Verify chain link
            if (!entry.previousHash().equals(expectedPreviousHash)) {
                violations.add("Chain broken at entry " + entry.entryId() +
                    ": expected " + expectedPreviousHash + " but found " + entry.previousHash());
                valid = false;
            }

            // Verify entry hash
            String data = entry.entryId() + entry.entityId() + entry.action() +
                entry.actorId() + entry.timestamp().toString() + entry.previousHash();
            String calculatedHash = calculateHash(data);

            if (!calculatedHash.equals(entry.hash())) {
                violations.add("Hash mismatch at entry " + entry.entryId());
                valid = false;
            }

            expectedPreviousHash = entry.hash();
        }

        VerificationResult result = new VerificationResult(
            entityId,
            valid,
            trail.size(),
            violations,
            Instant.now()
        );

        LOG.info("Audit trail verification for {}: valid={}, entries={}, violations={}",
            entityId, valid, trail.size(), violations.size());

        return Promise.of(result);
    }

    @Override
    public Promise<Void> exportTrail(String entityId, ExportFormat format, OutputStream out) {
        List<AuditEntry> trail = trails.getOrDefault(entityId, Collections.emptyList());

        try {
            switch (format) {
                case JSON -> exportJson(trail, out);
                case CSV -> exportCsv(trail, out);
                case XML -> exportXml(trail, out);
                case PDF -> throw new UnsupportedOperationException("PDF export not yet implemented");
            }

            LOG.info("Exported audit trail for {} in {} format ({} entries)",
                entityId, format, trail.size());

            return Promise.complete();
        } catch (IOException e) {
            return Promise.ofException(e);
        } catch (UnsupportedOperationException e) {
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Integer> purgeEntriesOlderThan(long cutoffEpochMs) {
        int totalDeleted = 0;
        for (Map.Entry<String, List<AuditEntry>> trailEntry : trails.entrySet()) {
            List<AuditEntry> entries = trailEntry.getValue();
            int before = entries.size();
            entries.removeIf(e -> e.timestamp().toEpochMilli() < cutoffEpochMs);
            totalDeleted += before - entries.size();
        }
        LOG.info("purgeEntriesOlderThan({}): deleted {} audit entry/entries", cutoffEpochMs, totalDeleted);
        return Promise.of(totalDeleted);
    }

    private void exportJson(List<AuditEntry> trail, OutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.println("[");

        for (int i = 0; i < trail.size(); i++) {
            AuditEntry entry = trail.get(i);
            writer.printf("  {%n");
            writer.printf("    \"entryId\": \"%s\",%n", entry.entryId());
            writer.printf("    \"entityId\": \"%s\",%n", entry.entityId());
            writer.printf("    \"action\": \"%s\",%n", entry.action());
            writer.printf("    \"actorId\": \"%s\",%n", entry.actorId());
            writer.printf("    \"timestamp\": \"%s\",%n", ISO_FORMAT.format(entry.timestamp()));
            writer.printf("    \"hash\": \"%s\",%n", entry.hash());
            writer.printf("    \"previousHash\": \"%s\"%n", entry.previousHash());
            writer.printf("  }%s%n", i < trail.size() - 1 ? "," : "");
        }

        writer.println("]");
        writer.flush();
    }

    private void exportCsv(List<AuditEntry> trail, OutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.println("entryId,entityId,action,actorId,timestamp,hash,previousHash");

        for (AuditEntry entry : trail) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
                entry.entryId(),
                entry.entityId(),
                entry.action(),
                entry.actorId(),
                ISO_FORMAT.format(entry.timestamp()),
                entry.hash(),
                entry.previousHash()
            );
        }

        writer.flush();
    }

    private void exportXml(List<AuditEntry> trail, OutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.println("<auditTrail>");

        for (AuditEntry entry : trail) {
            writer.printf("  <entry id=\"%s\">%n", entry.entryId());
            writer.printf("    <entityId>%s</entityId>%n", escapeXml(entry.entityId()));
            writer.printf("    <action>%s</action>%n", escapeXml(entry.action()));
            writer.printf("    <actorId>%s</actorId>%n", escapeXml(entry.actorId()));
            writer.printf("    <timestamp>%s</timestamp>%n", ISO_FORMAT.format(entry.timestamp()));
            writer.printf("    <hash>%s</hash>%n", entry.hash());
            writer.printf("    <previousHash>%s</previousHash>%n", entry.previousHash());
            writer.println("  </entry>");
        }

        writer.println("</auditTrail>");
        writer.flush();
    }

    private String calculateHash(String data) {
        byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String formatDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        return details.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", ", "{", "}"));
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    @Override
    public String toString() {
        return "StandardAuditTrailPlugin{trails=" + trails.size() +
               ", totalEntries=" + trails.values().stream().mapToInt(List::size).sum() + "}";
    }
}
