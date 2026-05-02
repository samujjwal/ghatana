package com.ghatana.digitalmarketing.pack;

import com.ghatana.plugin.compliance.CompliancePlugin.ComplianceRule;
import com.ghatana.plugin.compliance.CompliancePlugin.ComplianceRule.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DigitalMarketingComplianceRulePack}.
 *
 * <p>Verifies that each rule pack factory method returns the expected rules with
 * correct IDs, types, severities, and non-blank conditions.</p>
 */
@DisplayName("DigitalMarketingComplianceRulePack")
class DigitalMarketingComplianceRulePackTest {

    @Test
    @DisplayName("marketingIntegrityRules() returns 3 rules with correct IDs and severities")
    void shouldReturnMarketingIntegrityRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.marketingIntegrityRules();

        assertThat(rules).hasSize(3);
        assertThatRuleExists(rules, "MI-001", Severity.HIGH);
        assertThatRuleExists(rules, "MI-002", Severity.MEDIUM);
        assertThatRuleExists(rules, "MI-003", Severity.MEDIUM);
    }

    @Test
    @DisplayName("consentLifecycleRules() returns 4 rules including two CRITICAL rules")
    void shouldReturnConsentLifecycleRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.consentLifecycleRules();

        assertThat(rules).hasSize(4);
        assertThatRuleExists(rules, "CL-001", Severity.CRITICAL);
        assertThatRuleExists(rules, "CL-002", Severity.CRITICAL);
        assertThatRuleExists(rules, "CL-003", Severity.HIGH);
        assertThatRuleExists(rules, "CL-004", Severity.HIGH);
    }

    @Test
    @DisplayName("auditTraceabilityRules() returns 3 HIGH-severity rules")
    void shouldReturnAuditTraceabilityRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.auditTraceabilityRules();

        assertThat(rules).hasSize(3);
        rules.forEach(r -> assertThat(r.severity())
            .as("rule %s should be HIGH", r.ruleId())
            .isEqualTo(Severity.HIGH));
    }

    @Test
    @DisplayName("campaignPreflightRules() returns 4 rules including one CRITICAL")
    void shouldReturnCampaignPreflightRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.campaignPreflightRules();

        assertThat(rules).hasSize(4);
        assertThatRuleExists(rules, "CP-001", Severity.HIGH);
        assertThatRuleExists(rules, "CP-002", Severity.HIGH);
        assertThatRuleExists(rules, "CP-003", Severity.HIGH);
        assertThatRuleExists(rules, "CP-004", Severity.CRITICAL);
    }

    @Test
    @DisplayName("claimsDisclosuresRules() returns 2 rules including one CRITICAL")
    void shouldReturnClaimsDisclosuresRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.claimsDisclosuresRules();

        assertThat(rules).hasSize(2);
        assertThatRuleExists(rules, "CD-001", Severity.HIGH);
        assertThatRuleExists(rules, "CD-002", Severity.CRITICAL);
    }

    @Test
    @DisplayName("emailComplianceRules() returns 4 rules including two CRITICAL")
    void shouldReturnEmailComplianceRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.emailComplianceRules();

        assertThat(rules).hasSize(4);
        assertThatRuleExists(rules, "EC-001", Severity.CRITICAL);
        assertThatRuleExists(rules, "EC-002", Severity.CRITICAL);
        assertThatRuleExists(rules, "EC-003", Severity.HIGH);
        assertThatRuleExists(rules, "EC-004", Severity.HIGH);
    }

    @Test
    @DisplayName("connectorExecutionSafetyRules() returns 3 rules including two CRITICAL")
    void shouldReturnConnectorExecutionSafetyRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.connectorExecutionSafetyRules();

        assertThat(rules).hasSize(3);
        assertThatRuleExists(rules, "CES-001", Severity.CRITICAL);
        assertThatRuleExists(rules, "CES-002", Severity.CRITICAL);
        assertThatRuleExists(rules, "CES-003", Severity.HIGH);
    }

    @Test
    @DisplayName("all rules have non-blank descriptions and conditions")
    void shouldHaveNonBlankDescriptionsAndConditions() {
        List<List<ComplianceRule>> allRuleSets = List.of(
            DigitalMarketingComplianceRulePack.marketingIntegrityRules(),
            DigitalMarketingComplianceRulePack.consentLifecycleRules(),
            DigitalMarketingComplianceRulePack.auditTraceabilityRules(),
            DigitalMarketingComplianceRulePack.campaignPreflightRules(),
            DigitalMarketingComplianceRulePack.claimsDisclosuresRules(),
            DigitalMarketingComplianceRulePack.emailComplianceRules(),
            DigitalMarketingComplianceRulePack.connectorExecutionSafetyRules()
        );

        allRuleSets.stream()
            .flatMap(List::stream)
            .forEach(rule -> {
                assertThat(rule.description())
                    .as("description for rule %s", rule.ruleId())
                    .isNotBlank();
                assertThat(rule.condition())
                    .as("condition for rule %s", rule.ruleId())
                    .isNotBlank();
            });
    }

    @Test
    @DisplayName("all rule IDs across all packs are globally unique")
    void shouldHaveGloballyUniqueRuleIds() {
        long total = 0;
        long distinct = 0;

        List<List<ComplianceRule>> allRuleSets = List.of(
            DigitalMarketingComplianceRulePack.marketingIntegrityRules(),
            DigitalMarketingComplianceRulePack.consentLifecycleRules(),
            DigitalMarketingComplianceRulePack.auditTraceabilityRules(),
            DigitalMarketingComplianceRulePack.campaignPreflightRules(),
            DigitalMarketingComplianceRulePack.claimsDisclosuresRules(),
            DigitalMarketingComplianceRulePack.emailComplianceRules(),
            DigitalMarketingComplianceRulePack.connectorExecutionSafetyRules()
        );

        List<String> allIds = allRuleSets.stream()
            .flatMap(List::stream)
            .map(ComplianceRule::ruleId)
            .toList();

        long distinctCount = allIds.stream().distinct().count();
        assertThat(distinctCount).isEqualTo(allIds.size());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private void assertThatRuleExists(List<ComplianceRule> rules, String ruleId, Severity expectedSeverity) {
        ComplianceRule found = rules.stream()
            .filter(r -> r.ruleId().equals(ruleId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected rule not found: " + ruleId));

        assertThat(found.severity())
            .as("severity for rule %s", ruleId)
            .isEqualTo(expectedSeverity);
    }
}
