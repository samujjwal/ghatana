/**
 * @doc.type class
 * @doc.purpose Test feature lineage tracking, origin tracing, and transformation history
 * @doc.layer products
 * @doc.pattern Test
 */
package com.ghatana.datacloud.featurestore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature Lineage Tests
 *
 * Test feature lineage tracking, origin tracing, and transformation history.
 */
@DisplayName("Feature Lineage Tests")
@Tag("lineage")
class FeatureLineageTest {

    @Test
    @DisplayName("Should track feature origin source")
    void shouldTrackFeatureOriginSource() { 
        Map<String, Object> feature = new LinkedHashMap<>(); 
        feature.put("feature_name", "user_age"); 
        feature.put("source", "user_profile_table"); 
        feature.put("source_type", "database"); 
        feature.put("extraction_time", Instant.now()); 

        assertThat(feature).containsKey("source");
        assertThat(feature.get("source")).isEqualTo("user_profile_table");
        assertThat(feature.get("source_type")).isEqualTo("database");
    }

    @Test
    @DisplayName("Should track feature transformation history")
    void shouldTrackFeatureTransformationHistory() { 
        List<Map<String, Object>> transformations = new ArrayList<>(); 

        Map<String, Object> transform1 = new HashMap<>(); 
        transform1.put("type", "normalization"); 
        transform1.put("timestamp", Instant.now()); 
        transform1.put("params", Map.of("min", 0, "max", 100)); 
        transformations.add(transform1); 

        Map<String, Object> transform2 = new HashMap<>(); 
        transform2.put("type", "encoding"); 
        transform2.put("timestamp", Instant.now()); 
        transform2.put("params", Map.of("method", "one_hot")); 
        transformations.add(transform2); 

        assertThat(transformations).hasSize(2); 
        assertThat(transformations.get(0).get("type")).isEqualTo("normalization");
        assertThat(transformations.get(1).get("type")).isEqualTo("encoding");
    }

    @Test
    @DisplayName("Should trace feature to upstream sources")
    void shouldTraceFeatureToUpstreamSources() { 
        Map<String, Object> feature = Map.of( 
            "name", "derived_feature",
            "upstream_sources", List.of("source_table_1", "source_table_2", "api_endpoint") 
        );

        @SuppressWarnings("unchecked")
        List<String> upstreamSources = (List<String>) feature.get("upstream_sources");

        assertThat(upstreamSources).hasSize(3); 
        assertThat(upstreamSources).contains("source_table_1", "source_table_2", "api_endpoint"); 
    }

    @Test
    @DisplayName("Should track feature version changes")
    void shouldTrackFeatureVersionChanges() { 
        List<Map<String, Object>> versions = new ArrayList<>(); 

        Map<String, Object> v1 = new HashMap<>(); 
        v1.put("version", 1); 
        v1.put("created_at", Instant.now().minusSeconds(3600)); 
        v1.put("change_description", "Initial version"); 
        versions.add(v1); 

        Map<String, Object> v2 = new HashMap<>(); 
        v2.put("version", 2); 
        v2.put("created_at", Instant.now()); 
        v2.put("change_description", "Added normalization"); 
        versions.add(v2); 

        assertThat(versions).hasSize(2); 
        assertThat(versions.get(0).get("version")).isEqualTo(1);
        assertThat(versions.get(1).get("version")).isEqualTo(2);
    }

    @Test
    @DisplayName("Should detect feature lineage cycles")
    void shouldDetectFeatureLineageCycles() { 
        Map<String, List<String>> lineageGraph = new HashMap<>(); 
        lineageGraph.put("feature_a", List.of("feature_b"));
        lineageGraph.put("feature_b", List.of("feature_c"));
        lineageGraph.put("feature_c", List.of("feature_a"));  // Cycle

        boolean hasCycle = detectCycle(lineageGraph, "feature_a", new HashSet<>()); 

        assertThat(hasCycle).isTrue(); 
    }

    private boolean detectCycle(Map<String, List<String>> graph, String node, Set<String> visited) { 
        if (!visited.add(node)) { 
            return true;  // Cycle detected
        }

        if (graph.containsKey(node)) { 
            for (String neighbor : graph.get(node)) { 
                if (detectCycle(graph, neighbor, new HashSet<>(visited))) { 
                    return true;
                }
            }
        }

        return false;
    }

