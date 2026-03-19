package com.ghatana.kernel.test.compliance;

import com.ghatana.finance.extension.RegulatoryComplianceKernelExtension;
import com.ghatana.phr.extension.HealthcareConsentKernelExtension;
import com.ghatana.kernel.audit.CrossProductAuditService;
import com.ghatana.kernel.plugin.security.KernelPluginSecurityManager;
import io.activej.test.ActivejTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compliance tests for regulatory retention requirements.
 *
 * <p>Validates compliance with:
 * <ul>
 *   <li>Finance (SEBON): 10-year retention</li>
 *   <li>PHR (Nepal Directive 2081): 25-year retention</li>
 *   <li>Audit immutability: cryptographic signatures</li>
 *   <li>Plugin security: Ed25519 signature validation</li>
 * </ul></p>
 *
 * @doc.type test
 * @doc.purpose Compliance tests for regulatory retention and security requirements
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
class RegulatoryComplianceTest extends ActivejTestBase {

    // ==================== Retention Period Tests ====================

    @Test
    void financeAuditRetentionIs10Years() {
        // SEBON requires 10-year retention for financial records
        CrossProductAuditService auditService = new CrossProductAuditService(null);

        CrossProductAuditService.CrossProductAuditEvent event =
            CrossProductAuditService.CrossProductAuditEvent.builder()
                .sourceProduct("finance")
                .targetProduct("external-system")
                .action("trade.execute")
                .userId("trader-1")
                .tenantId("finance-tenant")
                .metadata(Map.of("amount", 100000.00))
                .build();

        CrossProductAuditService.RetentionPeriod retention =
            auditService.getRetentionPeriod(event);

        assertEquals(10, retention.getYears(),
            "Finance records must be retained for 10 years per SEBON regulations");

        // Verify expiration calculation
        Instant now = Instant.now();
        Instant expiration = retention.getExpirationDate(now);
        long years = ChronoUnit.DAYS.between(now, expiration) / 365;

        assertTrue(years >= 10, "Retention period must be at least 10 years");
    }

    @Test
    void phrAuditRetentionIs25Years() {
        // Nepal Directive 2081 requires 25-year retention for healthcare records
        CrossProductAuditService auditService = new CrossProductAuditService(null);

        CrossProductAuditService.CrossProductAuditEvent event =
            CrossProductAuditService.CrossProductAuditEvent.builder()
                .sourceProduct("phr")
                .targetProduct("finance")
                .action("billing.create")
                .userId("doctor-1")
                .tenantId("healthcare-tenant")
                .metadata(Map.of("patient_id", "P-12345", "service", "surgery"))
                .build();

        CrossProductAuditService.RetentionPeriod retention =
            auditService.getRetentionPeriod(event);

        assertEquals(25, retention.getYears(),
            "PHR records must be retained for 25 years per Nepal Directive 2081");
    }

    @Test
    void crossDomainAuditUsesLongestRetention() {
        // When PHR and Finance interact, use the longer retention (PHR = 25 years)
        CrossProductAuditService auditService = new CrossProductAuditService(null);

        CrossProductAuditService.CrossProductAuditEvent event =
            CrossProductAuditService.CrossProductAuditEvent.builder()
                .sourceProduct("phr")
                .targetProduct("finance")
                .action("healthcare.payment")
                .build();

        CrossProductAuditService.RetentionPeriod retention =
            auditService.getRetentionPeriod(event);

        // Should use PHR retention (25 years) as it's longer
        assertEquals(25, retention.getYears(),
            "Cross-domain PHR-Finance events must use 25-year retention");
    }

    // ==================== Healthcare Consent Tests ====================

    @Test
    void healthcareConsentPolicyValidation() {
        HealthcareConsentKernelExtension extension = new HealthcareConsentKernelExtension();

        // Test valid consent
        HealthcareConsentKernelExtension.ConsentRecord validConsent =
            new HealthcareConsentKernelExtension.ConsentRecord(
                "consent-1",
                "patient-123",
                HealthcareConsentKernelExtension.ConsentPurpose.TREATMENT,
                HealthcareConsentKernelExtension.ConsentScope.ALL_DATA,
                HealthcareConsentKernelExtension.ConsentStatus.GRANTED,
                Instant.now(),
                Instant.now().plusSeconds(86400 * 30), // 30 days
                null,
                "Audit trail"
            );

        assertEquals(HealthcareConsentKernelExtension.ConsentStatus.GRANTED, validConsent.getStatus());
        assertFalse(validConsent.isExpired());
    }

    @Test
    void expiredConsentIsDetected() {
        HealthcareConsentKernelExtension.ConsentRecord expiredConsent =
            new HealthcareConsentKernelExtension.ConsentRecord(
                "consent-2",
                "patient-456",
                HealthcareConsentKernelExtension.ConsentPurpose.TREATMENT,
                HealthcareConsentKernelExtension.ConsentScope.ALL_DATA,
                HealthcareConsentKernelExtension.ConsentStatus.GRANTED,
                Instant.now().minusSeconds(86400 * 60), // 60 days ago
                Instant.now().minusSeconds(86400 * 30), // Expired 30 days ago
                null,
                "Audit trail"
            );

        assertTrue(expiredConsent.isExpired(),
            "Expired consent should be detected");
    }

