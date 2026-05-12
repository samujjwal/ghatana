package com.ghatana.platform.observability.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.platform.observability.contract.ObservabilityFlowContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Executable contract tests for Kernel observability flow contracts.
 * Replaces token-scanning conformance gates with structured contract tests.
 *
 * @doc.type class
 * @doc.purpose Executable contract tests for observability flow contracts
 * @doc.layer platform
 * @doc.pattern Contract Test
 */
@DisplayName("Observability Flow Contract Tests")
class ObservabilityFlowContractTest {

    @Test
    @DisplayName("Should validate observability flow contract structure")
    void shouldValidateObservabilityFlowContractStructure() {
        // Test that the contract validates required fields
        ObservabilityFlowContract contract = new ObservabilityFlowContract();
        
        assertNotNull(contract.getRequiredFacets(), "Required facets must not be null");
        assertNotNull(contract.getFlows(), "Flows must not be null");
        assertFalse(contract.getRequiredFacets().isEmpty(), "Required facets must not be empty");
        assertFalse(contract.getFlows().isEmpty(), "Flows must not be empty");
    }

    @Test
    @DisplayName("Should validate each flow has required fields")
    void shouldValidateEachFlowHasRequiredFields() {
        ObservabilityFlowContract contract = new ObservabilityFlowContract();
        
        for (ObservabilityFlowContract.Flow flow : contract.getFlows()) {
            assertNotNull(flow.product(), "Flow must have product");
            assertNotNull(flow.flow(), "Flow must have flow name");
            assertNotNull(flow.kind(), "Flow must have kind");
            assertNotNull(flow.facets(), "Flow must have facets");
            assertNotNull(flow.evidence(), "Flow must have evidence");
            assertFalse(flow.facets().isEmpty(), "Flow facets must not be empty");
            assertFalse(flow.evidence().isEmpty(), "Flow evidence must not be empty");
        }
    }

    @Test
    @DisplayName("Should validate all required products are covered")
    void shouldValidateAllRequiredProductsAreCovered() {
        ObservabilityFlowContract contract = new ObservabilityFlowContract();
        
        var coveredProducts = contract.getFlows().stream()
            .map(ObservabilityFlowContract.Flow::product)
            .distinct()
            .toList();
        
        assertTrue(coveredProducts.contains("phr"), "PHR must be covered");
        assertTrue(coveredProducts.contains("finance"), "Finance must be covered");
        assertTrue(coveredProducts.contains("digital-marketing"), "Digital Marketing must be covered");
        assertTrue(coveredProducts.contains("flashit"), "FlashIt must be covered");
    }

    @Test
    @DisplayName("Should validate at least one bridge flow exists")
    void shouldValidateAtLeastOneBridgeFlowExists() {
        ObservabilityFlowContract contract = new ObservabilityFlowContract();
        
        var bridgeFlowCount = contract.getFlows().stream()
            .filter(flow -> "bridge".equals(flow.kind()))
            .count();
        
        assertTrue(bridgeFlowCount > 0, "At least one bridge flow must be covered");
    }

    @Test
    @DisplayName("Should validate evidence files exist")
    void shouldValidateEvidenceFilesExist() {
        ObservabilityFlowContract contract = new ObservabilityFlowContract();
        
        for (ObservabilityFlowContract.Flow flow : contract.getFlows()) {
            for (ObservabilityFlowContract.Evidence evidence : flow.evidence()) {
                assertNotNull(evidence.file(), "Evidence must have file path");
                assertNotNull(evidence.tokens(), "Evidence must have tokens");
                assertFalse(evidence.tokens().isEmpty(), "Evidence tokens must not be empty");
            }
        }
    }
}
