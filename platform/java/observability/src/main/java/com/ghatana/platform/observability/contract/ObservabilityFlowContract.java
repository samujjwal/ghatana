package com.ghatana.platform.observability.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Kernel observability flow contract.
 * <p>
 * Defines the required observability flows, facets, and evidence for all products.
 * This contract is validated by executable contract tests instead of token-scanning.
 * </p>
 *
 * @doc.type class
 * @doc.purpose Kernel observability flow contract
 * @doc.layer platform
 * @doc.pattern Contract
 */
public final class ObservabilityFlowContract {

    private final List<String> requiredFacets;
    private final List<Flow> flows;

    public ObservabilityFlowContract() {
        this.requiredFacets = List.of(
            "logging",
            "tracing",
            "metrics",
            "audit-trail"
        );
        this.flows = loadFlows();
    }

    public List<String> getRequiredFacets() {
        return requiredFacets;
    }

    public List<Flow> getFlows() {
        return flows;
    }

    private List<Flow> loadFlows() {
        List<Flow> flows = new ArrayList<>();
        
        // PHR flows
        flows.add(new Flow(
            "phr",
            "fhir-api",
            "api",
            List.of("logging", "tracing", "metrics"),
            List.of(
                new Evidence("products/phr/src/main/java/com/ghatana/phr/api/PhrHttpServer.java", 
                    List.of("PhrHttpServer", "entitlementContext", "roleEvaluator"))
            )
        ));
        
        // Digital Marketing flows
        flows.add(new Flow(
            "digital-marketing",
            "dm-api",
            "api",
            List.of("logging", "tracing", "metrics"),
            List.of(
                new Evidence("products/digital-marketing/dm-api/src/main/java/com/ghatana/digitalmarketing/api/DmosApiServer.java",
                    List.of("DmosApiServer", "DmosObservability", "wireObservability"))
            )
        ));
        
        // Finance flows
        flows.add(new Flow(
            "finance",
            "transaction-processing",
            "worker",
            List.of("logging", "tracing", "metrics", "audit-trail"),
            List.of(
                new Evidence("products/finance/src/main/java/com/ghatana/finance/service/TransactionService.java",
                    List.of("outcomeMetadata", "tenant_id", "transaction_id", "audit_classification"))
            )
        ));
        
        // FlashIt flows
        flows.add(new Flow(
            "flashit",
            "gateway-api",
            "api",
            List.of("logging", "tracing", "metrics"),
            List.of(
                new Evidence("products/flashit/backend/gateway/src/server.ts",
                    List.of("registerLoggerPlugin", "registerTracingMiddleware", "metricsPlugin"))
            )
        ));
        
        // Bridge flow (Kernel bridge for Digital Marketing)
        flows.add(new Flow(
            "digital-marketing",
            "kernel-bridge",
            "bridge",
            List.of("logging", "tracing", "metrics", "audit-trail"),
            List.of(
                new Evidence("products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java",
                    List.of("shouldRecordAuditWithContextAttributes", "workspaceId", "correlationId", "tenantId"))
            )
        ));
        
        return flows;
    }

    public record Flow(
        String product,
        String flow,
        String kind,
        List<String> facets,
        List<Evidence> evidence
    ) {
        public Flow {
            Objects.requireNonNull(product, "product must not be null");
            Objects.requireNonNull(flow, "flow must not be null");
            Objects.requireNonNull(kind, "kind must not be null");
            Objects.requireNonNull(facets, "facets must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
        }
    }

    public record Evidence(
        String file,
        List<String> tokens
    ) {
        public Evidence {
            Objects.requireNonNull(file, "file must not be null");
            Objects.requireNonNull(tokens, "tokens must not be null");
        }
    }
}
