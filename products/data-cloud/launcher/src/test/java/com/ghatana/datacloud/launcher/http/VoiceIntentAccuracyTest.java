package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Voice Intent Accuracy [GH-90000]")
class VoiceIntentAccuracyTest {

    @Test
    @DisplayName("common voice queries resolve with at least 90 percent heuristic accuracy [GH-90000]")
    void commonVoiceQueriesResolveWithAtLeastNinetyPercentAccuracy() { // GH-90000
        List<Map.Entry<String, String>> corpus = List.of( // GH-90000
            Map.entry("query entities in orders", "query_entities"), // GH-90000
            Map.entry("get entity 42 from orders", "get_entity"), // GH-90000
            Map.entry("create entity in orders", "create_entity"), // GH-90000
            Map.entry("delete entity 42 from orders", "delete_entity"), // GH-90000
            Map.entry("query events for order created", "query_events"), // GH-90000
            Map.entry("append event order created", "append_event"), // GH-90000
            Map.entry("list pipelines for tenant acme", "list_pipelines"), // GH-90000
            Map.entry("get pipeline status for daily etl", "get_pipeline_status"), // GH-90000
            Map.entry("list agents for tenant acme", "list_agents"), // GH-90000
            Map.entry("run analytics query revenue by month", "run_analytics_query"), // GH-90000
            Map.entry("get workspace spotlight", "get_workspace_spotlight"), // GH-90000
            Map.entry("search agent memory for churn", "search_agent_memory"), // GH-90000
            Map.entry("get learning status", "get_learning_status"), // GH-90000
            Map.entry("trigger learning now", "trigger_learning"), // GH-90000
            Map.entry("list models", "list_models") // GH-90000
        );

        long matched = corpus.stream() // GH-90000
            .filter(sample -> VoiceIntentCatalog.findCandidates(sample.getKey()).stream() // GH-90000
                .findFirst() // GH-90000
                .map(intent -> intent.name().equals(sample.getValue())) // GH-90000
                .orElse(false)) // GH-90000
            .count(); // GH-90000

        double accuracy = matched / (double) corpus.size(); // GH-90000

        assertThat(accuracy).isGreaterThanOrEqualTo(0.90d); // GH-90000
    }
}