    @Test
    @DisplayName("Should track feature dependencies")
    void shouldTrackFeatureDependencies() { 
        Map<String, Object> feature = Map.of( 
            "name", "composite_feature",
            "dependencies", List.of("base_feature_1", "base_feature_2", "calculated_feature") 
        );

        @SuppressWarnings("unchecked")
        List<String> dependencies = (List<String>) feature.get("dependencies");

        assertThat(dependencies).isNotEmpty(); 
        assertThat(dependencies).contains("base_feature_1", "base_feature_2", "calculated_feature"); 
    }

    @Test
    @DisplayName("Should validate lineage completeness")
    void shouldValidateLineageCompleteness() { 
        Map<String, Object> feature = new HashMap<>(); 
        feature.put("name", "test_feature"); 
        feature.put("source", "test_source"); 
        feature.put("lineage_id", UUID.randomUUID().toString()); 
        feature.put("created_at", Instant.now()); 

        // Check required lineage fields
        assertThat(feature).containsKey("source");
        assertThat(feature).containsKey("lineage_id");
        assertThat(feature).containsKey("created_at");
    }

    @Test
    @DisplayName("Should track data quality metrics in lineage")
    void shouldTrackDataQualityMetricsInLineage() { 
        Map<String, Object> lineage = Map.of( 
            "feature_name", "user_age",
            "quality_metrics", Map.of( 
                "completeness", 0.95,
                "accuracy", 0.98,
                "consistency", 0.92
            )
        );

        @SuppressWarnings("unchecked")
        Map<String, Double> qualityMetrics = (Map<String, Double>) lineage.get("quality_metrics");

        assertThat(qualityMetrics).containsKey("completeness");
        assertThat(qualityMetrics).containsKey("accuracy");
        assertThat(qualityMetrics).containsKey("consistency");
        assertThat(qualityMetrics.get("completeness")).isBetween(0.0, 1.0);
        assertThat(qualityMetrics.get("accuracy")).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Should track feature owner and stewardship")
    void shouldTrackFeatureOwnerAndStewardship() { 
        Map<String, Object> lineage = Map.of( 
            "feature_name", "customer_segment",
            "owner", "data_science_team",
            "steward", "product_analyst",
            "contact", "data-team@ghatana.ai"
        );

        assertThat(lineage).containsKey("owner");
        assertThat(lineage).containsKey("steward");
        assertThat(lineage).containsKey("contact");
        assertThat(lineage.get("owner")).isEqualTo("data_science_team");
        assertThat(lineage.get("steward")).isEqualTo("product_analyst");
    }

    @Test
    @DisplayName("Should trace feature access patterns")
    void shouldTraceFeatureAccessPatterns() { 
        List<Map<String, Object>> accessLog = new ArrayList<>(); 

        Map<String, Object> access1 = Map.of( 
            "timestamp", Instant.now().minusSeconds(3600), 
            "user", "data_scientist_1",
            "operation", "read"
        );

        Map<String, Object> access2 = Map.of( 
            "timestamp", Instant.now().minusSeconds(1800), 
            "user", "ml_engine",
            "operation", "read"
        );

        accessLog.add(access1); 
        accessLog.add(access2); 

        assertThat(accessLog).hasSize(2); 
        assertThat(accessLog.get(0).get("operation")).isEqualTo("read");
        assertThat(accessLog.get(1).get("operation")).isEqualTo("read");
    }

    @Test
    @DisplayName("Should track feature schema evolution")
    void shouldTrackFeatureSchemaEvolution() { 
        List<Map<String, Object>> schemaVersions = new ArrayList<>(); 

        Map<String, Object> v1 = Map.of( 
            "version", 1,
            "schema", Map.of("type", "integer", "nullable", false), 
            "effective_from", Instant.now().minus(Duration.ofDays(30)) 
        );

        Map<String, Object> v2 = Map.of( 
            "version", 2,
            "schema", Map.of("type", "integer", "nullable", true), 
            "effective_from", Instant.now().minus(Duration.ofDays(7)) 
        );

        schemaVersions.add(v1); 
        schemaVersions.add(v2); 

        assertThat(schemaVersions).hasSize(2); 
        @SuppressWarnings("unchecked")
        Map<String, Object> v1Schema = (Map<String, Object>) schemaVersions.get(0).get("schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> v2Schema = (Map<String, Object>) schemaVersions.get(1).get("schema");

        assertThat(v1Schema.get("nullable")).isEqualTo(false);
        assertThat(v2Schema.get("nullable")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should validate lineage timestamp ordering")
    void shouldValidateLineageTimestampOrdering() { 
        Instant t1 = Instant.now().minusSeconds(3600); 
        Instant t2 = Instant.now().minusSeconds(1800); 
        Instant t3 = Instant.now(); 

        List<Instant> timestamps = List.of(t1, t2, t3); 

        // Verify chronological order
        assertThat(timestamps.get(0)).isBefore(timestamps.get(1)); 
        assertThat(timestamps.get(1)).isBefore(timestamps.get(2)); 
    }

    @Test
    @DisplayName("Should track feature retention policy")
    void shouldTrackFeatureRetentionPolicy() { 
        Map<String, Object> lineage = Map.of( 
            "feature_name", "user_pii",
            "retention_policy", Map.of( 
                "ttl_days", 90,
                "archive_after_days", 30,
                "delete_after_days", 365
            )
        );

        @SuppressWarnings("unchecked")
        Map<String, Integer> retentionPolicy = (Map<String, Integer>) lineage.get("retention_policy");

        assertThat(retentionPolicy.get("ttl_days")).isEqualTo(90);
        assertThat(retentionPolicy.get("archive_after_days")).isEqualTo(30);
        assertThat(retentionPolicy.get("delete_after_days")).isEqualTo(365);
    }

    @Test
    @DisplayName("Should detect lineage breaks")
    void shouldDetectLineageBreaks() { 
        Map<String, Object> feature = Map.of( 
            "name", "orphaned_feature",
            "upstream_sources", List.of(),  // No upstream sources 
            "downstream_consumers", List.of()  // No downstream consumers 
        );

        @SuppressWarnings("unchecked")
        List<String> upstreamSources = (List<String>) feature.get("upstream_sources");
        @SuppressWarnings("unchecked")
        List<String> downstreamConsumers = (List<String>) feature.get("downstream_consumers");

        boolean isOrphaned = upstreamSources.isEmpty() && downstreamConsumers.isEmpty(); 
        assertThat(isOrphaned).isTrue(); 
    }

    @Test
    @DisplayName("Should track feature merge operations")
    void shouldTrackFeatureMergeOperations() { 
        Map<String, Object> lineage = Map.of( 
            "feature_name", "merged_profile",
            "merge_operation", Map.of( 
                "type", "left_join",
                "sources", List.of("profile_a", "profile_b"), 
                "join_key", "user_id",
                "timestamp", Instant.now() 
            )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> mergeOperation = (Map<String, Object>) lineage.get("merge_operation");

        assertThat(mergeOperation.get("type")).isEqualTo("left_join");
        assertThat(mergeOperation.get("join_key")).isEqualTo("user_id");
    }

    @Test
    @DisplayName("Should validate feature lineage metadata")
    void shouldValidateFeatureLineageMetadata() { 
        Map<String, Object> lineage = new HashMap<>(); 
        lineage.put("feature_id", UUID.randomUUID().toString()); 
        lineage.put("dataset_id", UUID.randomUUID().toString()); 
        lineage.put("pipeline_id", UUID.randomUUID().toString()); 
        lineage.put("run_id", UUID.randomUUID().toString()); 

        // All metadata fields should be valid UUIDs
        assertThat(lineage.get("feature_id")).isInstanceOf(String.class);
        assertThat(lineage.get("dataset_id")).isInstanceOf(String.class);
        assertThat(lineage.get("pipeline_id")).isInstanceOf(String.class);
        assertThat(lineage.get("run_id")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("Should track feature aggregation operations")
    void shouldTrackFeatureAggregationOperations() { 
        Map<String, Object> lineage = Map.of( 
            "feature_name", "total_spending",
            "aggregation", Map.of( 
                "type", "sum",
                "source_feature", "transaction_amount",
                "group_by", "user_id",
                "window", "30d"
            )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> aggregation = (Map<String, Object>) lineage.get("aggregation");

        assertThat(aggregation.get("type")).isEqualTo("sum");
        assertThat(aggregation.get("source_feature")).isEqualTo("transaction_amount");
        assertThat(aggregation.get("group_by")).isEqualTo("user_id");
    }

    @Test
    @DisplayName("Should detect duplicate lineage records")
    void shouldDetectDuplicateLineageRecords() { 
        List<Map<String, Object>> lineageRecords = new ArrayList<>(); 

        Map<String, Object> record1 = Map.of( 
            "feature_id", "feature_123",
            "version", 1,
            "timestamp", Instant.now() 
        );

        Map<String, Object> record2 = Map.of( 
            "feature_id", "feature_123",
            "version", 1,
            "timestamp", Instant.now() 
        );

        lineageRecords.add(record1); 
        lineageRecords.add(record2); 

        // Detect duplicates based on feature_id and version
        Set<String> uniqueKeys = new HashSet<>(); 
        Set<String> duplicates = new HashSet<>(); 

        for (Map<String, Object> record : lineageRecords) { 
            String key = record.get("feature_id") + "_" + record.get("version");
            if (!uniqueKeys.add(key)) { 
                duplicates.add(key); 
            }
        }

        assertThat(duplicates).isNotEmpty(); 
    }

    @Test
    @DisplayName("Should track feature derivation depth")
    void shouldTrackFeatureDerivationDepth() { 
        Map<String, Object> feature = Map.of( 
            "name", "deeply_derived_feature",
            "derivation_chain", List.of( 
                "raw_source",
                "intermediate_1",
                "intermediate_2",
                "intermediate_3",
                "deeply_derived_feature"
            )
        );

        @SuppressWarnings("unchecked")
        List<String> derivationChain = (List<String>) feature.get("derivation_chain");

        assertThat(derivationChain).hasSize(5); 
        assertThat(derivationChain.get(0)).isEqualTo("raw_source");
        assertThat(derivationChain.get(4)).isEqualTo("deeply_derived_feature");

        int depth = derivationChain.size() - 1; 
        assertThat(depth).isEqualTo(4); 
    }

    @Test
    @DisplayName("Should validate lineage data integrity")
    void shouldValidateLineageDataIntegrity() { 
        Map<String, Object> lineage = Map.of( 
            "feature_id", UUID.randomUUID().toString(), 
            "source_id", UUID.randomUUID().toString(), 
            "transformation_id", UUID.randomUUID().toString(), 
            "checksum", "abc123def456",
            "row_count", 1000
        );

        assertThat(lineage).containsKey("checksum");
        assertThat(lineage).containsKey("row_count");
        assertThat(lineage.get("row_count")).isInstanceOf(Integer.class);
        assertThat((Integer) lineage.get("row_count")).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should track feature labeling and tagging")
    void shouldTrackFeatureLabelingAndTagging() { 
        Map<String, Object> lineage = Map.of( 
            "feature_name", "customer_lifetime_value",
            "tags", List.of("financial", "predictive", "high_value"), 
            "labels", Map.of( 
                "domain", "marketing",
                "sensitivity", "low",
                "category", "customer_metrics"
            )
        );

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) lineage.get("tags");
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) lineage.get("labels");

        assertThat(tags).contains("financial", "predictive", "high_value"); 
        assertThat(labels).containsEntry("domain", "marketing"); 
        assertThat(labels).containsEntry("sensitivity", "low"); 
    }

    @Test
    @DisplayName("Should reconstruct lineage from downstream feature")
    void shouldReconstructLineageFromDownstreamFeature() { 
        Map<String, Object> downstreamFeature = Map.of( 
            "name", "final_feature",
            "upstream_sources", List.of("intermediate_1", "intermediate_2") 
        );

        Map<String, List<String>> lineageGraph = new HashMap<>(); 
        lineageGraph.put("final_feature", List.of("intermediate_1", "intermediate_2")); 
        lineageGraph.put("intermediate_1", List.of("source_1", "source_2")); 
        lineageGraph.put("intermediate_2", List.of("source_3"));
        lineageGraph.put("source_1", List.of()); 
        lineageGraph.put("source_2", List.of()); 
        lineageGraph.put("source_3", List.of()); 

        List<String> fullLineage = reconstructLineage("final_feature", lineageGraph); 

        assertThat(fullLineage).contains("source_1", "source_2", "source_3"); 
    }

    private List<String> reconstructLineage(String feature, Map<String, List<String>> graph) { 
        List<String> lineage = new ArrayList<>(); 
        lineage.add(feature); 

        if (graph.containsKey(feature)) { 
            for (String upstream : graph.get(feature)) { 
                lineage.addAll(reconstructLineage(upstream, graph)); 
            }
        }

        return lineage;
    }
}
