package com.ghatana.digitalmarketing.pack;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.compliance.CompliancePlugin;
import com.ghatana.plugin.compliance.CompliancePlugin.ComplianceRule;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Product contract tests for DMOS domain packs.
 */
@DisplayName("DigitalMarketingPackContract")
class DigitalMarketingPackContractTest extends EventloopTestBase {

    @Test
    @DisplayName("domain-pack manifest declares DM product code, prefix, and canonical classes")
    void shouldDeclareCanonicalManifestFields() throws Exception {
        String manifest = Files.readString(Path.of("domain-pack.json"));

        assertThat(manifest).contains("\"productCode\": \"DM\"");
        assertThat(manifest).contains("\"rulePrefix\": \"DM-\"");
        assertThat(manifest).contains("DigitalMarketingBoundaryPolicyStore");
        assertThat(manifest).contains("DigitalMarketingPluginBindings");
        assertThat(manifest).contains("DigitalMarketingComplianceRulePack");
    }

    @Test
    @DisplayName("domain-pack manifest rule sets match registered plugin bindings")
    void shouldBindEveryManifestRuleSet() throws Exception {
        String manifest = Files.readString(Path.of("domain-pack.json"));
        Set<String> manifestRuleSets = extractComplianceRuleSets(manifest);

        RecordingCompliancePlugin plugin = new RecordingCompliancePlugin();
        DigitalMarketingPluginBindings bindings = new DigitalMarketingPluginBindings(plugin);

        runPromise(bindings::registerAll);

        assertThat(plugin.registeredRuleSets().keySet()).isEqualTo(manifestRuleSets);
    }

    @Test
    @DisplayName("all compliance rule ids are unique and DM-prefixed")
    void shouldUseUniqueDmPrefixedRuleIds() {
        List<ComplianceRule> allRules = List.of(
            DigitalMarketingComplianceRulePack.marketingIntegrityRules(),
            DigitalMarketingComplianceRulePack.consentLifecycleRules(),
            DigitalMarketingComplianceRulePack.auditTraceabilityRules(),
            DigitalMarketingComplianceRulePack.campaignPreflightRules(),
            DigitalMarketingComplianceRulePack.claimsDisclosuresRules(),
            DigitalMarketingComplianceRulePack.emailComplianceRules(),
            DigitalMarketingComplianceRulePack.connectorExecutionSafetyRules()
        ).stream().flatMap(List::stream).toList();

        List<String> ruleIds = allRules.stream().map(ComplianceRule::ruleId).toList();

        assertThat(ruleIds).allMatch(id -> id.startsWith("DM-"));
        assertThat(ruleIds.stream().distinct().count()).isEqualTo(ruleIds.size());
    }

    private static Set<String> extractComplianceRuleSets(String manifest) {
        Pattern listPattern = Pattern.compile("\\\"complianceRuleSets\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
        Matcher listMatcher = listPattern.matcher(manifest);
        if (!listMatcher.find()) {
            throw new IllegalStateException("complianceRuleSets not found in domain-pack.json");
        }

        String rawList = listMatcher.group(1);
        Pattern itemPattern = Pattern.compile("\\\"([^\\\"]+)\\\"");
        Matcher itemMatcher = itemPattern.matcher(rawList);

        Set<String> items = new java.util.LinkedHashSet<>();
        while (itemMatcher.find()) {
            items.add(itemMatcher.group(1));
        }
        return items;
    }

    private static final class RecordingCompliancePlugin implements CompliancePlugin {
        private final Map<String, List<ComplianceRule>> registeredRuleSets = new LinkedHashMap<>();

        Map<String, List<ComplianceRule>> registeredRuleSets() {
            return registeredRuleSets;
        }

        @Override
        public Promise<ComplianceResult> evaluate(String ruleSetId, ComplianceContext context) {
            return Promise.of(new ComplianceResult(true, List.of(), ruleSetId, Instant.now()));
        }

        @Override
        public Promise<Void> registerRuleSet(String ruleSetId, List<ComplianceRule> rules) {
            registeredRuleSets.put(ruleSetId, List.copyOf(rules));
            return Promise.of(null);
        }

        @Override
        public Promise<Void> addRule(String ruleSetId, ComplianceRule rule) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<AuditEntry>> getAuditTrail(String entityId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<ComplianceViolation>> getActiveViolations(String ruleSetId) {
            return Promise.of(List.of());
        }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("dm-pack-contract-plugin")
                .name("DM Pack Contract Plugin")
                .type(PluginType.CUSTOM)
                .build();
        }

        @Override
        public PluginState getState() {
            return PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> start() {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> stop() {
            return Promise.of(null);
        }
    }
}
