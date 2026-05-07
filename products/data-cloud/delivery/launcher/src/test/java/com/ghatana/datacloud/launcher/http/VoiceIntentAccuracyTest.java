package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Voice Intent Accuracy")
class VoiceIntentAccuracyTest {

    @Test
    @DisplayName("common voice queries resolve with at least 90 percent heuristic accuracy")
    void commonVoiceQueriesResolveWithAtLeastNinetyPercentAccuracy() { 
        List<Map.Entry<String, String>> corpus = List.of( 
            Map.entry("query entities in orders", "query_entities"), 
            Map.entry("get entity 42 from orders", "get_entity"), 
            Map.entry("create entity in orders", "create_entity"), 
            Map.entry("delete entity 42 from orders", "delete_entity"), 
            Map.entry("query events for order created", "query_events"), 
            Map.entry("append event order created", "append_event"), 
            Map.entry("list pipelines for tenant acme", "list_pipelines"), 
            Map.entry("get pipeline status for daily etl", "get_pipeline_status"), 
            Map.entry("list agents for tenant acme", "list_agents"), 
            Map.entry("run analytics query revenue by month", "run_analytics_query"), 
            Map.entry("get workspace spotlight", "get_workspace_spotlight"), 
            Map.entry("search agent memory for churn", "search_agent_memory"), 
            Map.entry("get learning status", "get_learning_status"), 
            Map.entry("trigger learning now", "trigger_learning"), 
            Map.entry("list models", "list_models") 
        );

        long matched = corpus.stream() 
            .filter(sample -> VoiceIntentCatalog.findCandidates(sample.getKey()).stream() 
                .findFirst() 
                .map(intent -> intent.name().equals(sample.getValue())) 
                .orElse(false)) 
            .count(); 

        double accuracy = matched / (double) corpus.size(); 

        assertThat(accuracy).isGreaterThanOrEqualTo(0.90d); 
    }
}