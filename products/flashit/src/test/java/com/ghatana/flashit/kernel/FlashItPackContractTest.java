package com.ghatana.flashit.kernel;

import com.ghatana.flashit.kernel.policy.FlashItBoundaryPolicyStore;
import com.ghatana.flashit.kernel.policy.FlashItComplianceRulePack;
import com.ghatana.flashit.kernel.policy.FlashItPluginBindings;
import com.ghatana.kernel.policy.BoundaryPolicyActionRegistry;
import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyResourceRegistry;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.ProductBoundaryPolicyPackValidator;
import com.ghatana.kernel.policy.ProductBoundaryPolicyValidationProfile;
import com.ghatana.kernel.testing.DeclaredPolicyActionsReader;
import com.ghatana.kernel.testing.DeclaredPolicyResourcesReader;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.compliance.CompliancePlugin;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FlashItPackContract")
class FlashItPackContractTest {

    private static final ProductBoundaryPolicyValidationProfile VALIDATION_PROFILE =
        ProductBoundaryPolicyValidationProfile.builder()
            .productName("FlashIt")
            .rulePrefix("FLASHIT-BP-")
            .defaultDenyRuleId("FLASHIT-BP-999")
            .targetScopePrefix("flashit.")
            .requiredMetadataKeys(Set.of("packVersion", "ruleCategory"))
            .build();
    private static final BoundaryPolicyActionRegistry ACTION_REGISTRY =
        BoundaryPolicyActionRegistry.ofDeclaredActions(
            DeclaredPolicyActionsReader.read(Path.of("domain-pack-manifest.yaml"))
        );
    private static final BoundaryPolicyResourceRegistry RESOURCE_REGISTRY =
        BoundaryPolicyResourceRegistry.ofDeclaredResources(
            DeclaredPolicyResourcesReader.read(Path.of("domain-pack-manifest.yaml"))
        );

    @Test
    void shouldLoadDefaultDenyBoundaryPolicy() {
        FlashItBoundaryPolicyStore store = new FlashItBoundaryPolicyStore();
        List<BoundaryPolicyRule> rules = store.loadRules(BoundaryPolicyLoadContext.global());

        ProductBoundaryPolicyPackValidator.validate(
                rules,
                VALIDATION_PROFILE,
                ACTION_REGISTRY,
                RESOURCE_REGISTRY);
        assertThat(rules).isNotEmpty();
        assertThat(rules.get(rules.size() - 1).getRuleId()).isEqualTo("FLASHIT-BP-999");
        assertThat(rules.get(rules.size() - 1).getEffect()).isEqualTo(BoundaryPolicyRule.Effect.DENY);
        assertThat(rules.get(rules.size() - 1).isRequiresAudit()).isTrue();
    }

    @Test
    void shouldFailClosedForUnsupportedOverrides() {
        FlashItBoundaryPolicyStore store = new FlashItBoundaryPolicyStore();

        assertThatThrownBy(() -> store.loadRules(BoundaryPolicyLoadContext.of("tenant-us", "US")))
                .hasMessageContaining("unsupported");
    }

    @Test
    void shouldExposeManifestAndPolicyPackArtifacts() throws Exception {
        assertThat(Files.readString(Path.of("domain-pack-manifest.yaml"))).contains("FLASHIT-BP-999");
        assertThat(Files.exists(Path.of("policy-packs/flashit-boundary-policy.yaml"))).isTrue();
        assertThat(Files.exists(Path.of("policy-packs/flashit-compliance-rule-pack.yaml"))).isTrue();
    }

    @Test
    void shouldRegisterComplianceRulesThroughPluginBindings() {
        RecordingCompliancePlugin plugin = new RecordingCompliancePlugin();
        FlashItPluginBindings bindings = new FlashItPluginBindings(plugin);

        bindings.registerAll();

        assertThat(plugin.ruleSets).containsKey(FlashItComplianceRulePack.MOMENT_PRIVACY);
        assertThat(plugin.ruleSets.get(FlashItComplianceRulePack.MOMENT_PRIVACY))
                .extracting(CompliancePlugin.ComplianceRule::ruleId)
                .allMatch(ruleId -> ruleId.startsWith("FLASHIT-"));
    }

    private static final class RecordingCompliancePlugin implements CompliancePlugin {
        private final Map<String, List<ComplianceRule>> ruleSets = new LinkedHashMap<>();

        @Override
        public Promise<ComplianceResult> evaluate(String ruleSetId, ComplianceContext context) {
            return Promise.of(new ComplianceResult(true, List.of(), ruleSetId, Instant.now()));
        }

        @Override
        public Promise<Void> registerRuleSet(String ruleSetId, List<ComplianceRule> rules) {
            ruleSets.put(ruleSetId, List.copyOf(rules));
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
                    .id("flashit-pack-contract-plugin")
                    .name("FlashIt Pack Contract Plugin")
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
