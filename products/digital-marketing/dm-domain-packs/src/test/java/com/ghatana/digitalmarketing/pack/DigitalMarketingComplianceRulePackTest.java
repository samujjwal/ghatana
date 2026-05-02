package com.ghatana.digitalmarketing.pack;

import com.ghatana.plugin.compliance.CompliancePlugin.ComplianceRule;
import com.ghatana.plugin.compliance.CompliancePlugin.ComplianceRule.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThatRuleExists(rules, "DM-MI-001", Severity.HIGH);
        assertThatRuleExists(rules, "DM-MI-002", Severity.MEDIUM);
        assertThatRuleExists(rules, "DM-MI-003", Severity.MEDIUM);
    }

    @Test
    @DisplayName("consentLifecycleRules() returns 4 rules including two CRITICAL rules")
    void shouldReturnConsentLifecycleRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.consentLifecycleRules();

        assertThat(rules).hasSize(4);
        assertThatRuleExists(rules, "DM-CL-001", Severity.CRITICAL);
        assertThatRuleExists(rules, "DM-CL-002", Severity.CRITICAL);
        assertThatRuleExists(rules, "DM-CL-003", Severity.HIGH);
        assertThatRuleExists(rules, "DM-CL-004", Severity.HIGH);
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
        assertThatRuleExists(rules, "DM-CP-001", Severity.HIGH);
        assertThatRuleExists(rules, "DM-CP-002", Severity.HIGH);
        assertThatRuleExists(rules, "DM-CP-003", Severity.HIGH);
        assertThatRuleExists(rules, "DM-CP-004", Severity.CRITICAL);
    }

    @Test
    @DisplayName("claimsDisclosuresRules() returns 2 rules including one CRITICAL")
    void shouldReturnClaimsDisclosuresRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.claimsDisclosuresRules();

        assertThat(rules).hasSize(2);
        assertThatRuleExists(rules, "DM-CD-001", Severity.HIGH);
        assertThatRuleExists(rules, "DM-CD-002", Severity.CRITICAL);
    }

    @Test
    @DisplayName("emailComplianceRules() returns 4 rules including two CRITICAL")
    void shouldReturnEmailComplianceRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.emailComplianceRules();

        assertThat(rules).hasSize(4);
        assertThatRuleExists(rules, "DM-EC-001", Severity.CRITICAL);
        assertThatRuleExists(rules, "DM-EC-002", Severity.CRITICAL);
        assertThatRuleExists(rules, "DM-EC-003", Severity.HIGH);
        assertThatRuleExists(rules, "DM-EC-004", Severity.HIGH);
    }

    @Test
    @DisplayName("connectorExecutionSafetyRules() returns 3 rules including two CRITICAL")
    void shouldReturnConnectorExecutionSafetyRules() {
        List<ComplianceRule> rules = DigitalMarketingComplianceRulePack.connectorExecutionSafetyRules();

        assertThat(rules).hasSize(3);
        assertThatRuleExists(rules, "DM-CES-001", Severity.CRITICAL);
        assertThatRuleExists(rules, "DM-CES-002", Severity.CRITICAL);
        assertThatRuleExists(rules, "DM-CES-003", Severity.HIGH);
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

    @Test
    @DisplayName("all rule IDs across all packs use DM- prefix")
    void shouldUseDmPrefixForAllRuleIds() {
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
            .forEach(rule -> assertThat(rule.ruleId())
                .as("ruleId %s should start with DM-", rule.ruleId())
                .startsWith("DM-"));
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
