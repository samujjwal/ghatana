package com.ghatana.platform.observability.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Executable contract tests for Kernel observability flow contracts.
 *
 * @doc.type class
 * @doc.purpose Executable contract tests for typed observability flow contracts
 * @doc.layer platform
 * @doc.pattern Contract Test
 */
@DisplayName("Observability Flow Contract Tests")
class ObservabilityFlowContractTest {

    private static final Path FLOW_MANIFEST = findRepoFile(
            Path.of("config", "observability", "product-observability-flows.json")
    );

    @Test
    @DisplayName("Should load the canonical JSON manifest through the Java contract")
    void shouldLoadCanonicalJsonManifestThroughJavaContract() throws Exception {
        ObservabilityFlowContract contract = ObservabilityFlowContract.fromManifest(FLOW_MANIFEST);

        assertThat(contract.schemaVersion()).isEqualTo(ObservabilityFlowContract.SUPPORTED_SCHEMA_VERSION);
        assertThat(contract.requiredFacets())
                .containsExactly("trace", "tenantContext", "metrics", "audit", "safeLogging", "redaction");
        assertThat(contract.flows()).isNotEmpty();
    }

    @Test
    @DisplayName("Should validate every canonical flow and evidence entry")
    void shouldValidateEveryCanonicalFlowAndEvidenceEntry() throws Exception {
        ObservabilityFlowContract contract = ObservabilityFlowContract.fromManifest(FLOW_MANIFEST);

        for (ObservabilityFlowContract.Flow flow : contract.flows()) {
            assertThat(flow.product()).isNotBlank();
            assertThat(flow.flow()).isNotBlank();
            assertThat(flow.kind()).isNotNull();
            assertThat(flow.facets()).containsAll(contract.requiredFacets());
            assertThat(flow.evidence()).isNotEmpty();

            for (ObservabilityFlowContract.Evidence evidence : flow.evidence()) {
                assertThat(evidence.file()).isNotBlank();
                if (evidence.type() == ObservabilityFlowContract.EvidenceType.BEHAVIOR) {
                    assertThat(evidence.requiredFacets()).isNotEmpty();
                    assertThat(flow.facets()).containsAll(evidence.requiredFacets());
                    assertThat(evidence.tokens()).isEmpty();
                } else {
                    assertThat(evidence.tokens()).isNotEmpty();
                    assertThat(evidence.requiredFacets()).isEmpty();
                }
            }
        }
    }

    @Test
    @DisplayName("Should validate all observability-required products are covered")
    void shouldValidateAllRequiredProductsAreCovered() throws Exception {
        ObservabilityFlowContract contract = ObservabilityFlowContract.fromManifest(FLOW_MANIFEST);

        Set<String> coveredProducts = contract.flows().stream()
                .map(ObservabilityFlowContract.Flow::product)
                .collect(Collectors.toSet());

        assertThat(coveredProducts)
                .contains("phr", "finance", "digital-marketing", "flashit");
    }

    @Test
    @DisplayName("Should validate at least one bridge flow exists")
    void shouldValidateAtLeastOneBridgeFlowExists() throws Exception {
        ObservabilityFlowContract contract = ObservabilityFlowContract.fromManifest(FLOW_MANIFEST);

        assertThat(contract.flows())
                .anyMatch(flow -> flow.kind() == ObservabilityFlowContract.FlowKind.BRIDGE);
    }

    @Test
    @DisplayName("Should reject unsupported schema versions")
    void shouldRejectUnsupportedSchemaVersions() {
        assertThatThrownBy(() -> new ObservabilityFlowContract(
                "2.0.0",
                java.util.List.of("trace"),
                java.util.List.of(new ObservabilityFlowContract.Flow(
                        "phr",
                        "appointment-create-api",
                        ObservabilityFlowContract.FlowKind.API,
                        java.util.List.of("trace"),
                        java.util.List.of(new ObservabilityFlowContract.Evidence(
                                ObservabilityFlowContract.EvidenceType.SOURCE,
                                "products/phr/src/main/java/com/ghatana/phr/kernel/service/AppointmentService.java",
                                java.util.List.of("correlationId"),
                                null
                        ))
                ))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion must be 1.0.0");
    }

    private static Path findRepoFile(Path relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to find repo file " + relativePath);
    }
}
