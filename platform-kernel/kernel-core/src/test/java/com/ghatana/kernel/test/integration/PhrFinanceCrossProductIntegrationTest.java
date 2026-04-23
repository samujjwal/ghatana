package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.audit.CrossScopeAuditService;
import com.ghatana.kernel.audit.CrossScopeAuditService.AuditEventStore;
import com.ghatana.kernel.audit.CrossScopeAuditService.CrossScopeAuditEvent;
import com.ghatana.kernel.audit.CrossScopeAuditService.ScopeAuditRecord;
import com.ghatana.kernel.boundary.BoundaryPolicyResolver;
import com.ghatana.kernel.boundary.DefaultBoundaryPolicyResolver;
import com.ghatana.kernel.boundary.ScopeBoundaryEnforcer;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.context.KernelTenantContext.SecurityContext;
import com.ghatana.kernel.context.KernelTenantContext.TenantType;
import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.policy.ClassificationDescriptor.SensitivityLevel;
import com.ghatana.kernel.policy.DefaultAuditPolicyResolver;
import com.ghatana.kernel.scope.ScopeDescriptor;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHR-Finance Cross-Scope Integration Tests.
 *
 * <p>Validates the canonical cross-scope boundary enforcement and audit pipeline
 * using {@link ScopeBoundaryEnforcer} and {@link CrossScopeAuditService}.
 * These tests replaced the disabled placeholder that referenced the legacy
 * {@code CrossProductAuditService} and {@code ProductBoundaryEnforcer} APIs.</p>
 *
 * <p>Test scenarios cover the two most representative cross-domain access patterns:
 * <ul>
 *   <li>PHR data sharing (healthcare domain, RESTRICTED sensitivity, Nepal-2081 compliance)</li> // GH-90000
 *   <li>Finance trade data access (finance domain, CONFIDENTIAL sensitivity, SEBON compliance)</li> // GH-90000
 * </ul></p>
 *
 * @doc.type test
 * @doc.purpose Cross-scope boundary and audit integration tests using canonical scope-aware APIs
 * @doc.layer test
 * @doc.pattern Integration Test
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
@DisplayName("PHR-Finance Cross-Scope Integration Tests")
class PhrFinanceCrossProductIntegrationTest {

    private ScopeBoundaryEnforcer boundaryEnforcer;
    private CrossScopeAuditService auditService;
    private InMemoryAuditStore auditStore;

    // Canonical scope descriptors — no product-id literal strings
    private static final ScopeDescriptor PHR_SCOPE      = ScopeDescriptor.domainPack("phr");
    private static final ScopeDescriptor FINANCE_SCOPE  = ScopeDescriptor.domainPack("finance");
    private static final ScopeDescriptor PLATFORM_SCOPE = ScopeDescriptor.product("platform");

