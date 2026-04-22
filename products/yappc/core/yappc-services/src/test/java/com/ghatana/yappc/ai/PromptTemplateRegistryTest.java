package com.ghatana.yappc.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptTemplateRegistry [GH-90000]")
class PromptTemplateRegistryTest {

    @Test
    @DisplayName("returns latest registered version [GH-90000]")
    void returnsLatestVersion() { // GH-90000
        PromptTemplateRegistry registry = new PromptTemplateRegistry(); // GH-90000
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "baseline", "template-v1", 100)); // GH-90000
        registry.register(PromptTemplateVersion.of("intent.capture", "v2", "baseline", "template-v2", 100)); // GH-90000

        PromptTemplateVersion latest = registry.latest("intent.capture [GH-90000]").orElseThrow();
        assertThat(latest.version()).isEqualTo("v2 [GH-90000]");
    }

    @Test
    @DisplayName("selectForExperiment is deterministic for same subject and experiment [GH-90000]")
    void selectForExperimentDeterministic() { // GH-90000
        PromptTemplateRegistry registry = new PromptTemplateRegistry(); // GH-90000
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "baseline", "a", 50)); // GH-90000
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "concise", "b", 50)); // GH-90000

        PromptTemplateVersion first = registry
                .selectForExperiment("intent.capture", "v1", "tenant-a", "exp-1") // GH-90000
                .orElseThrow(); // GH-90000
        PromptTemplateVersion second = registry
                .selectForExperiment("intent.capture", "v1", "tenant-a", "exp-1") // GH-90000
                .orElseThrow(); // GH-90000

        assertThat(first.variant()).isEqualTo(second.variant()); // GH-90000
    }

    @Test
    @DisplayName("renders template variables [GH-90000]")
    void rendersVariables() { // GH-90000
        PromptTemplateRegistry registry = new PromptTemplateRegistry(); // GH-90000
        PromptTemplateVersion template = PromptTemplateVersion.of( // GH-90000
                "intent.capture",
                "v1",
                "baseline",
                "Idea: ${rawText}",
                100);

        String rendered = registry.render(template, Map.of("rawText", "Build a compliance dashboard")); // GH-90000
        assertThat(rendered).isEqualTo("Idea: Build a compliance dashboard [GH-90000]");
    }

    @Test
    @DisplayName("selectForActiveExperiment uses active version and supports rollback [GH-90000]")
    void activeVersionSelectionAndRollback() { // GH-90000
        PromptTemplateRegistry registry = new PromptTemplateRegistry(); // GH-90000
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "baseline", "v1", 100)); // GH-90000
        registry.register(PromptTemplateVersion.of("intent.capture", "v2", "baseline", "v2", 100)); // GH-90000

        registry.setActiveVersion("intent.capture", "v2"); // GH-90000
        PromptTemplateVersion active = registry
                .selectForActiveExperiment("intent.capture", "tenant-a", "exp-1") // GH-90000
                .orElseThrow(); // GH-90000

        assertThat(active.version()).isEqualTo("v2 [GH-90000]");
        assertThat(registry.rollbackToVersion("intent.capture", "v1")).isTrue(); // GH-90000

        PromptTemplateVersion rolledBack = registry
                .selectForActiveExperiment("intent.capture", "tenant-a", "exp-1") // GH-90000
                .orElseThrow(); // GH-90000
        assertThat(rolledBack.version()).isEqualTo("v1 [GH-90000]");
    }

    @Test
    @DisplayName("rebalanceVariantWeights adjusts experiment weights using recorded scores [GH-90000]")
    void rebalancesVariantWeights() { // GH-90000
        PromptTemplateRegistry registry = new PromptTemplateRegistry(); // GH-90000
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "baseline", "a", 50)); // GH-90000
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "concise", "b", 50)); // GH-90000

        registry.recordVariantScore("intent.capture", "v1", "baseline", 0.9); // GH-90000
        registry.recordVariantScore("intent.capture", "v1", "baseline", 0.8); // GH-90000
        registry.recordVariantScore("intent.capture", "v1", "concise", 0.2); // GH-90000
        registry.recordVariantScore("intent.capture", "v1", "concise", 0.3); // GH-90000

        boolean rebalanced = registry.rebalanceVariantWeights("intent.capture", "v1", 2); // GH-90000
        assertThat(rebalanced).isTrue(); // GH-90000

        PromptTemplateVersion baseline = registry.find("intent.capture", "v1", "baseline").orElseThrow(); // GH-90000
        PromptTemplateVersion concise = registry.find("intent.capture", "v1", "concise").orElseThrow(); // GH-90000
        assertThat(baseline.weight()).isGreaterThan(concise.weight()); // GH-90000
    }
}