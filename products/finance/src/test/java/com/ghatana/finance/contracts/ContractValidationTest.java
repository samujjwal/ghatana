/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.contracts;

import com.ghatana.kernel.contracts.KernelContract;
import com.ghatana.kernel.contracts.validation.ContractValidationGate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Finance contract validation
 */
public class ContractValidationTest {

    @Test
    public void testTransactionAPIContract_ShouldBeValid() {
        KernelContract contract = FinanceContracts.TRANSACTION_API;
        
        assertNotNull(contract);
        assertEquals("finance.api.transaction", contract.getContractId());
        assertEquals(KernelContract.ContractFamily.API, contract.getFamily());
        assertTrue(contract.getMetadata().containsKey("http_methods"));
        assertTrue(contract.getMetadata().containsKey("authentication"));
    }
    
    @Test
    public void testTransactionSchemaContract_ShouldBeValid() {
        KernelContract contract = FinanceContracts.TRANSACTION_SCHEMA;
        
        assertNotNull(contract);
        assertEquals("finance.schema.transaction", contract.getContractId());
        assertEquals(KernelContract.ContractFamily.SCHEMA, contract.getFamily());
        assertTrue(contract.getMetadata().containsKey("required_fields"));
        assertTrue(contract.getMetadata().containsKey("validation_rules"));
    }
    
    @Test
    public void testFraudDetectionAutonomousContract_ShouldBeValid() {
        KernelContract contract = FinanceContracts.FRAUD_DETECTION_AUTONOMOUS;
        
        assertNotNull(contract);
        assertEquals("finance.autonomous.fraud-detection", contract.getContractId());
        assertEquals(KernelContract.ContractFamily.AUTONOMY, contract.getFamily());
        assertEquals("true", contract.getMetadata().get("ai_governed"));
        assertEquals("required", contract.getMetadata().get("model_approval"));
        assertEquals("true", contract.getMetadata().get("explainability"));
    }
    
    @Test
    public void testTransactionAnalyticsContract_ShouldBeValid() {
        KernelContract contract = FinanceContracts.TRANSACTION_ANALYTICS;
        
        assertNotNull(contract);
        assertEquals("finance.analytics.transactions", contract.getContractId());
        assertEquals(KernelContract.ContractFamily.ANALYTICS, contract.getFamily());
        assertEquals("true", contract.getMetadata().get("regulatory"));
        assertTrue(contract.getMetadata().containsKey("compliance_requirements"));
    }
    
    @Test
    public void testAllContracts_ShouldValidate() {
        ContractValidationGate gate = new ContractValidationGate();
        List<KernelContract> contracts = FinanceContracts.getAllContracts();
        
        assertEquals(4, contracts.size());
        
        ContractValidationGate.GateResult result = 
            gate.validateContractsForDeployment(contracts);
        
        assertTrue(result.isValid(), 
            "All Finance contracts should be valid. Violations: " + result.getViolations());
    }
    
    @Test
    public void testContractMetadata_ShouldContainRequiredFields() {
        KernelContract apiContract = FinanceContracts.TRANSACTION_API;
        
        // API contract should have required metadata
        assertTrue(apiContract.getMetadata().containsKey("version"));
        assertTrue(apiContract.getMetadata().containsKey("authentication"));
        assertTrue(apiContract.getMetadata().containsKey("rate_limiting"));
        
        KernelContract schemaContract = FinanceContracts.TRANSACTION_SCHEMA;
        
        // Schema contract should have required metadata
        assertTrue(schemaContract.getMetadata().containsKey("schema_type"));
        assertTrue(schemaContract.getMetadata().containsKey("compatibility_mode"));
        
        KernelContract autonomousContract = FinanceContracts.FRAUD_DETECTION_AUTONOMOUS;
        
        // Autonomous contract should have AI governance metadata
        assertTrue(autonomousContract.getMetadata().containsKey("autonomy_level"));
        assertTrue(autonomousContract.getMetadata().containsKey("human_review"));
        assertTrue(autonomousContract.getMetadata().containsKey("decision_logging"));
    }
}