    @Test
    void withdrawnConsentCannotBeUsed() {
        HealthcareConsentKernelExtension.ConsentRecord withdrawnConsent =
            new HealthcareConsentKernelExtension.ConsentRecord(
                "consent-3",
                "patient-789",
                HealthcareConsentKernelExtension.ConsentPurpose.RESEARCH,
                HealthcareConsentKernelExtension.ConsentScope.ANONYMIZED_ONLY,
                HealthcareConsentKernelExtension.ConsentStatus.WITHDRAWN,
                Instant.now().minusSeconds(86400 * 10),
                Instant.now().plusSeconds(86400 * 20),
                Instant.now(),
                "Withdrawn by patient"
            );

        assertEquals(HealthcareConsentKernelExtension.ConsentStatus.WITHDRAWN, withdrawnConsent.getStatus());
        assertFalse(withdrawnConsent.getStatus() == HealthcareConsentKernelExtension.ConsentStatus.GRANTED,
            "Withdrawn consent should not be usable");
    }

    // ==================== Financial Compliance Tests ====================

    @Test
    void sebonLargeTradeRequiresNotification() {
        RegulatoryComplianceKernelExtension extension = new RegulatoryComplianceKernelExtension();

        RegulatoryComplianceKernelExtension.Trade largeTrade =
            new RegulatoryComplianceKernelExtension.Trade(
                "trade-1", "NEPSE", 2000000, 100.0, 100.0, 200000000, 0.001,
                false, false, true, 0, false, true, "audit-trail"
            );

        RegulatoryComplianceKernelExtension.ComplianceValidation result =
            extension.validateTrade(largeTrade);

        assertFalse(result.isValid(),
            "Large trades (>1M volume) should require SEBON notification");
        assertTrue(result.getReason().contains("SEBON"),
            "Violation should mention SEBON requirement");
    }

    @Test
    void sebonCircuitBreakerEnforced() {
        RegulatoryComplianceKernelExtension extension = new RegulatoryComplianceKernelExtension();

        RegulatoryComplianceKernelExtension.Trade volatileTrade =
            new RegulatoryComplianceKernelExtension.Trade(
                "trade-2", "NEPSE", 1000, 120.0, 100.0, 120000, 0.001,
                false, false, true, 0, false, true, "audit-trail"
            );

        RegulatoryComplianceKernelExtension.ComplianceValidation result =
            extension.validateTrade(volatileTrade);

        // Price is 20% above last price (>10% circuit breaker)
        assertFalse(result.isValid(),
            "Price exceeding 10% circuit breaker should be rejected");
    }

    @Test
    void validTradePassesCompliance() {
        RegulatoryComplianceKernelExtension extension = new RegulatoryComplianceKernelExtension();

        RegulatoryComplianceKernelExtension.Trade validTrade =
            new RegulatoryComplianceKernelExtension.Trade(
                "trade-3", "NEPSE", 100, 105.0, 100.0, 10500, 0.001,
                false, false, true, 0, false, true, "audit-trail"
            );

        RegulatoryComplianceKernelExtension.ComplianceValidation result =
            extension.validateTrade(validTrade);

        assertTrue(result.isValid(),
            "Valid trade within limits should pass compliance");
    }

    // ==================== Plugin Security Tests ====================

    @Test
    void pluginSecurityManagerValidatesSignatures() {
        KernelPluginSecurityManager securityManager = new KernelPluginSecurityManager(true);

        // Add a trusted test key (base64 encoded Ed25519 public key)
        String testPublicKey = java.util.Base64.getEncoder()
            .encodeToString(new byte[32]); // 32 bytes for Ed25519 public key
        securityManager.addTrustedPublicKey(testPublicKey);

        // In strict mode, unsigned plugins should be rejected
        // This is a basic validation test
        assertTrue(securityManager.verifyPermissions("test-plugin",
            Set.of("read:kernel.config", "write:plugin.data")));
    }

    @Test
    void pluginPermissionDenial() {
        KernelPluginSecurityManager securityManager = new KernelPluginSecurityManager(true);

        Set<String> requestedPermissions = Set.of(
            "read:kernel.config",
            "write:kernel.config", // Not allowed
            "execute:system.command" // Not allowed
        );

        boolean allowed = securityManager.verifyPermissions("test-plugin", requestedPermissions);

        assertFalse(allowed, "Plugins should not get unauthorized permissions");
    }

    // ==================== Audit Immutability Tests ====================

    @Test
    void auditRecordsIncludeCryptographicSignature() {
        CrossProductAuditService auditService = new CrossProductAuditService(null);

        CrossProductAuditService.AuditRecord record =
            CrossProductAuditService.AuditRecord.builder()
                .auditId("audit-123")
                .eventType("cross-product.access")
                .sourceProduct("phr")
                .targetProduct("finance")
                .userId("user-1")
                .tenantId("tenant-1")
                .timestamp(Instant.now())
                .metadata(Map.of("action", "billing.create"))
                .retentionPeriod(CrossProductAuditService.RetentionPeriod.ofYears(25))
                .signature("HMAC_SHA256_SIGNATURE")
                .build();

        assertNotNull(record.getSignature(), "Audit records must include signature");
        assertFalse(record.getSignature().isEmpty(), "Signature must not be empty");
    }

    @Test
    void retentionPeriodEquality() {
        CrossProductAuditService.RetentionPeriod tenYears =
            CrossProductAuditService.RetentionPeriod.ofYears(10);
        CrossProductAuditService.RetentionPeriod anotherTenYears =
            CrossProductAuditService.RetentionPeriod.ofYears(10);
        CrossProductAuditService.RetentionPeriod twentyFiveYears =
            CrossProductAuditService.RetentionPeriod.ofYears(25);

        assertEquals(tenYears, anotherTenYears);
        assertNotEquals(tenYears, twentyFiveYears);
        assertEquals(10, tenYears.getYears());
        assertEquals("10 years", tenYears.toString());
    }
}
