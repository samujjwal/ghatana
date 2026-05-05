package com.ghatana.yappc.domain.pageartifact;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lightweight design-system contract registry for server-side page artifact validation.
 *
 * <p>This registry intentionally validates only a small canonical subset of contracts that
 * YAPPC page artifacts commonly emit today. It gives the validator a concrete source of
 * truth for contract existence, slot names, required props, and basic prop typing without
 * requiring the full frontend design-system runtime in the JVM.</p>
 */
public final class PageArtifactDesignSystemRegistry {

    private static final Map<String, ContractRule> CONTRACTS = Map.of(
            "Box", new ContractRule(
                    Set.of("default", "header", "footer", "actions", "aside"),
                    Set.of(),
                    Map.of()
            ),
            "Card", new ContractRule(
                    Set.of("default", "header", "footer", "actions"),
                    Set.of(),
                    Map.of()
            ),
            "Text", new ContractRule(
                    Set.of(),
                    Set.of(),
                    Map.of(
                            "text", PropType.STRING,
                            "variant", PropType.STRING
                    )
            ),
            "Button", new ContractRule(
                    Set.of("default", "icon"),
                    Set.of(),
                    Map.of(
                            "label", PropType.STRING,
                            "variant", PropType.STRING,
                            "disabled", PropType.BOOLEAN
                    )
            ),
            "TextField", new ContractRule(
                    Set.of(),
                    Set.of("name"),
                    Map.of(
                            "name", PropType.STRING,
                            "label", PropType.STRING,
                            "placeholder", PropType.STRING,
                            "type", PropType.STRING,
                            "required", PropType.BOOLEAN,
                            "value", PropType.STRING
                    )
            ),
            "Image", new ContractRule(
                    Set.of(),
                    Set.of("src"),
                    Map.of(
                            "src", PropType.STRING,
                            "alt", PropType.STRING
                    )
            )
    );

    private PageArtifactDesignSystemRegistry() {
    }

    @NotNull
    public static Optional<ContractRule> findContract(@NotNull String contractName) {
        return Optional.ofNullable(CONTRACTS.get(contractName));
    }

    public record ContractRule(
            Set<String> allowedSlots,
            Set<String> requiredProps,
            Map<String, PropType> propTypes
    ) {
    }

    public enum PropType {
        STRING,
        BOOLEAN,
        NUMBER
    }
}
