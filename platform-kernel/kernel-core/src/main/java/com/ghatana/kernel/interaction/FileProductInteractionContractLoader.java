package com.ghatana.kernel.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * File-based implementation of ProductInteractionContractLoader.
 *
 * <p>Loads contracts from JSON files in a specified directory. Each file
 * should contain a valid ProductInteractionContract in JSON format.</p>
 *
 * @doc.type class
 * @doc.purpose Load product interaction contracts from JSON files
 * @doc.layer kernel
 * @doc.pattern Loader
 */
public final class FileProductInteractionContractLoader implements ProductInteractionContractLoader {

    private final Path contractsDirectory;
    private final ObjectMapper objectMapper;

    private FileProductInteractionContractLoader(Path contractsDirectory) {
        this.contractsDirectory = contractsDirectory;
        this.objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    public static FileProductInteractionContractLoader create(Path contractsDirectory) {
        if (contractsDirectory == null) {
            throw new IllegalArgumentException("contractsDirectory must not be null");
        }
        return new FileProductInteractionContractLoader(contractsDirectory);
    }

    @Override
    public Map<String, ProductInteractionContract> loadAll() throws ContractLoadException {
        if (!isAvailable()) {
            throw new ContractLoadException("Contracts directory not available: " + contractsDirectory);
        }

        Map<String, ProductInteractionContract> contracts = new HashMap<>();

        try (Stream<Path> paths = Files.walk(contractsDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            ProductInteractionContract contract = objectMapper.readValue(
                                    path.toFile(),
                                    ProductInteractionContract.class
                            );
                            contracts.put(contract.contractId(), contract);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to load contract from: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new ContractLoadException("Failed to load contracts from directory: " + contractsDirectory, e);
        }

        return contracts;
    }

    @Override
    public List<ProductInteractionContract> loadByProvider(String providerProductId) throws ContractLoadException {
        Map<String, ProductInteractionContract> allContracts = loadAll();
        List<ProductInteractionContract> providerContracts = new ArrayList<>();

        for (ProductInteractionContract contract : allContracts.values()) {
            if (contract.providerProductId().equals(providerProductId)) {
                providerContracts.add(contract);
            }
        }

        return providerContracts;
    }

    @Override
    public ProductInteractionContract loadById(String contractId) throws ContractLoadException {
        Map<String, ProductInteractionContract> allContracts = loadAll();
        return allContracts.get(contractId);
    }

    @Override
    public boolean isAvailable() {
        return contractsDirectory != null && Files.exists(contractsDirectory) && Files.isDirectory(contractsDirectory);
    }
}
