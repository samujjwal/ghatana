package com.ghatana.platform.domain.domain.audit;

import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;

/**
 * {@code AuditEntry} represents an immutable audit log entry capturing a single discrete
 * auditable action within the system for compliance, debugging, and forensics.
 *
 * <h2>Purpose</h2>
 * Records security-relevant actions with:
 * <ul>
 *   <li>Action identification and categorization (verb: what happened)</li>
 *   <li>Resource identity and type tracking (object: what was affected)</li>
 *   <li>Actor/principal identification (subject: who did it)</li>
 *   <li>Temporal ordering (timestamp in milliseconds for chronological analysis)</li>
 *   <li>Optional contextual notes (why: human-readable explanation)</li>
 * </ul>
 * Forms the atomic building block of {@link AuditTrail} and compliance records.
 *
 * <h2>Domain Model</h2>
 * Represents the fundamental audit triple: (actor, action, resource, when, why)
 * <ul>
 *   <li><b>Action</b>: What happened (verb)</li>
 *   <li><b>Resource</b>: What was affected (object)</li>
 *   <li><b>Actor</b>: Who did it (subject)</li>
 *   <li><b>Timestamp</b>: When it happened (time)</li>
 *   <li><b>Notes</b>: Why it happened (rationale)</li>
 * </ul>
 *
 * <h2>Structure</h2>
 * Immutable composition ensures audit integrity:
 * <ul>
 *   <li><b>action</b>: Action verb (required, e.g., "CREATE", "UPDATE", "DELETE", "LOGIN", "ACCESS")</li>
 *   <li><b>resourceId</b>: Unique resource identifier (required, e.g., "user-123", "doc-456")</li>
 *   <li><b>resourceType</b>: Resource type classification (required, e.g., "USER", "DOCUMENT", "CONFIG")</li>
 *   <li><b>performedBy</b>: Principal/actor identity (required, e.g., "admin@company.com", "system")</li>
 *   <li><b>timestamp</b>: Milliseconds since Unix epoch (for chronological ordering)</li>
 *   <li><b>notes</b>: Optional contextual information (e.g., "Password reset after security breach")</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Created by</b>: Application services, interceptors, security handlers</li>
 *   <li><b>Used by</b>: Audit trails, compliance reports, forensics analysis, security dashboards</li>
 *   <li><b>Stored in</b>: {@link AuditTrail} collections, audit log databases, compliance archives</li>
 *   <li><b>Related to</b>: {@link AuditTrail} (collection), {@link AuditEvent} (enriched form)</li>
 * </ul>
 *
 * <h2>Immutability Guarantees</h2>
 * Leverages Lombok {@code @Value} for compile-time immutability:
 * <ul>
 *   <li>All fields final (no setters generated)</li>
 *   <li>Implements {@link Comparable&lt;AuditEntry&gt;} for natural ordering by timestamp</li>
 *   <li>Thread-safe for concurrent access (no synchronization needed)</li>
 *   <li>Safe JSON serialization via {@code @Jacksonized}</li>
 *   <li>Safe for event sourcing and replay (immutable snapshots)</li>
 * </ul>
 *
 * <h2>Natural Ordering</h2>
 * Implements Comparable with chronological ordering by timestamp:
 * <ul>
 *   <li>Earlier entries compare as less-than later entries</li>
 *   <li>Entries with same timestamp maintain insertion order</li>
 *   <li>Enables natural sorting: {@code Collections.sort(entries)}</li>
 *   <li>Used for timeline generation, compliance gap analysis</li>
 * </ul>
 *
 * <h2>Builder Pattern</h2>
 * Supports fluent construction with Lombok {@code @Builder}:
 * {@code
 *   AuditEntry entry = AuditEntry.builder()
 *       .action("UPDATE")
 *       .resourceType("USER")
 *       .resourceId("user-123")
 *       .performedBy("admin@company.com")
 *       .timestamp(System.currentTimeMillis())
 *       .notes("Security role changed from User to Admin")
 *       .build();
 * }
 *
 * <h2>Functional Modification via Immutable Copy</h2>
 * Supports functional-style immutable updates:
 * {@code
 *   AuditEntry original = AuditEntry.builder()
 *       .action("CREATE").resourceType("USER").resourceId("u1")
 *       .performedBy("sys").timestamp(now).build();
 *
 *   // Create modified copy without affecting original
 *   AuditEntry updated = original.withNotes("Additional context added");
 *   // original.notes still null, updated.notes has new value
 * }
 *
 * <h2>Temporal Chronology</h2>
 * Entries maintain complete history via timestamps:
 * {@code
 *   // Sort actions chronologically for compliance audit trail
 *   List&lt;AuditEntry&gt; timeline = entries.stream()
 *       .sorted()  // Uses compareTo() for natural ordering
 *       .collect(Collectors.toList());
 *
 *   // Find audit events in time window
 *   long startMs = System.currentTimeMillis() - (30 * 86400000); // 30 days ago
 *   List&lt;AuditEntry&gt; recent = entries.stream()
 *       .filter(e -> e.getTimestamp() >= startMs)
 *       .sorted()
 *       .collect(Collectors.toList());
 * }
 *
 * <h2>Action Categories</h2>
 * Common action types supported:
 * <ul>
 *   <li><b>Lifecycle</b>: CREATE, UPDATE, DELETE, ARCHIVE, RESTORE</li>
 *   <li><b>Access Control</b>: LOGIN, LOGOUT, PERMISSION_GRANT, PERMISSION_REVOKE</li>
 *   <li><b>Data Access</b>: ACCESS, DOWNLOAD, EXPORT, SHARE</li>
 *   <li><b>Configuration</b>: CONFIG_CHANGE, POLICY_UPDATE, SETTINGS_CHANGE</li>
 *   <li><b>Security</b>: FAILED_LOGIN, BRUTE_FORCE_ALERT, PASSWORD_CHANGE</li>
 * </ul>
 *
 * <h2>Resource Types</h2>
 * Examples of supported resource types:
 * <ul>
 *   <li>USER, ROLE, GROUP, TEAM - Identity resources</li>
 *   <li>DOCUMENT, FILE, DATABASE_RECORD - Data resources</li>
 *   <li>API_KEY, TOKEN, CERTIFICATE - Security resources</li>
 *   <li>POLICY, CONFIGURATION, SETTING - System resources</li>
 *   <li>PIPELINE, STAGE, AGENT - Process resources</li>
 * </ul>
 *
 * <h2>Principal Identification</h2>
 * The performedBy field supports various identity formats:
 * <ul>
 *   <li>Email: "user@company.com" (user principals)</li>
 *   <li>Service Account: "service-account@internal" (automated actions)</li>
 *   <li>System: "SYSTEM" (internal operations)</li>
 *   <li>API Client: "client-123" (external integrations)</li>
 * </ul>
 *
 * <h2>Typical Usage Patterns</h2>
 * <ul>
 *   <li><b>Security Logging</b>: Track login, logout, password changes, role changes</li>
 *   <li><b>Data Access Audit</b>: Who accessed what data and when</li>
 *   <li><b>Change Tracking</b>: Document creation, update, deletion history</li>
 *   <li><b>Compliance Reporting</b>: Generate audit trails for regulatory requirements (SOX, HIPAA, GDPR)</li>
 *   <li><b>Forensic Investigation</b>: Reconstruct actions during security incidents</li>
 *   <li><b>SLA Monitoring</b>: Track response times for change requests</li>
 * </ul>
 *
 * <h2>Serialization</h2>
 * Supports JSON serialization via Jackson:
 * {@code
 *   ObjectMapper mapper = new ObjectMapper();
 *   String json = mapper.writeValueAsString(entry);
 *   AuditEntry deserialized = mapper.readValue(json, AuditEntry.class);
 * }
 * The {@code @Jacksonized} annotation enables Jackson deserialization via builder.
 *
 * @see AuditTrail
 * @see AuditEvent
 * @see Comparable
 * @since 1.0.0
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose single atomic audit log entry with immutable snapshot semantics
 * @doc.pattern value-object, immutable, comparable, builder
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class AuditEntry implements Serializable, Comparable<AuditEntry> {
    @lombok.NonNull String action;
    @lombok.NonNull String resourceId;
    @lombok.NonNull String resourceType;
    @lombok.NonNull String performedBy;
    long timestamp;
    @Builder.Default String notes = null;

    @Override
    public int compareTo(AuditEntry other) {
        return Long.compare(this.timestamp, other.timestamp);
    }
    
    /**
     * Creates a new instance of AuditEntry with the specified notes.
     *
     * @param notes the new notes value
     * @return a new AuditEntry instance with the updated notes
     */
    public AuditEntry withNotes(String notes) {
        return AuditEntry.builder()
            .action(this.action)
            .resourceType(this.resourceType)
            .resourceId(this.resourceId)
            .performedBy(this.performedBy)
            .timestamp(this.timestamp)
            .notes(notes)
            .build();
    }
}
