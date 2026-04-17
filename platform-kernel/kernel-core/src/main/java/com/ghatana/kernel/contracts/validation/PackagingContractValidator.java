package com.ghatana.kernel.contracts.validation;

import com.ghatana.kernel.contracts.ContractValidator;
import com.ghatana.kernel.contracts.KernelContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates PACKAGING-family kernel contracts (pack manifests, deployment units, lifecycle hooks).
 *
 * <p>Enforces:
 * <ul>
 *   <li>{@code contractId} and semver version must be valid.</li>
 *   <li>Metadata key {@code packType} must be one of
 *       {@code domain-pack}, {@code platform-plugin}, {@code product-module}, {@code service}.</li>
 *   <li>Metadata key {@code entrypoint} must be present and non-blank.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates PACKAGING-family kernel contracts
 * @doc.layer core
 * @doc.pattern Strategy
 * @since 1.1.0
 */
public final class PackagingContractValidator implements ContractValidator {

    private static final List<String> PACK_TYPES =
            List.of("domain-pack", "platform-plugin", "product-module", "service", "library");

    @Override
    public ValidationResult validate(KernelContract contract) {
        Objects.requireNonNull(contract, "contract cannot be null");
        List<String> errors = new ArrayList<>(contract.getValidationErrors());

        Map<String, String> meta = contract.getMetadata();

        String packType = meta.get("packType");
        if (packType == null || packType.isBlank()) {
            errors.add("PACKAGING contract '" + contract.getContractId()
                    + "' must declare metadata.packType");
        } else if (!PACK_TYPES.contains(packType.toLowerCase(java.util.Locale.ROOT))) {
            errors.add("PACKAGING contract '" + contract.getContractId()
                    + "' has unrecognised packType '" + packType
                    + "'. Expected one of: " + PACK_TYPES);
        }

        String entrypoint = meta.get("entrypoint");
        if (entrypoint == null || entrypoint.isBlank()) {
            errors.add("PACKAGING contract '" + contract.getContractId()
                    + "' must declare metadata.entrypoint (the fully qualified class or module name)");
        }

        return errors.isEmpty() ? ValidationResult.OK : ValidationResult.failed(errors);
    }

    @Override
    public List<KernelContract.ContractFamily> applicableFamilies() {
        return List.of(KernelContract.ContractFamily.PACKAGING);
    }
}
