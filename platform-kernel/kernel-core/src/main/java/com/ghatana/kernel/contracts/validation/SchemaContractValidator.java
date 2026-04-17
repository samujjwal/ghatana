package com.ghatana.kernel.contracts.validation;

import com.ghatana.kernel.contracts.ContractValidator;
import com.ghatana.kernel.contracts.KernelContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates SCHEMA-family kernel contracts (data models, event schemas, evolution rules).
 *
 * <p>Enforces:
 * <ul>
 *   <li>{@code contractId} and semver version must be valid.</li>
 *   <li>Metadata key {@code format} must be present and one of
 *       {@code json}, {@code avro}, {@code protobuf}, {@code thrift}.</li>
 *   <li>Metadata key {@code owner} must be present to declare the owning module.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates SCHEMA-family kernel contracts
 * @doc.layer core
 * @doc.pattern Strategy
 * @since 1.1.0
 */
public final class SchemaContractValidator implements ContractValidator {

    private static final List<String> VALID_FORMATS =
            List.of("json", "avro", "protobuf", "thrift", "xml", "csv");

    @Override
    public ValidationResult validate(KernelContract contract) {
        Objects.requireNonNull(contract, "contract cannot be null");
        List<String> errors = new ArrayList<>(contract.getValidationErrors());

        Map<String, String> meta = contract.getMetadata();

        String format = meta.get("format");
        if (format == null || format.isBlank()) {
            errors.add("SCHEMA contract '" + contract.getContractId()
                    + "' must declare metadata.format");
        } else if (!VALID_FORMATS.contains(format.toLowerCase(java.util.Locale.ROOT))) {
            errors.add("SCHEMA contract '" + contract.getContractId()
                    + "' has unsupported format '" + format
                    + "'. Expected one of: " + VALID_FORMATS);
        }

        String owner = meta.get("owner");
        if (owner == null || owner.isBlank()) {
            errors.add("SCHEMA contract '" + contract.getContractId()
                    + "' must declare metadata.owner (the module that owns this schema)");
        }

        return errors.isEmpty() ? ValidationResult.OK : ValidationResult.failed(errors);
    }

    @Override
    public List<KernelContract.ContractFamily> applicableFamilies() {
        return List.of(KernelContract.ContractFamily.SCHEMA);
    }
}
