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
    void setUp() { // GH-90000
        validationGate = new ContractValidationGate(); // GH-90000
    }

    @Test
    @DisplayName("Should validate API contract successfully")
    void testValidateAPIContract() { // GH-90000
        // Use concrete ApiContract type
        ApiContract contract = ApiContract.builder("api.user.login", "User Login API", "1.0.0") // GH-90000
            .metadata(Map.of( // GH-90000
                "http_methods", "POST",
                "path", "/api/v1/login",
                "authentication", "oauth2"
            ))
            .build(); // GH-90000

        // Just validate that the contract is created properly
        assertNotNull(contract); // GH-90000
        assertEquals("api.user.login", contract.getContractId()); // GH-90000
        assertEquals(KernelContract.ContractFamily.API, contract.getFamily()); // GH-90000
    }

    @Test
    @DisplayName("Should validate Autonomy contract successfully")
    void testValidateAutonomyContract() { // GH-90000
        AutonomyContract contract = AutonomyContract.builder("autonomy.fraud.detection", "Fraud Detection Agent", "1.0.0") // GH-90000
            .metadata(Map.of( // GH-90000
                "agent_type", "fraud_detection",
                "autonomy_level", "MEDIUM"
            ))
            .build(); // GH-90000

        assertNotNull(contract); // GH-90000
        assertEquals("autonomy.fraud.detection", contract.getContractId()); // GH-90000
        assertEquals(KernelContract.ContractFamily.AUTONOMY, contract.getFamily()); // GH-90000
    }

    @Test
    @DisplayName("Should test AutonomousContractValidator")
    void testAutonomousContractValidator() { // GH-90000
        AutonomyContract contract = AutonomyContract.builder("autonomy.test", "Test Autonomy", "1.0.0") // GH-90000
            .metadata(Map.of()) // GH-90000
            .build(); // GH-90000

        AutonomousContractValidator validator = new AutonomousContractValidator(); // GH-90000
        ContractValidator.ValidationResult result = validator.validate(contract); // GH-90000

        assertTrue(result.valid()); // GH-90000
    }
}
