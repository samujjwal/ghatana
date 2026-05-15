package com.ghatana.yappc.kernel;

import com.ghatana.yappc.plugin.PluginRegistry;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Compatibility adapter from YAPPC PluginRegistry to narrow Kernel evidence ports.
 *
 * @doc.type class
 * @doc.purpose Keep PluginRegistry inside YAPPC while exposing stable evidence contracts
 * @doc.layer adapter
 * @doc.pattern Adapter
 */
public final class YappcPluginRegistryEvidenceAdapter implements
        YappcProductUnitIntentProvider,
        YappcSemanticArtifactEvidenceProvider,
        YappcArtifactGraphSummaryProvider,
        YappcResidualIslandReportProvider,
        YappcRiskHotspotReportProvider {

    private final PluginRegistry pluginRegistry;

    public YappcPluginRegistryEvidenceAdapter(PluginRegistry pluginRegistry) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "PluginRegistry must not be null");
    }

    @Override
    public Promise<Map<String, Object>> exportProductUnitIntent(String candidateId, Map<String, Object> request) {
        return Promise.of(evidence("product-unit-intent", candidateId, request));
    }

    @Override
    public Promise<Map<String, Object>> semanticArtifactEvidence(String artifactId, Map<String, Object> request) {
        return Promise.of(evidence("semantic-artifact", artifactId, request));
    }

    @Override
    public Promise<Map<String, Object>> artifactGraphSummary(String productUnitId, Map<String, Object> request) {
        return Promise.of(evidence("artifact-graph-summary", productUnitId, request));
    }

    @Override
    public Promise<Map<String, Object>> residualIslandReport(String productUnitId, Map<String, Object> request) {
        return Promise.of(evidence("residual-island-report", productUnitId, request));
    }

    @Override
    public Promise<Map<String, Object>> riskHotspotReport(String productUnitId, Map<String, Object> request) {
        return Promise.of(evidence("risk-hotspot-report", productUnitId, request));
    }

    private Map<String, Object> evidence(String kind, String subjectId, Map<String, Object> request) {
        return Map.of(
            "schemaVersion", "1.0.0",
            "kind", kind,
            "subjectId", Objects.requireNonNull(subjectId, "subjectId cannot be null"),
            "source", "yappc-plugin-registry-adapter",
            "pluginCount", pluginRegistry.getPluginCount(),
            "request", request != null ? request : Map.of(),
            "createdAt", Instant.now().toString()
        );
    }
}