    // Classification descriptors — replaces hardcoded product-id branching
    private static final ClassificationDescriptor PHR_PROTECTED =
            ClassificationDescriptor.of("healthcare", SensitivityLevel.RESTRICTED, "nepal-2081"); // GH-90000
    private static final ClassificationDescriptor FINANCE_TRADE =
            ClassificationDescriptor.of("regulatory", SensitivityLevel.CONFIDENTIAL, "sebon"); // GH-90000
    private static final ClassificationDescriptor GENERAL_DATA =
            ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL); // GH-90000
    private static final ClassificationDescriptor RESIDENCY_LOCKED =
            ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL, "data-residency-restricted"); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        BoundaryPolicyResolver policyResolver = DefaultBoundaryPolicyResolver.withStandardRules(); // GH-90000
        boundaryEnforcer = new ScopeBoundaryEnforcer(policyResolver); // GH-90000

        auditStore = new InMemoryAuditStore(); // GH-90000
        auditService = new CrossScopeAuditService( // GH-90000
                DefaultAuditPolicyResolver.withStandardMappings(), auditStore); // GH-90000
    }

    // ── PHR Boundary Tests ───────────────────────────────────────────────────

    @Nested
    @DisplayName("PHR data access boundary enforcement")
    class PhrBoundaryTests {

        @Test
        @DisplayName("Finance cannot read PHR restricted patient records without consent")
        void financeCannotReadPhrRestrictedDataWithoutConsentFeature() { // GH-90000
            // No permissions, no consent feature → Layer 2 and Layer 3 both fail
            KernelTenantContext tenant = tenantContext("tenant-1", // GH-90000
                    Set.of("read:patient.records"),
                    Set.of() /* no consent feature */); // GH-90000

            boolean allowed = boundaryEnforcer.canAccess( // GH-90000
                    FINANCE_SCOPE, PHR_SCOPE,
                    "patient.records", "read",
                    PHR_PROTECTED,
                    tenant);

            assertFalse(allowed, // GH-90000
                "Finance must not read PHR restricted records without the consent feature enabled");
        }

        @Test
        @DisplayName("Finance can read PHR restricted records when tenant has consent feature")
        void financeCanReadPhrRestrictedDataWithConsentFeature() { // GH-90000
            // Layer 2: has permission; Layer 3: has consent feature for DOMAIN_PACK target
            KernelTenantContext tenant = tenantContext("tenant-1", // GH-90000
                    Set.of("read:patient.records"),
                    Set.of("cross-scope.consent.domain_pack"));

            boolean allowed = boundaryEnforcer.canAccess( // GH-90000
                    FINANCE_SCOPE, PHR_SCOPE,
                    "patient.records", "read",
                    PHR_PROTECTED,
                    tenant);

            assertTrue(allowed, // GH-90000
                "Finance should read PHR restricted records when consent feature is enabled");
        }

        @Test
        @DisplayName("PHR internal data allows cross-scope read with permission but no consent")
        void phrInternalDataAllowsReadWithoutConsent() { // GH-90000
            // INTERNAL sensitivity → no consent required, only permission
            KernelTenantContext tenant = tenantContext("tenant-2", // GH-90000
                    Set.of("read:patient.records"),
                    Set.of()); // GH-90000

            boolean allowed = boundaryEnforcer.canAccess( // GH-90000
                    PLATFORM_SCOPE, PHR_SCOPE,
                    "patient.records", "read",
                    GENERAL_DATA,
                    tenant);

            assertTrue(allowed, // GH-90000
                "Platform should read INTERNAL PHR data with permission alone (no consent required)"); // GH-90000
        }

        @Test
        @DisplayName("PHR data with residency restriction is blocked cross-scope even with consent")
        void phrResidencyLockedDataBlockedCrossScope() { // GH-90000
            KernelTenantContext tenant = tenantContext("tenant-1", // GH-90000
                    Set.of("read:patient.records"),
                    Set.of("cross-scope.consent.domain_pack"));

            boolean allowed = boundaryEnforcer.canAccess( // GH-90000
                    FINANCE_SCOPE, PHR_SCOPE,
                    "patient.records", "read",
                    RESIDENCY_LOCKED,
                    tenant);

            assertFalse(allowed, // GH-90000
                "Data-residency-restricted data must be blocked at Layer 1 regardless of consent or permissions");
        }
    }

    // ── Finance Boundary Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("Finance trade data access boundary enforcement")
    class FinanceBoundaryTests {

        @Test
        @DisplayName("PHR can write finance trade records when it holds the write permission")
        void phrCanWriteFinanceTradeRecordsWithPermission() { // GH-90000
            // CONFIDENTIAL data → no consent required, Layer 2 permission sufficient
            KernelTenantContext tenant = tenantContext("tenant-3", // GH-90000
                    Set.of("write:trade.records"),
                    Set.of()); // GH-90000

            boolean allowed = boundaryEnforcer.canAccess( // GH-90000
                    PHR_SCOPE, FINANCE_SCOPE,
                    "trade.records", "write",
                    FINANCE_TRADE,
                    tenant);

            assertTrue(allowed, // GH-90000
                "PHR with write permission can write finance CONFIDENTIAL trade records (requires audit, not consent)"); // GH-90000
        }

        @Test
        @DisplayName("Cross-scope write is denied without the required permission")
        void crossScopeWriteDeniedWithoutPermission() { // GH-90000
            KernelTenantContext tenant = tenantContext("tenant-3", Set.of(), Set.of()); // GH-90000

            boolean allowed = boundaryEnforcer.canAccess( // GH-90000
                    PHR_SCOPE, FINANCE_SCOPE,
                    "trade.records", "write",
                    FINANCE_TRADE,
                    tenant);

            assertFalse(allowed, // GH-90000
                "Cross-scope write should be denied when the tenant lacks write:trade.records permission");
        }

        @Test
        @DisplayName("Same-scope access with valid permission is allowed without consent")
        void sameScopeAccessAllowed() { // GH-90000
            // Source == target → Layer 1 returns allow(false); Layers 2+3 still gate on permission // GH-90000
            KernelTenantContext tenant = tenantContext("tenant-4", // GH-90000
                    Set.of("write:trade.records"),
                    Set.of()); // GH-90000

            boolean allowed = boundaryEnforcer.canAccess( // GH-90000
                    FINANCE_SCOPE, FINANCE_SCOPE,
                    "trade.records", "write",
                    FINANCE_TRADE,
                    tenant);

            assertTrue(allowed, "Same-scope access with valid permission should be allowed"); // GH-90000
        }
    }

    // ── Audit Policy Tests ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Scope-aware audit service policy resolution")
    class AuditPolicyTests {

        @Test
        @DisplayName("PHR audit record uses Nepal-2081 25-year retention")
        void phrAuditUsesNepal2081Retention() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(FINANCE_SCOPE) // GH-90000
                    .targetScope(PHR_SCOPE) // GH-90000
                    .action("data.share")
                    .userId("user-101")
                    .tenantId("tenant-nep")
                    .classification(PHR_PROTECTED) // GH-90000
                    .build(); // GH-90000

            auditService.auditCrossScopeAction(event).getResult(); // GH-90000

            assertEquals(1, auditStore.records.size()); // GH-90000
            ScopeAuditRecord stored = auditStore.records.get(0); // GH-90000
            assertEquals(25, stored.getRetentionYears(), // GH-90000
                "PHR data under nepal-2081 compliance tag should use 25-year retention");
            assertEquals("archive", stored.getStorageTier(), // GH-90000
                "25-year retention should use 'archive' storage tier");
        }

        @Test
        @DisplayName("Finance audit record uses SEBON 10-year retention")
        void financeAuditUsesSebon10YearRetention() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(PHR_SCOPE) // GH-90000
                    .targetScope(FINANCE_SCOPE) // GH-90000
                    .action("compliance.query")
                    .userId("user-202")
                    .tenantId("tenant-fin")
                    .classification(FINANCE_TRADE) // GH-90000
                    .build(); // GH-90000

            auditService.auditCrossScopeAction(event).getResult(); // GH-90000

            assertEquals(1, auditStore.records.size()); // GH-90000
            ScopeAuditRecord stored = auditStore.records.get(0); // GH-90000
            assertEquals(10, stored.getRetentionYears(), // GH-90000
                "Finance data under sebon compliance tag should use 10-year retention");
            assertEquals("compliance", stored.getStorageTier(), // GH-90000
                "10-year retention should use 'compliance' storage tier");
        }

        @Test
        @DisplayName("RESTRICTED audit record includes a cryptographic signature")
        void restrictedAuditRecordIncludesSignature() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(PLATFORM_SCOPE) // GH-90000
                    .targetScope(PHR_SCOPE) // GH-90000
                    .action("patient.export")
                    .userId("user-303")
                    .tenantId("tenant-1")
                    .classification(PHR_PROTECTED) // GH-90000
                    .build(); // GH-90000

            auditService.auditCrossScopeAction(event).getResult(); // GH-90000

            ScopeAuditRecord stored = auditStore.records.get(0); // GH-90000
            assertNotNull(stored.getSignature(), // GH-90000
                "RESTRICTED data audit records must include a cryptographic signature for tamper evidence");
        }

        @Test
        @DisplayName("Audit event type uses 'cross-scope.' prefix, not legacy 'cross-product.'")
        void auditEventTypeUsesScopePrefix() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(FINANCE_SCOPE) // GH-90000
                    .targetScope(PHR_SCOPE) // GH-90000
                    .action("data.check")
                    .userId("usr")
                    .tenantId("t1")
                    .classification(GENERAL_DATA) // GH-90000
                    .build(); // GH-90000

            auditService.auditCrossScopeAction(event).getResult(); // GH-90000

            ScopeAuditRecord stored = auditStore.records.get(0); // GH-90000
            String eventType = stored.getEventType(); // GH-90000
            assertNotNull(eventType); // GH-90000
            assertTrue(eventType.startsWith("cross-scope."),
                "Audit event type must start with 'cross-scope.' (got: " + eventType + ")"); // GH-90000
            assertFalse(eventType.startsWith("cross-product."),
                "Audit event type must NOT use legacy 'cross-product.' prefix");
        }

        @Test
        @DisplayName("Audit record stores ScopeDescriptor objects, not raw product-id strings")
        void auditRecordCarriesScopeDescriptors() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(FINANCE_SCOPE) // GH-90000
                    .targetScope(PHR_SCOPE) // GH-90000
                    .action("analytics.query")
                    .userId("analyst")
                    .tenantId("t-123")
                    .classification(FINANCE_TRADE) // GH-90000
                    .build(); // GH-90000

            auditService.auditCrossScopeAction(event).getResult(); // GH-90000

            ScopeAuditRecord stored = auditStore.records.get(0); // GH-90000
            assertEquals(FINANCE_SCOPE, stored.getSourceScope(), // GH-90000
                "Source scope must be stored as a ScopeDescriptor");
            assertEquals(PHR_SCOPE, stored.getTargetScope(), // GH-90000
                "Target scope must be stored as a ScopeDescriptor");
        }
    }

    // ── Boundary + Audit Integration ─────────────────────────────────────────

    @Nested
    @DisplayName("Boundary enforcement + audit trail integration")
    class BoundaryAuditIntegrationTests {

        @Test
        @DisplayName("Denied access creates no audit record — boundary gate fires before audit")
        void deniedAccessProducesNoAuditRecord() { // GH-90000
            KernelTenantContext tenant = tenantContext("t-denied", Set.of(), Set.of()); // GH-90000

            boolean allowed = boundaryEnforcer.canAccess( // GH-90000
                    PHR_SCOPE, FINANCE_SCOPE,
                    "trade.records", "write",
                    FINANCE_TRADE,
                    tenant);

            assertFalse(allowed); // GH-90000
            assertEquals(0, auditStore.records.size(), // GH-90000
                "No audit record should be written when boundary enforcement denies access");
        }

        @Test
        @DisplayName("Policy engine is product-agnostic — same rules apply to any domain-pack pair")
        void policyDrivenAccessIsProductAgnostic() { // GH-90000
            ScopeDescriptor alpha = ScopeDescriptor.domainPack("domain-alpha");
            ScopeDescriptor beta  = ScopeDescriptor.domainPack("domain-beta");
            ClassificationDescriptor restricted =
                    ClassificationDescriptor.of("general", SensitivityLevel.RESTRICTED, "nepal-2081"); // GH-90000

            KernelTenantContext tenantWithConsent = tenantContext("t-alpha", // GH-90000
                    Set.of("read:patient.records"),
                    Set.of("cross-scope.consent.domain_pack"));

            boolean allowed = boundaryEnforcer.canAccess( // GH-90000
                    alpha, beta,
                    "patient.records", "read",
                    restricted,
                    tenantWithConsent);

            assertTrue(allowed, // GH-90000
                "Policy-driven access must work for any domain-pack pair, not just phr/finance-named scopes");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a {@link KernelTenantContext} for testing.
     *
     * @param tenantId        tenant id
     * @param permissions     permissions checked via {@link SecurityContext#hasPermission}
     * @param enabledFeatures feature flags checked via {@link KernelTenantContext#isFeatureEnabled}
     */
    private static KernelTenantContext tenantContext(String tenantId, // GH-90000
                                                     Set<String> permissions,
                                                     Set<String> enabledFeatures) {
        SecurityContext secCtx = new SecurityContext() { // GH-90000
            @Override public String getUserId() { return "test-user"; } // GH-90000
            @Override public Set<String> getRoles() { return Set.of(); } // GH-90000
            @Override public Set<String> getPermissions() { return Set.copyOf(permissions); } // GH-90000
            @Override public boolean isAuthenticated() { return true; } // GH-90000
            @Override public boolean hasRole(String role) { return false; } // GH-90000
            @Override public boolean hasPermission(String permission) { return permissions.contains(permission); } // GH-90000
        };
        return new KernelTenantContext( // GH-90000
                tenantId,
                TenantType.STANDARD,
                Map.of(), // GH-90000
                Set.copyOf(enabledFeatures), // GH-90000
                secCtx,
                null
        );
    }

    private static class InMemoryAuditStore implements AuditEventStore {
        final List<ScopeAuditRecord> records = new CopyOnWriteArrayList<>(); // GH-90000

        @Override
        public Promise<Void> store(ScopeAuditRecord record) { // GH-90000
            records.add(record); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Set<ScopeAuditRecord>> query(Instant start, Instant end, // GH-90000
                                                     ScopeDescriptor source,
                                                     ScopeDescriptor target) {
            Set<ScopeAuditRecord> result = new HashSet<>(); // GH-90000
            for (ScopeAuditRecord r : records) { // GH-90000
                if (r.getTimestamp() != null // GH-90000
                        && !r.getTimestamp().isBefore(start) // GH-90000
                        && !r.getTimestamp().isAfter(end)) { // GH-90000
                    if (source == null || source.equals(r.getSourceScope())) { // GH-90000
                        if (target == null || target.equals(r.getTargetScope())) { // GH-90000
                            result.add(r); // GH-90000
                        }
                    }
                }
            }
            return Promise.of(result); // GH-90000
        }
    }
}
