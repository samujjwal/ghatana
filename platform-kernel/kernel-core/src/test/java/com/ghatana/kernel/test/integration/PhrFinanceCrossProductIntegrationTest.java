package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.audit.CrossScopeAuditService;
import com.ghatana.kernel.audit.CrossScopeAuditService.AuditEventStore;
import com.ghatana.kernel.audit.CrossScopeAuditService.CrossScopeAuditEvent;
import com.ghatana.kernel.audit.CrossScopeAuditService.ScopeAuditRecord;
import com.ghatana.kernel.policy.BoundaryPolicyResolver;
import com.ghatana.kernel.policy.DefaultBoundaryPolicyResolver;
import com.ghatana.kernel.boundary.ScopeBoundaryEnforcer;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.context.KernelTenantContext.SecurityContext;
import com.ghatana.kernel.context.KernelTenantContext.TenantType;
import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.policy.ClassificationDescriptor.SensitivityLevel;
import com.ghatana.kernel.policy.DefaultAuditPolicyResolver;
import com.ghatana.kernel.policy.DefaultRetentionPolicyResolver;
import com.ghatana.kernel.policy.AuditPolicyResolver;
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
 *   <li>PHR data sharing (healthcare domain, RESTRICTED sensitivity, Nepal-2081 compliance)</li> 
 *   <li>Finance trade data access (finance domain, CONFIDENTIAL sensitivity, SEBON compliance)</li> 
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

    // Canonical scope descriptors â€” neutral domain-pack identifiers, no product-id literal strings
    private static final ScopeDescriptor DOMAIN_ALPHA_SCOPE  = ScopeDescriptor.domainPack("domain-alpha");
    private static final ScopeDescriptor DOMAIN_BETA_SCOPE   = ScopeDescriptor.domainPack("domain-beta");
    private static final ScopeDescriptor PLATFORM_SCOPE      = ScopeDescriptor.product("platform");

    // Classification descriptors â€” compliance tags are framework identifiers, not product names
    private static final ClassificationDescriptor RESTRICTED_LONG_RETENTION =
            ClassificationDescriptor.of("regulated", SensitivityLevel.RESTRICTED, "nepal-2081");
    private static final ClassificationDescriptor CONFIDENTIAL_COMPLIANCE =
            ClassificationDescriptor.of("regulatory", SensitivityLevel.CONFIDENTIAL, "sebon");
    private static final ClassificationDescriptor INTERNAL_GENERAL =
            ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL);
    private static final ClassificationDescriptor RESIDENCY_LOCKED =
            ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL, "data-residency-restricted");

    @BeforeEach
    void setUp() {
        BoundaryPolicyResolver policyResolver = DefaultBoundaryPolicyResolver.withStandardMappings();
        boundaryEnforcer = new ScopeBoundaryEnforcer(policyResolver);

        auditStore = new InMemoryAuditStore();
        // Use custom retention mappings for test scenarios
        DefaultRetentionPolicyResolver retentionResolver = new DefaultRetentionPolicyResolver(Map.of(
            "nepal-2081", 25,
            "sebon", 10,
            "gdpr", 7
        ));
        AuditPolicyResolver auditPolicyResolver = new DefaultAuditPolicyResolver(retentionResolver);
        auditService = new CrossScopeAuditService(auditPolicyResolver, auditStore);
    }

    // â”€â”€ PHR Boundary Tests â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Nested
    @DisplayName("PHR data access boundary enforcement")
    class PhrBoundaryTests {

        @Test
        @DisplayName("Finance cannot read PHR restricted patient records without consent")
        void financeCannotReadPhrRestrictedDataWithoutConsentFeature() { 
            // No permissions, no consent feature â†’ Layer 2 and Layer 3 both fail
            KernelTenantContext tenant = tenantContext("tenant-1", 
                    Set.of("read:patient.records"),
                    Set.of() /* no consent feature */); 

            boolean allowed = boundaryEnforcer.canAccess( 
                    DOMAIN_BETA_SCOPE, DOMAIN_ALPHA_SCOPE,
                    "patient.records", "read",
                    RESTRICTED_LONG_RETENTION,
                    tenant);

            assertFalse(allowed, 
                "Finance must not read PHR restricted records without the consent feature enabled");
        }

        @Test
        @DisplayName("Finance can read PHR restricted records when tenant has consent feature")
        void financeCanReadPhrRestrictedDataWithConsentFeature() { 
            // Layer 2: has permission; Layer 3: has consent feature for DOMAIN_PACK target
            KernelTenantContext tenant = tenantContext("tenant-1", 
                    Set.of("read:patient.records"),
                    Set.of("cross-scope.consent.domain_pack"));

            boolean allowed = boundaryEnforcer.canAccess( 
                    DOMAIN_BETA_SCOPE, DOMAIN_ALPHA_SCOPE,
                    "patient.records", "read",
                    RESTRICTED_LONG_RETENTION,
                    tenant);

            assertTrue(allowed, 
                "Finance should read PHR restricted records when consent feature is enabled");
        }

        @Test
        @DisplayName("PHR internal data allows cross-scope read with permission but no consent")
        void phrInternalDataAllowsReadWithoutConsent() { 
            // INTERNAL sensitivity â†’ no consent required, only permission
            KernelTenantContext tenant = tenantContext("tenant-2", 
                    Set.of("read:patient.records"),
                    Set.of()); 

            boolean allowed = boundaryEnforcer.canAccess( 
                    PLATFORM_SCOPE, DOMAIN_ALPHA_SCOPE,
                    "patient.records", "read",
                    INTERNAL_GENERAL,
                    tenant);

            assertTrue(allowed, 
                "Platform should read INTERNAL PHR data with permission alone (no consent required)"); 
        }

        @Test
        @DisplayName("PHR data with residency restriction is blocked cross-scope even with consent")
        void phrResidencyLockedDataBlockedCrossScope() { 
            KernelTenantContext tenant = tenantContext("tenant-1", 
                    Set.of("read:patient.records"),
                    Set.of("cross-scope.consent.domain_pack"));

            boolean allowed = boundaryEnforcer.canAccess( 
                    DOMAIN_BETA_SCOPE, DOMAIN_ALPHA_SCOPE,
                    "patient.records", "read",
                    RESIDENCY_LOCKED,
                    tenant);

            assertFalse(allowed, 
                "Data-residency-restricted data must be blocked at Layer 1 regardless of consent or permissions");
        }
    }

    // â”€â”€ Finance Boundary Tests â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Nested
    @DisplayName("Finance trade data access boundary enforcement")
    class FinanceBoundaryTests {

        @Test
        @DisplayName("PHR can write finance trade records when it holds the write permission")
        void phrCanWriteFinanceTradeRecordsWithPermission() { 
            // CONFIDENTIAL data â†’ no consent required, Layer 2 permission sufficient
            KernelTenantContext tenant = tenantContext("tenant-3", 
                    Set.of("write:trade.records"),
                    Set.of()); 

            boolean allowed = boundaryEnforcer.canAccess( 
                    DOMAIN_ALPHA_SCOPE, DOMAIN_BETA_SCOPE,
                    "trade.records", "write",
                    CONFIDENTIAL_COMPLIANCE,
                    tenant);

            assertTrue(allowed, 
                "PHR with write permission can write finance CONFIDENTIAL trade records (requires audit, not consent)"); 
        }

        @Test
        @DisplayName("Cross-scope write is denied without the required permission")
        void crossScopeWriteDeniedWithoutPermission() { 
            KernelTenantContext tenant = tenantContext("tenant-3", Set.of(), Set.of()); 

            boolean allowed = boundaryEnforcer.canAccess( 
                    DOMAIN_ALPHA_SCOPE, DOMAIN_BETA_SCOPE,
                    "trade.records", "write",
                    CONFIDENTIAL_COMPLIANCE,
                    tenant);

            assertFalse(allowed, 
                "Cross-scope write should be denied when the tenant lacks write:trade.records permission");
        }

        @Test
        @DisplayName("Same-scope access with valid permission is allowed without consent")
        void sameScopeAccessAllowed() { 
            // Source == target â†’ Layer 1 returns allow(false); Layers 2+3 still gate on permission 
            KernelTenantContext tenant = tenantContext("tenant-4", 
                    Set.of("write:trade.records"),
                    Set.of()); 

            boolean allowed = boundaryEnforcer.canAccess( 
                    DOMAIN_BETA_SCOPE, DOMAIN_BETA_SCOPE,
                    "trade.records", "write",
                    CONFIDENTIAL_COMPLIANCE,
                    tenant);

            assertTrue(allowed, "Same-scope access with valid permission should be allowed"); 
        }
    }

    // â”€â”€ Audit Policy Tests â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Nested
    @DisplayName("Scope-aware audit service policy resolution")
    class AuditPolicyTests {

        @Test
        @DisplayName("PHR audit record uses Nepal-2081 25-year retention")
        void phrAuditUsesNepal2081Retention() { 
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() 
                    .sourceScope(DOMAIN_BETA_SCOPE) 
                    .targetScope(DOMAIN_ALPHA_SCOPE) 
                    .action("data.share")
                    .userId("user-101")
                    .tenantId("tenant-nep")
                    .classification(RESTRICTED_LONG_RETENTION) 
                    .build(); 

            auditService.auditCrossScopeAction(event).getResult(); 

            assertEquals(1, auditStore.records.size()); 
            ScopeAuditRecord stored = auditStore.records.get(0); 
            assertEquals(25, stored.getRetentionYears(), 
                "PHR data under nepal-2081 compliance tag should use 25-year retention");
            assertEquals("archive", stored.getStorageTier(), 
                "25-year retention should use 'archive' storage tier");
        }

        @Test
        @DisplayName("Finance audit record uses SEBON 10-year retention")
        void financeAuditUsesSebon10YearRetention() { 
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() 
                    .sourceScope(DOMAIN_ALPHA_SCOPE) 
                    .targetScope(DOMAIN_BETA_SCOPE) 
                    .action("compliance.query")
                    .userId("user-202")
                    .tenantId("tenant-fin")
                    .classification(CONFIDENTIAL_COMPLIANCE) 
                    .build(); 

            auditService.auditCrossScopeAction(event).getResult(); 

            assertEquals(1, auditStore.records.size()); 
            ScopeAuditRecord stored = auditStore.records.get(0); 
            assertEquals(10, stored.getRetentionYears(), 
                "Finance data under sebon compliance tag should use 10-year retention");
            assertEquals("compliance", stored.getStorageTier(), 
                "10-year retention should use 'compliance' storage tier");
        }

        @Test
        @DisplayName("RESTRICTED audit record includes a cryptographic signature")
        void restrictedAuditRecordIncludesSignature() { 
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() 
                    .sourceScope(PLATFORM_SCOPE) 
                    .targetScope(DOMAIN_ALPHA_SCOPE) 
                    .action("patient.export")
                    .userId("user-303")
                    .tenantId("tenant-1")
                    .classification(RESTRICTED_LONG_RETENTION) 
                    .build(); 

            auditService.auditCrossScopeAction(event).getResult(); 

            ScopeAuditRecord stored = auditStore.records.get(0); 
            assertNotNull(stored.getSignature(), 
                "RESTRICTED data audit records must include a cryptographic signature for tamper evidence");
        }

        @Test
        @DisplayName("Audit event type uses 'cross-scope.' prefix, not legacy 'cross-product.'")
        void auditEventTypeUsesScopePrefix() { 
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() 
                    .sourceScope(DOMAIN_BETA_SCOPE) 
                    .targetScope(DOMAIN_ALPHA_SCOPE) 
                    .action("data.check")
                    .userId("usr")
                    .tenantId("t1")
                    .classification(INTERNAL_GENERAL) 
                    .build(); 

            auditService.auditCrossScopeAction(event).getResult(); 

            ScopeAuditRecord stored = auditStore.records.get(0); 
            String eventType = stored.getEventType(); 
            assertNotNull(eventType); 
            assertTrue(eventType.startsWith("cross-scope."),
                "Audit event type must start with 'cross-scope.' (got: " + eventType + ")"); 
            assertFalse(eventType.startsWith("cross-product."),
                "Audit event type must NOT use legacy 'cross-product.' prefix");
        }

        @Test
        @DisplayName("Audit record stores ScopeDescriptor objects, not raw product-id strings")
        void auditRecordCarriesScopeDescriptors() { 
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() 
                    .sourceScope(DOMAIN_BETA_SCOPE) 
                    .targetScope(DOMAIN_ALPHA_SCOPE) 
                    .action("analytics.query")
                    .userId("analyst")
                    .tenantId("t-123")
                    .classification(CONFIDENTIAL_COMPLIANCE) 
                    .build(); 

            auditService.auditCrossScopeAction(event).getResult(); 

            ScopeAuditRecord stored = auditStore.records.get(0); 
            assertEquals(DOMAIN_BETA_SCOPE, stored.getSourceScope(), 
                "Source scope must be stored as a ScopeDescriptor");
            assertEquals(DOMAIN_ALPHA_SCOPE, stored.getTargetScope(), 
                "Target scope must be stored as a ScopeDescriptor");
        }
    }

    // â”€â”€ Boundary + Audit Integration â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Nested
    @DisplayName("Boundary enforcement + audit trail integration")
    class BoundaryAuditIntegrationTests {

        @Test
        @DisplayName("Denied access creates no audit record â€” boundary gate fires before audit")
        void deniedAccessProducesNoAuditRecord() { 
            KernelTenantContext tenant = tenantContext("t-denied", Set.of(), Set.of()); 

            boolean allowed = boundaryEnforcer.canAccess( 
                    DOMAIN_ALPHA_SCOPE, DOMAIN_BETA_SCOPE,
                    "trade.records", "write",
                    CONFIDENTIAL_COMPLIANCE,
                    tenant);

            assertFalse(allowed); 
            assertEquals(0, auditStore.records.size(), 
                "No audit record should be written when boundary enforcement denies access");
        }

        @Test
        @DisplayName("Policy engine is product-agnostic â€” same rules apply to any domain-pack pair")
        void policyDrivenAccessIsProductAgnostic() { 
            ScopeDescriptor alpha = ScopeDescriptor.domainPack("domain-alpha");
            ScopeDescriptor beta  = ScopeDescriptor.domainPack("domain-beta");
            ClassificationDescriptor restricted =
                    ClassificationDescriptor.of("general", SensitivityLevel.RESTRICTED, "nepal-2081"); 

            KernelTenantContext tenantWithConsent = tenantContext("t-alpha", 
                    Set.of("read:patient.records"),
                    Set.of("cross-scope.consent.domain_pack"));

            boolean allowed = boundaryEnforcer.canAccess( 
                    alpha, beta,
                    "patient.records", "read",
                    restricted,
                    tenantWithConsent);

            assertTrue(allowed, 
                "Policy-driven access must work for any domain-pack pair, not just phr/finance-named scopes");
        }
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Builds a {@link KernelTenantContext} for testing.
     *
     * @param tenantId        tenant id
     * @param permissions     permissions checked via {@link SecurityContext#hasPermission}
     * @param enabledFeatures feature flags checked via {@link KernelTenantContext#isFeatureEnabled}
     */
    private static KernelTenantContext tenantContext(String tenantId, 
                                                     Set<String> permissions,
                                                     Set<String> enabledFeatures) {
        SecurityContext secCtx = new SecurityContext() { 
            @Override public String getUserId() { return "test-user"; } 
            @Override public Set<String> getRoles() { return Set.of(); } 
            @Override public Set<String> getPermissions() { return Set.copyOf(permissions); } 
            @Override public boolean isAuthenticated() { return true; } 
            @Override public boolean hasRole(String role) { return false; } 
            @Override public boolean hasPermission(String permission) { return permissions.contains(permission); } 
        };
        return new KernelTenantContext( 
                tenantId,
                TenantType.STANDARD,
                Map.of(), 
                Set.copyOf(enabledFeatures), 
                secCtx,
                null
        );
    }

    private static class InMemoryAuditStore implements AuditEventStore {
        final List<ScopeAuditRecord> records = new CopyOnWriteArrayList<>(); 

        @Override
        public Promise<Void> store(ScopeAuditRecord record) { 
            records.add(record); 
            return Promise.complete(); 
        }

        @Override
        public Promise<Set<ScopeAuditRecord>> query(Instant start, Instant end, 
                                                     ScopeDescriptor source,
                                                     ScopeDescriptor target) {
            Set<ScopeAuditRecord> result = new HashSet<>(); 
            for (ScopeAuditRecord r : records) { 
                if (r.getTimestamp() != null 
                        && !r.getTimestamp().isBefore(start) 
                        && !r.getTimestamp().isAfter(end)) { 
                    if (source == null || source.equals(r.getSourceScope())) { 
                        if (target == null || target.equals(r.getTargetScope())) { 
                            result.add(r); 
                        }
                    }
                }
            }
            return Promise.of(result); 
        }
    }
}
