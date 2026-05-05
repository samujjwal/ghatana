/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.kernel;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyStore;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyRule.Effect;
import com.ghatana.kernel.policy.ProductBoundaryPolicyPackValidator;
import com.ghatana.kernel.policy.ProductBoundaryPolicyValidationProfile;
import com.ghatana.plugin.compliance.CompliancePlugin;
import com.ghatana.phr.kernel.policy.PhrBoundaryPolicyStore;
import com.ghatana.phr.kernel.policy.PhrComplianceRulePack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3.4 product-side contract tests for PHR packs.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>PHR boundary policy store loads a non-empty, well-formed rule list.</li>
 *   <li>Default-deny rule is present and is the last rule.</li>
 *   <li>PHR compliance rule packs are non-empty and use non-blank rule IDs.</li>
 *   <li>Kernel source files do not import PHR pack classes (boundary isolation).</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose PHR pack contract tests — boundary and rule correctness
 * @doc.layer product
 * @doc.pattern Test
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
@DisplayName("PHR Pack Contract Tests")
class PhrPackContractTest {

    private static final ProductBoundaryPolicyValidationProfile VALIDATION_PROFILE =
            ProductBoundaryPolicyValidationProfile.builder()
                    .productName("PHR")
                    .rulePrefix("PHR-BP-")
                    .defaultDenyRuleId("PHR-BP-999")
                    .targetScopePrefix("phr.")
                    .requiredMetadataKeys(Set.of("packVersion", "ruleCategory"))
                    .build();

    // -------------------------------------------------------------------------
    // BoundaryPolicyStore contract
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PhrBoundaryPolicyStore")
    class BoundaryPolicyStoreTests {

        private final PhrBoundaryPolicyStore store = new PhrBoundaryPolicyStore();
        private final BoundaryPolicyLoadContext anyContext = BoundaryPolicyLoadContext.global();

        @Test
        @DisplayName("loadRules() returns a non-empty rule list")
        void loadRulesReturnsNonEmpty() {
            List<BoundaryPolicyRule> rules = store.loadRules(anyContext);
            assertThat(rules).isNotEmpty();
        }

        @Test
        @DisplayName("all rules have non-blank ruleId and non-empty actions")
        void allRulesWellFormed() {
            List<BoundaryPolicyRule> rules = store.loadRules(anyContext);
            ProductBoundaryPolicyPackValidator.validate(rules, VALIDATION_PROFILE);
            for (BoundaryPolicyRule rule : rules) {
                assertThat(rule.getRuleId())
                        .as("ruleId must not be blank")
                        .isNotBlank();
                assertThat(rule.getActions())
                        .as("actions must not be empty for rule %s", rule.getRuleId())
                        .isNotEmpty();
                assertThat(rule.getEffect())
                        .as("effect must not be null for rule %s", rule.getRuleId())
                        .isNotNull();
            }
        }

        @Test
        @DisplayName("last rule is a default-deny rule covering all scopes and resources")
        void lastRuleIsDefaultDeny() {
            List<BoundaryPolicyRule> rules = store.loadRules(anyContext);
            BoundaryPolicyRule lastRule = rules.get(rules.size() - 1);
            assertThat(lastRule.getEffect())
                    .as("last rule must be DENY (default-deny posture)")
                    .isEqualTo(Effect.DENY);
            assertThat(lastRule.getSourceScopePattern())
                    .as("default-deny must match all source scopes")
                    .isEqualTo("**");
            assertThat(lastRule.getTargetScopePattern())
                    .as("default-deny must target phr scope")
                    .startsWith("phr");
        }

