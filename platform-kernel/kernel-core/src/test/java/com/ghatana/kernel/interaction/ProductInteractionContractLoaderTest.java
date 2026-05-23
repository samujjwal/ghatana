package com.ghatana.kernel.interaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProductInteractionContractLoader implementations.
 */
class ProductInteractionContractLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void fileLoader_loadsAllContracts() throws Exception {
        // Create test contract files
        Path contract1File = tempDir.resolve("contract1.json");
        Path contract2File = tempDir.resolve("contract2.json");

        String contract1Json = """
            {
                "contractId": "test-contract-1",
                "contractVersion": "1.0.0",
                "providerProductId": "provider-a",
                "consumerProductIds": ["consumer-a", "consumer-b"],
                "requiresAuth": true,
                "requiresTenant": true,
                "requiresConsent": false,
                "piiClassification": "none",
                "tenantScope": "single",
                "allowedCallerRoles": ["admin"],
                "allowedPurposes": ["read"],
                "allowedLifecyclePhases": ["production"],
                "degradedModeAllowed": false
            }
            """;

        String contract2Json = """
            {
                "contractId": "test-contract-2",
                "contractVersion": "1.0.0",
                "providerProductId": "provider-b",
                "consumerProductIds": ["consumer-c"],
                "requiresAuth": false,
                "requiresTenant": false,
                "requiresConsent": false,
                "piiClassification": "none",
                "tenantScope": "shared",
                "allowedCallerRoles": [],
                "allowedPurposes": [],
                "allowedLifecyclePhases": [],
                "degradedModeAllowed": true
            }
            """;

        Files.writeString(contract1File, contract1Json);
        Files.writeString(contract2File, contract2Json);

        // Load contracts
        ProductInteractionContractLoader loader = FileProductInteractionContractLoader.create(tempDir);
        Map<String, ProductInteractionContract> contracts = loader.loadAll();

        // Verify
        assertEquals(2, contracts.size());
        assertTrue(contracts.containsKey("test-contract-1"));
        assertTrue(contracts.containsKey("test-contract-2"));

        ProductInteractionContract contract1 = contracts.get("test-contract-1");
        assertEquals("test-contract-1", contract1.contractId());
        assertEquals("1.0.0", contract1.contractVersion());
        assertEquals("provider-a", contract1.providerProductId());
        assertTrue(contract1.requiresAuth());
        assertTrue(contract1.requiresTenant());
    }

    @Test
    void fileLoader_loadsByProvider() throws Exception {
        // Create test contract files
        Path contract1File = tempDir.resolve("contract1.json");
        Path contract2File = tempDir.resolve("contract2.json");

        String contract1Json = """
            {
                "contractId": "test-contract-1",
                "contractVersion": "1.0.0",
                "providerProductId": "provider-a",
                "consumerProductIds": ["consumer-a"],
                "requiresAuth": true,
                "requiresTenant": true,
                "requiresConsent": false,
                "piiClassification": "none",
                "tenantScope": "single",
                "allowedCallerRoles": ["admin"],
                "allowedPurposes": ["read"],
                "allowedLifecyclePhases": ["production"],
                "degradedModeAllowed": false
            }
            """;

        String contract2Json = """
            {
                "contractId": "test-contract-2",
                "contractVersion": "1.0.0",
                "providerProductId": "provider-b",
                "consumerProductIds": ["consumer-b"],
                "requiresAuth": false,
                "requiresTenant": false,
                "requiresConsent": false,
                "piiClassification": "none",
                "tenantScope": "shared",
                "allowedCallerRoles": [],
                "allowedPurposes": [],
                "allowedLifecyclePhases": [],
                "degradedModeAllowed": true
            }
            """;

        Files.writeString(contract1File, contract1Json);
        Files.writeString(contract2File, contract2Json);

        // Load contracts by provider
        ProductInteractionContractLoader loader = FileProductInteractionContractLoader.create(tempDir);
        List<ProductInteractionContract> providerAContracts = loader.loadByProvider("provider-a");

        // Verify
        assertEquals(1, providerAContracts.size());
        assertEquals("test-contract-1", providerAContracts.get(0).contractId());
    }

    @Test
    void fileLoader_loadsById() throws Exception {
        // Create test contract file
        Path contractFile = tempDir.resolve("contract1.json");

        String contractJson = """
            {
                "contractId": "test-contract-1",
                "contractVersion": "1.0.0",
                "providerProductId": "provider-a",
                "consumerProductIds": ["consumer-a"],
                "requiresAuth": true,
                "requiresTenant": true,
                "requiresConsent": false,
                "piiClassification": "none",
                "tenantScope": "single",
                "allowedCallerRoles": ["admin"],
                "allowedPurposes": ["read"],
                "allowedLifecyclePhases": ["production"],
                "degradedModeAllowed": false
            }
            """;

        Files.writeString(contractFile, contractJson);

        // Load contract by ID
        ProductInteractionContractLoader loader = FileProductInteractionContractLoader.create(tempDir);
        ProductInteractionContract contract = loader.loadById("test-contract-1");

        // Verify
        assertNotNull(contract);
        assertEquals("test-contract-1", contract.contractId());

        // Test non-existent contract
        ProductInteractionContract nonExistent = loader.loadById("non-existent");
        assertNull(nonExistent);
    }

    @Test
    void fileLoader_isAvailable() {
        // Test with existing directory
        ProductInteractionContractLoader loader = FileProductInteractionContractLoader.create(tempDir);
        assertTrue(loader.isAvailable());

        // Test with non-existent directory
        ProductInteractionContractLoader nonExistentLoader = 
                FileProductInteractionContractLoader.create(Path.of("/non/existent/path"));
        assertFalse(nonExistentLoader.isAvailable());
    }

    @Test
    void fileLoader_throwsWhenDirectoryNotAvailable() {
        ProductInteractionContractLoader loader = 
                FileProductInteractionContractLoader.create(Path.of("/non/existent/path"));

        assertThrows(ProductInteractionContractLoader.ContractLoadException.class, loader::loadAll);
    }

    @Test
    void fileLoader_requiresNonNullDirectory() {
        assertThrows(IllegalArgumentException.class, () -> 
                FileProductInteractionContractLoader.create(null));
    }
}
