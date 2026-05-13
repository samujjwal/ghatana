package com.ghatana.platform.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MutationMetadataEnricher")
class MutationMetadataEnricherTest {

    @Test
    @DisplayName("normalizes product operation names and delegates trace metadata")
    void enrichesTraceMetadata() {
        MutationMetadataEnricher enricher = MutationMetadataEnricher.builder("finance")
            .correlationIdFactory(operation -> "corr-" + operation)
            .traceMetadataProvider((correlationId, operation, existingMetadata) -> Map.of(
                "correlation_id", correlationId,
                "trace_operation", operation,
                "existing_tenant", existingMetadata.get("tenantId")
            ))
            .build();
        Map<String, String> metadata = new HashMap<>(Map.of("tenantId", "tenant-1"));

        enricher.enrich(metadata, "CREATE", "record-1", "TradeOrder");

        assertThat(metadata)
            .containsEntry("correlation_id", "corr-finance_trade-order_create")
            .containsEntry("trace_operation", "finance_trade-order_create")
            .containsEntry("existing_tenant", "tenant-1");
    }

    @Test
    @DisplayName("uses existing snake-case correlation id when configured")
    void usesSnakeCaseCorrelationId() {
        MutationMetadataEnricher enricher = MutationMetadataEnricher.builder("finance")
            .keyStyle(MutationMetadataEnricher.MetadataKeyStyle.SNAKE_CASE)
            .correlationIdFactory(operation -> "generated-" + operation)
            .traceMetadataProvider((correlationId, operation, existingMetadata) -> Map.of(
                "correlation_id", correlationId,
                "trace_operation", operation
            ))
            .build();
        Map<String, String> metadata = new HashMap<>(Map.of("correlation_id", "provided-corr"));

        enricher.enrich(metadata, "update", "record-1", "ledger");

        assertThat(metadata).containsEntry("correlation_id", "provided-corr");
    }

    @Test
    @DisplayName("infers owner scope from configured product owner fields")
    void infersOwnerScope() {
        ProductDataServiceBase.OwnerScopeStrategy strategy = MutationMetadataEnricher.ownerScopeStrategy(
            "patientId",
            "subjectId"
        );

        assertThat(strategy.inferOwnerScope(Map.of("patientId", "patient-1"), "note", "note-1"))
            .isEqualTo("note:patient-1");
        assertThat(strategy.inferOwnerScope(Map.of(), "note", "note-1"))
            .isEqualTo("note:note-1");
    }

    @Test
    @DisplayName("rejects blank product prefixes")
    void rejectsBlankProductPrefix() {
        assertThatThrownBy(() -> MutationMetadataEnricher.builder(" ").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("productPrefix");
    }
}