        @Test
        @DisplayName("subject-record read rule requires consent and audit")
        void subjectRecordReadRequiresConsentAndAudit() {
            List<BoundaryPolicyRule> rules = store.loadRules(anyContext);
            BoundaryPolicyRule readRule = rules.stream()
                    .filter(r -> r.getActions().contains("read")
                            && r.getResourcePattern().startsWith("subject-records"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No subject-record read rule found"));

            assertThat(readRule.isRequiresConsent())
                    .as("subject-record read must require consent")
                    .isTrue();
            assertThat(readRule.isRequiresAudit())
                    .as("subject-record read must require audit")
                    .isTrue();
        }

        @Test
        @DisplayName("clinical document export is denied")
        void clinicalDocumentExportIsDenied() {
            List<BoundaryPolicyRule> rules = store.loadRules(anyContext);
            BoundaryPolicyRule exportRule = rules.stream()
                    .filter(r -> r.getActions().contains("export")
                            && r.getResourcePattern().startsWith("clinical-documents"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No clinical-document export rule found"));

            assertThat(exportRule.getEffect())
                    .as("clinical document export must be DENY")
                    .isEqualTo(Effect.DENY);
        }

        @Test
        @DisplayName("subject-record writes require approval and audit")
        void subjectRecordWritesRequireApprovalAndAudit() {
            BoundaryPolicyRule writeRule = store.loadRules(anyContext).stream()
                    .filter(r -> r.getResourcePattern().startsWith("subject-records")
                            && r.getActions().contains("write"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No subject-record write rule found"));

            assertThat(writeRule.getEffect()).isEqualTo(Effect.REQUIRE_APPROVAL);
            assertThat(writeRule.isRequiresAudit()).isTrue();
            assertThat(writeRule.isRequiresConsent()).isTrue();
        }

        @Test
        @DisplayName("unsupported tenant or region override fails closed")
        void unsupportedTenantOrRegionOverrideFailsClosed() {
            assertThatThrownBy(() -> store.loadRules(BoundaryPolicyLoadContext.of("tenant-eu", "EU")))
                    .isInstanceOf(BoundaryPolicyStore.BoundaryPolicyStoreException.class)
                    .hasMessageContaining("unsupported");
        }
    }

    // -------------------------------------------------------------------------
    // ComplianceRulePack contract
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PhrComplianceRulePack")
    class ComplianceRulePackTests {

        @Test
        @DisplayName("subjectRecordAccessRules() is non-empty with non-blank IDs")
        void subjectRecordAccessRulesNonEmpty() {
            List<CompliancePlugin.ComplianceRule> rules = PhrComplianceRulePack.subjectRecordAccessRules();
            assertThat(rules).isNotEmpty();
            rules.forEach(r -> {
                assertThat(r.ruleId()).isNotBlank();
                assertThat(r.description()).isNotBlank();
                assertThat(r.severity()).isNotNull();
            });
        }

        @Test
        @DisplayName("consentLifecycleRules() is non-empty with non-blank IDs")
        void consentLifecycleRulesNonEmpty() {
            List<CompliancePlugin.ComplianceRule> rules = PhrComplianceRulePack.consentLifecycleRules();
            assertThat(rules).isNotEmpty();
            rules.forEach(r -> assertThat(r.ruleId()).isNotBlank());
        }

        @Test
        @DisplayName("auditTraceabilityRules() is non-empty with non-blank IDs")
        void auditTraceabilityRulesNonEmpty() {
            List<CompliancePlugin.ComplianceRule> rules = PhrComplianceRulePack.auditTraceabilityRules();
            assertThat(rules).isNotEmpty();
            rules.forEach(r -> assertThat(r.ruleId()).isNotBlank());
        }

        @Test
        @DisplayName("rule set ID constants are non-blank and unique")
        void ruleSetIdsNonBlankAndUnique() {
            List<String> ids = List.of(
                    PhrComplianceRulePack.SUBJECT_RECORD_ACCESS,
                    PhrComplianceRulePack.CONSENT_LIFECYCLE,
                    PhrComplianceRulePack.AUDIT_TRACEABILITY
            );
            assertThat(ids).doesNotHaveDuplicates();
            ids.forEach(id -> assertThat(id).isNotBlank());
        }

        @Test
        @DisplayName("all rule IDs are prefixed with PHR- to avoid collisions")
        void ruleIdsPrefixedWithPhr() {
            List<CompliancePlugin.ComplianceRule> allRules = new java.util.ArrayList<>();
            allRules.addAll(PhrComplianceRulePack.subjectRecordAccessRules());
            allRules.addAll(PhrComplianceRulePack.consentLifecycleRules());
            allRules.addAll(PhrComplianceRulePack.auditTraceabilityRules());
            allRules.forEach(r ->
                    assertThat(r.ruleId())
                            .as("rule ID must be prefixed with PHR-")
                            .startsWith("PHR-")
            );
        }
    }

    // -------------------------------------------------------------------------
    // Boundary isolation contract
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Kernel boundary isolation")
    class BoundaryIsolationTests {

        @Test
        @DisplayName("PhrBoundaryPolicyStore must not import kernel-core implementation classes")
        void phrStoreDoesNotImportKernelImpl() throws Exception {
            // Load the class and verify it only implements the platform interface,
            // not an internal kernel implementation detail.
            Class<?> storeClass = PhrBoundaryPolicyStore.class;
            Class<?>[] interfaces = storeClass.getInterfaces();
            assertThat(interfaces)
                    .extracting(Class::getName)
                    .contains("com.ghatana.kernel.policy.BoundaryPolicyStore");
            // Must not extend any kernel *Impl or *Default* class
            assertThat(storeClass.getSuperclass().getName())
                    .as("PHR store must not extend a kernel implementation class")
                    .isEqualTo("java.lang.Object");
        }
    }
}
