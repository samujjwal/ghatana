/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.memory;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.security.MemoryRedactionFilter;
import com.ghatana.agent.memory.security.MemorySecurityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for YAPPC memory governance: redaction and tenant isolation (plan section 9.2).
 *
 * <p>Covers:
 * <ul>
 *   <li>9.2.4 — Store episode with email field → retrieve → email redacted by filter</li>
 *   <li>9.2.2 — Cross-tenant read and write rejected by TenantIsolatedMemorySecurityManager</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tests memory redaction and tenant isolation (9.2)
 * @doc.layer product
 * @doc.pattern Test
 * @doc.gaa.memory episodic
 */
@DisplayName("Memory Governance Tests (9.2)")
class MemoryGovernanceTest {

    private MemoryRedactionFilter redactionFilter;
    private MemorySecurityManager securityManager;

    @BeforeEach
    void setUp() {
        redactionFilter = MemoryRedactionFilter.defaultFilter();
        securityManager = new TenantIsolatedMemorySecurityManager();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9.2.4 — Redaction tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("9.2.4.1 — email address in episode content is redacted")
    void emailIsRedacted() {
        String rawContent = "User john.doe@example.com requested phase advance from draft to review.";
        String redacted = redactionFilter.redact(rawContent);

        assertThat(redacted).doesNotContain("john.doe@example.com");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    @DisplayName("9.2.4.2 — phone number in episode content is redacted")
    void phoneIsRedacted() {
        String rawContent = "Contact person at 555-123-4567 approved the phase gate.";
        String redacted = redactionFilter.redact(rawContent);

        assertThat(redacted).doesNotContain("555-123-4567");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    @DisplayName("9.2.4.3 — API key in episode content is redacted")
    void apiKeyIsRedacted() {
        String rawContent = "Agent configured with api_key=sk-abc123xyz987 to call external tool.";
        String redacted = redactionFilter.redact(rawContent);

        assertThat(redacted).doesNotContain("sk-abc123xyz987");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    @DisplayName("9.2.4.4 — bearer token in episode content is redacted")
    void bearerTokenIsRedacted() {
        String rawContent = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig";
        String redacted = redactionFilter.redact(rawContent);

        assertThat(redacted).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    @DisplayName("9.2.4.5 — non-sensitive content passes through unchanged")
    void nonSensitiveContentUnchanged() {
        String content = "The intent-agent analyzed project scope and produced a 5-phase plan.";
        String result = redactionFilter.redact(content);

        assertThat(result).isEqualTo(content);
    }

    @Test
    @DisplayName("9.2.4.6 — multiple PII types in single text all redacted")
    void multiplePiiTypesRedacted() {
        String content = "User alice@corp.com (phone: 555-987-6543) submitted a request.";
        String redacted = redactionFilter.redact(content);

        assertThat(redacted)
                .doesNotContain("alice@corp.com")
                .doesNotContain("555-987-6543")
                .contains("[REDACTED]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9.2.2 — Tenant isolation tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("9.2.2.1 — same-tenant read is allowed")
    void sameTenantReadAllowed() {
        MemoryItem item = episodeForTenant("tenant-a");
        assertThat(securityManager.canRead(item, "tenant-a", "intent-agent")).isTrue();
    }

    @Test
    @DisplayName("9.2.2.2 — cross-tenant read is DENIED")
    void crossTenantReadDenied() {
        MemoryItem item = episodeForTenant("tenant-a");
        assertThat(securityManager.canRead(item, "tenant-b", "intent-agent")).isFalse();
    }

    @Test
    @DisplayName("9.2.2.3 — same-tenant write is allowed")
    void sameTenantWriteAllowed() {
        MemoryItem item = episodeForTenant("tenant-x");
        assertThat(securityManager.canWrite(item, "tenant-x", "plan-agent")).isTrue();
    }

    @Test
    @DisplayName("9.2.2.4 — cross-tenant write is DENIED")
    void crossTenantWriteDenied() {
        MemoryItem item = episodeForTenant("tenant-x");
        assertThat(securityManager.canWrite(item, "tenant-y", "plan-agent")).isFalse();
    }

    @Test
    @DisplayName("9.2.2.5 — search is always permitted (filtering is query-layer responsibility)")
    void searchAlwaysPermitted() {
        assertThat(securityManager.canSearch("tenant-a", "search-agent")).isTrue();
        assertThat(securityManager.canSearch("tenant-b", "search-agent")).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    private MemoryItem episodeForTenant(String tenantId) {
        return EnhancedEpisode.builder()
                .id("ep-" + tenantId + "-001")
                .agentId("test-agent")
                .turnId("turn-001")
                .tenantId(tenantId)
                .input("test input")
                .output("test output")
                .build();
    }
}
