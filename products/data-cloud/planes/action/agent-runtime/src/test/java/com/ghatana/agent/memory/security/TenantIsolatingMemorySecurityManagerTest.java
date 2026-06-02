package com.ghatana.agent.memory.security;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.MemoryLink;
import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.Validity;
import com.ghatana.agent.memory.model.ValidityStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantIsolatingMemorySecurityManager")
class TenantIsolatingMemorySecurityManagerTest {

    private final TenantIsolatingMemorySecurityManager manager = new TenantIsolatingMemorySecurityManager();

    @Test
    @DisplayName("read allows same tenant")
    void readAllowsSameTenant() {
        assertThat(manager.canRead(memoryItem("tenant-a"), "tenant-a", "agent-alpha")).isTrue();
    }

    @Test
    @DisplayName("read denies other tenant")
    void readDeniesOtherTenant() {
        assertThat(manager.canRead(memoryItem("tenant-b"), "tenant-a", "agent-alpha")).isFalse();
    }

    @Test
    @DisplayName("write denies other tenant")
    void writeDeniesOtherTenant() {
        assertThat(manager.canWrite(memoryItem("tenant-b"), "tenant-a", "agent-alpha")).isFalse();
    }

    @Test
    @DisplayName("search returns tenant scoped policy")
    void searchReturnsTenantScopedPolicy() {
        MemorySecurityManager.MemorySearchPolicy policy = manager.authorizeSearch(
                new MemorySecurityManager.MemorySearchRequest(
                        EnumSet.of(MemoryItemType.FACT, MemoryItemType.PROCEDURE),
                        Set.of("INTERNAL", "PII"),
                        false,
                        true,
                        100,
                        null,
                        null
                ),
                "tenant-a",
                "agent-alpha"
        );

        assertThat(policy.allowed()).isTrue();
        assertThat(policy.tenantFilter()).isEqualTo("tenant-a");
        assertThat(policy.allowedAgentScopes()).containsExactly("agent-alpha");
        assertThat(policy.allowedMemoryTiers()).containsExactlyInAnyOrder(MemoryItemType.FACT, MemoryItemType.PROCEDURE);
        assertThat(policy.allowedClassifications()).containsExactly("INTERNAL");
        assertThat(policy.redactionRequirements()).contains("MASK_PII", "MASK_CREDENTIALS");
        assertThat(policy.maxResultLimit()).isEqualTo(50);
    }

    @Test
    @DisplayName("blank tenant denied")
    void blankTenantDenied() {
        MemorySecurityManager.MemorySearchPolicy policy = manager.authorizeSearch(
                MemorySecurityManager.MemorySearchRequest.defaultRequest(),
                " ",
                "agent-alpha"
        );

        assertThat(policy.allowed()).isFalse();
        assertThat(policy.denialReason()).isEqualTo("invalid-tenant");
    }

    @Test
    @DisplayName("shared memory search denied for unauthorized agent")
    void sharedMemoryDeniedForUnauthorizedAgent() {
        MemorySecurityManager.MemorySearchPolicy policy = manager.authorizeSearch(
                new MemorySecurityManager.MemorySearchRequest(
                        EnumSet.allOf(MemoryItemType.class),
                        Set.of("INTERNAL"),
                        true,
                        true,
                        10,
                        null,
                        null
                ),
                "tenant-a",
                "agent-alpha"
        );

        assertThat(policy.allowed()).isFalse();
        assertThat(policy.denialReason()).isEqualTo("shared-memory-not-authorized");
    }

    @Test
    @DisplayName("PII and PHI classifications are denied unless explicitly allowed")
    void piiAndPhiDeniedWithoutExplicitClassificationAllowance() {
        MemorySecurityManager.MemorySearchPolicy policy = manager.authorizeSearch(
                new MemorySecurityManager.MemorySearchRequest(
                        EnumSet.of(MemoryItemType.FACT),
                        Set.of("PII", "PHI"),
                        false,
                        true,
                        10,
                        null,
                        null
                ),
                "tenant-a",
                "agent-alpha"
        );

        assertThat(policy.allowed()).isFalse();
        assertThat(policy.denialReason()).isEqualTo("classification-not-authorized");
    }

    private MemoryItem memoryItem(String tenantId) {
        return new MemoryItem() {
            @Override
            public String getId() {
                return "memory-1";
            }

            @Override
            public MemoryItemType getType() {
                return MemoryItemType.FACT;
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.parse("2026-01-01T00:00:00Z");
            }

            @Override
            public Instant getUpdatedAt() {
                return Instant.parse("2026-01-01T00:00:00Z");
            }

            @Override
            public Instant getExpiresAt() {
                return null;
            }

            @Override
            public Provenance getProvenance() {
                return Provenance.builder()
                        .source("system")
                        .confidenceSource(Provenance.ConfidenceSource.TOOL_OUTPUT)
                        .traceId("test-trace")
                        .agentId("test-agent")
                        .sessionId("unit-test")
                        .parentItemId("test-run")
                        .build();
            }

            @Override
            public float[] getEmbedding() {
                return null;
            }

            @Override
            public Validity getValidity() {
                return Validity.builder()
                        .confidence(1.0d)
                        .decayRate(0.0d)
                        .lastVerified(Instant.parse("2026-01-01T00:00:00Z"))
                    .status(ValidityStatus.ACTIVE)
                        .build();
            }

            @Override
            public List<MemoryLink> getLinks() {
                return List.of();
            }

            @Override
            public Map<String, String> getLabels() {
                return Map.of();
            }

            @Override
            public String getTenantId() {
                return tenantId;
            }

            @Override
            public String getSphereId() {
                return null;
            }

            @Override
            public String getClassification() {
                return "INTERNAL";
            }
        };
    }
}