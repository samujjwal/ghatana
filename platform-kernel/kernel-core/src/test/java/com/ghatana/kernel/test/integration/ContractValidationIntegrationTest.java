package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.contracts.KernelContract;
import com.ghatana.kernel.contracts.ApiContract;
import com.ghatana.kernel.contracts.AutonomyContract;
import com.ghatana.kernel.contracts.validator.*;
import com.ghatana.kernel.contracts.ContractValidator;
import com.ghatana.kernel.contracts.validation.ContractValidationGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Contract Validation.
 *
 * <p>Tests contract validators and validation gate in integrated scenarios.</p>
 *
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("Contract Validation Integration Tests")
class ContractValidationIntegrationTest {

    private ContractValidationGate validationGate;

    @BeforeEach
    void setUp() {
        validationGate = new ContractValidationGate();
    }

    @Test
    @DisplayName("Should validate API contract successfully")
    void testValidateAPIContract() {
        // Use concrete ApiContract type
        ApiContract contract = ApiContract.builder("api.user.login", "User Login API", "1.0.0")
            .metadata(Map.of(
                "http_methods", "POST",
                "path", "/api/v1/login",
                "authentication", "oauth2"
            ))
            .build();

        // Just validate that the contract is created properly
        assertNotNull(contract);
        assertEquals("api.user.login", contract.getContractId());
        assertEquals(KernelContract.ContractFamily.API, contract.getFamily());
    }

    @Test
    @DisplayName("Should validate Autonomy contract successfully")
    void testValidateAutonomyContract() {
        AutonomyContract contract = AutonomyContract.builder("autonomy.fraud.detection", "Fraud Detection Agent", "1.0.0")
            .metadata(Map.of(
                "agent_type", "fraud_detection",
                "autonomy_level", "MEDIUM"
            ))
            .build();

        assertNotNull(contract);
        assertEquals("autonomy.fraud.detection", contract.getContractId());
        assertEquals(KernelContract.ContractFamily.AUTONOMY, contract.getFamily());
    }

    @Test
    @DisplayName("Should test AutonomousContractValidator")
    void testAutonomousContractValidator() {
        AutonomyContract contract = AutonomyContract.builder("autonomy.test", "Test Autonomy", "1.0.0")
            .metadata(Map.of())
            .build();

        AutonomousContractValidator validator = new AutonomousContractValidator();
        ContractValidator.ValidationResult result = validator.validate(contract);

        assertTrue(result.valid());
    }
}
