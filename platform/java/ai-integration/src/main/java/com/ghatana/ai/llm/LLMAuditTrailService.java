/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.ai.llm;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for logging full audit trail of LLM calls.
 * Records prompts, responses, decisions, and metadata for compliance and debugging.
 *
 * @doc.type class
 * @doc.purpose Full audit trail for LLM calls with prompts, responses, and decisions
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public final class LLMAuditTrailService {

    private final Map<String, List<AuditEntry>> auditLog = new ConcurrentHashMap<>();
    private final int maxEntriesPerTenant;
    private final boolean logFullContent;

    /**
     * Creates an LLM audit trail service with default settings.
     */
    public LLMAuditTrailService() {
        this(10000, false);
    }

    /**
     * Creates an LLM audit trail service with custom settings.
     *
     * @param maxEntriesPerTenant maximum audit entries to retain per tenant
     * @param logFullContent whether to log full prompt/response content (security consideration)
     */
    public LLMAuditTrailService(int maxEntriesPerTenant, boolean logFullContent) {
        this.maxEntriesPerTenant = maxEntriesPerTenant;
        this.logFullContent = logFullContent;
    }

    /**
     * Logs an LLM call to the audit trail.
     *
     * @param tenantId tenant identifier
     * @param requestId unique request identifier
     * @param provider LLM provider name
     * @param model model name
     * @param prompt full prompt text (or hash if logFullContent is false)
     * @param response full response text (or hash if logFullContent is false)
     * @param promptTokens prompt token count
     * @param completionTokens completion token count
     * @param latencyMs request latency in milliseconds
     * @param metadata additional metadata
     */
    public void logCall(String tenantId, String requestId, String provider, String model,
                       String prompt, String response, int promptTokens, int completionTokens,
                       long latencyMs, Map<String, Object> metadata) {
        AuditEntry entry = new AuditEntry(
            requestId,
            tenantId,
            provider,
            model,
            logFullContent ? prompt : hashContent(prompt),
            logFullContent ? response : hashContent(response),
            promptTokens,
            completionTokens,
            latencyMs,
            Instant.now(),
            metadata != null ? Map.copyOf(metadata) : Map.of()
        );

        auditLog.computeIfAbsent(tenantId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(entry);

        // Prune if over limit
        List<AuditEntry> entries = auditLog.get(tenantId);
        if (entries.size() > maxEntriesPerTenant) {
            entries.remove(0);
        }
    }

    /**
     * Gets audit entries for a tenant.
     *
     * @param tenantId tenant identifier
     * @return list of audit entries for the tenant
     */
    public List<AuditEntry> getAuditEntries(String tenantId) {
        return List.copyOf(auditLog.getOrDefault(tenantId, List.of()));
    }

    /**
     * Gets audit entries for a tenant within a time range.
     *
     * @param tenantId tenant identifier
     * @param from start of time range
     * @param to end of time range
     * @return list of audit entries within the time range
     */
    public List<AuditEntry> getAuditEntries(String tenantId, Instant from, Instant to) {
        return auditLog.getOrDefault(tenantId, List.of()).stream()
            .filter(entry -> !entry.timestamp().isBefore(from) && !entry.timestamp().isAfter(to))
            .toList();
    }

    /**
     * Gets audit entries for a specific request.
     *
     * @param tenantId tenant identifier
     * @param requestId request identifier
     * @return list of audit entries for the request
     */
    public List<AuditEntry> getAuditEntriesForRequest(String tenantId, String requestId) {
        return auditLog.getOrDefault(tenantId, List.of()).stream()
            .filter(entry -> entry.requestId().equals(requestId))
            .toList();
    }

    /**
     * Clears audit entries for a tenant.
     *
     * @param tenantId tenant identifier
     */
    public void clearAuditEntries(String tenantId) {
        auditLog.remove(tenantId);
    }

    /**
     * Clears all audit entries.
     */
    public void clearAllAuditEntries() {
        auditLog.clear();
    }

    /**
     * Gets the count of audit entries for a tenant.
     *
     * @param tenantId tenant identifier
     * @return count of audit entries
     */
    public int getAuditEntryCount(String tenantId) {
        return auditLog.getOrDefault(tenantId, List.of()).size();
    }

    /**
     * Checks if full content logging is enabled.
     *
     * @return true if full content is logged
     */
    public boolean isLogFullContent() {
        return logFullContent;
    }

    /**
     * Generates a hash of content for audit logging when full content is disabled.
     *
     * @param content the content to hash
     * @return hash of the content
     */
    private String hashContent(String content) {
        if (content == null || content.isEmpty()) {
            return "[empty]";
        }
        // Simple hash for demonstration - in production, use a proper cryptographic hash
        return String.valueOf(content.hashCode());
    }

    /**
     * Audit entry record.
     *
     * @param requestId unique request identifier
     * @param tenantId tenant identifier
     * @param provider LLM provider
     * @param model model name
     * @param prompt prompt text (or hash)
     * @param response response text (or hash)
     * @param promptTokens prompt token count
     * @param completionTokens completion token count
     * @param latencyMs request latency
     * @param timestamp when the call occurred
     * @param metadata additional metadata
     */
    public record AuditEntry(
        String requestId,
        String tenantId,
        String provider,
        String model,
        String prompt,
        String response,
        int promptTokens,
        int completionTokens,
        long latencyMs,
        Instant timestamp,
        Map<String, Object> metadata
    ) {}
}
