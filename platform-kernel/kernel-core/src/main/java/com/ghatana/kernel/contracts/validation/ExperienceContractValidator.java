package com.ghatana.kernel.contracts.validation;

import com.ghatana.kernel.contracts.ContractValidator;
import com.ghatana.kernel.contracts.KernelContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates EXPERIENCE-family kernel contracts (UI screens, navigation, theming).
 *
 * <p>Enforces:
 * <ul>
 *   <li>{@code contractId} and semver version must be valid.</li>
 *   <li>Metadata key {@code surfaceType} must be one of
 *       {@code screen}, {@code component}, {@code navigation}, {@code theme}.</li>
 *   <li>Metadata key {@code platform} should be one of
 *       {@code web}, {@code mobile-ios}, {@code mobile-android}, {@code desktop}, {@code all}.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates EXPERIENCE-family kernel contracts
 * @doc.layer core
 * @doc.pattern Strategy
 * @since 1.1.0
 */
public final class ExperienceContractValidator implements ContractValidator {

    private static final List<String> SURFACE_TYPES =
            List.of("screen", "component", "navigation", "theme", "widget");
    private static final List<String> PLATFORMS =
            List.of("web", "mobile-ios", "mobile-android", "desktop", "all");

    @Override
    public ValidationResult validate(KernelContract contract) {
        Objects.requireNonNull(contract, "contract cannot be null");
        List<String> errors = new ArrayList<>(contract.getValidationErrors());

        Map<String, String> meta = contract.getMetadata();

        String surfaceType = meta.get("surfaceType");
        if (surfaceType == null || surfaceType.isBlank()) {
            errors.add("EXPERIENCE contract '" + contract.getContractId()
                    + "' must declare metadata.surfaceType");
        } else if (!SURFACE_TYPES.contains(surfaceType.toLowerCase(java.util.Locale.ROOT))) {
            errors.add("EXPERIENCE contract '" + contract.getContractId()
                    + "' has unrecognised surfaceType '" + surfaceType
                    + "'. Expected one of: " + SURFACE_TYPES);
        }

        String platform = meta.get("platform");
        if (platform != null && !platform.isBlank()
                && !PLATFORMS.contains(platform.toLowerCase(java.util.Locale.ROOT))) {
            errors.add("EXPERIENCE contract '" + contract.getContractId()
                    + "' has unrecognised platform '" + platform
                    + "'. Expected one of: " + PLATFORMS);
        }

        return errors.isEmpty() ? ValidationResult.OK : ValidationResult.failed(errors);
    }

    @Override
    public List<KernelContract.ContractFamily> applicableFamilies() {
        return List.of(KernelContract.ContractFamily.EXPERIENCE);
    }
}
