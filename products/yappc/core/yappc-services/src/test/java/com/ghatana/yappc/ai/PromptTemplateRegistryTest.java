package com.ghatana.yappc.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptTemplateRegistry")
class PromptTemplateRegistryTest {

    @Test
    @DisplayName("returns latest registered version")
    void returnsLatestVersion() { 
        PromptTemplateRegistry registry = new PromptTemplateRegistry(); 
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "baseline", "template-v1", 100)); 
        registry.register(PromptTemplateVersion.of("intent.capture", "v2", "baseline", "template-v2", 100)); 

        PromptTemplateVersion latest = registry.latest("intent.capture").orElseThrow();
        assertThat(latest.version()).isEqualTo("v2");
    }

    @Test
    @DisplayName("selectForExperiment is deterministic for same subject and experiment")
    void selectForExperimentDeterministic() { 
        PromptTemplateRegistry registry = new PromptTemplateRegistry(); 
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "baseline", "a", 50)); 
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "concise", "b", 50)); 

        PromptTemplateVersion first = registry
                .selectForExperiment("intent.capture", "v1", "tenant-a", "exp-1") 
                .orElseThrow(); 
        PromptTemplateVersion second = registry
                .selectForExperiment("intent.capture", "v1", "tenant-a", "exp-1") 
                .orElseThrow(); 

        assertThat(first.variant()).isEqualTo(second.variant()); 
    }

    @Test
    @DisplayName("renders template variables")
    void rendersVariables() { 
        PromptTemplateRegistry registry = new PromptTemplateRegistry(); 
        PromptTemplateVersion template = PromptTemplateVersion.of( 
                "intent.capture",
                "v1",
                "baseline",
                "Idea: ${rawText}",
                100);

        String rendered = registry.render(template, Map.of("rawText", "Build a compliance dashboard")); 
        assertThat(rendered).isEqualTo("Idea: Build a compliance dashboard");
    }

    @Test
    @DisplayName("selectForActiveExperiment uses active version and supports rollback")
    void activeVersionSelectionAndRollback() { 
        PromptTemplateRegistry registry = new PromptTemplateRegistry(); 
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "baseline", "v1", 100)); 
        registry.register(PromptTemplateVersion.of("intent.capture", "v2", "baseline", "v2", 100)); 

        registry.setActiveVersion("intent.capture", "v2"); 
        PromptTemplateVersion active = registry
                .selectForActiveExperiment("intent.capture", "tenant-a", "exp-1") 
                .orElseThrow(); 

        assertThat(active.version()).isEqualTo("v2");
        assertThat(registry.rollbackToVersion("intent.capture", "v1")).isTrue(); 

        PromptTemplateVersion rolledBack = registry
                .selectForActiveExperiment("intent.capture", "tenant-a", "exp-1") 
                .orElseThrow(); 
        assertThat(rolledBack.version()).isEqualTo("v1");

        assertThat(registry.previousActiveVersion("intent.capture")).contains("v2");
        assertThat(registry.rollbackToPreviousVersion("intent.capture")).contains("v2");
        PromptTemplateVersion previousRollback = registry
            .selectForActiveExperiment("intent.capture", "tenant-a", "exp-1")
            .orElseThrow();
        assertThat(previousRollback.version()).isEqualTo("v2");
    }

    @Test
    @DisplayName("rebalanceVariantWeights adjusts experiment weights using recorded scores")
    void rebalancesVariantWeights() { 
        PromptTemplateRegistry registry = new PromptTemplateRegistry(); 
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "baseline", "a", 50)); 
        registry.register(PromptTemplateVersion.of("intent.capture", "v1", "concise", "b", 50)); 

        registry.recordVariantScore("intent.capture", "v1", "baseline", 0.9); 
        registry.recordVariantScore("intent.capture", "v1", "baseline", 0.8); 
        registry.recordVariantScore("intent.capture", "v1", "concise", 0.2); 
        registry.recordVariantScore("intent.capture", "v1", "concise", 0.3); 

        boolean rebalanced = registry.rebalanceVariantWeights("intent.capture", "v1", 2); 
        assertThat(rebalanced).isTrue(); 

        PromptTemplateVersion baseline = registry.find("intent.capture", "v1", "baseline").orElseThrow(); 
        PromptTemplateVersion concise = registry.find("intent.capture", "v1", "concise").orElseThrow(); 
        assertThat(baseline.weight()).isGreaterThan(concise.weight()); 
    }
}