/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contract for UI experience surfaces: screens, navigation, theming, components.
 *
 * <p>An experience contract declares what UI surfaces a module or pack provides,
 * the navigation routes it registers, and theming tokens it exposes or consumes.</p>
 *
 * @doc.type class
 * @doc.purpose Experience contract for UI surface declarations
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class ExperienceContract extends KernelContract {

    /**
     * Describes a screen or UI surface contributed by this contract.
     */
    public record ScreenDeclaration(String screenId, String route, String entryComponent) {
        public ScreenDeclaration {
            Objects.requireNonNull(screenId, "screenId required");
            Objects.requireNonNull(route, "route required");
            Objects.requireNonNull(entryComponent, "entryComponent required");
        }
    }

    /**
     * Describes a theming token this contract exposes or requires.
     */
    public record ThemeToken(String tokenName, String defaultValue, boolean required) {
        public ThemeToken {
            Objects.requireNonNull(tokenName, "tokenName required");
        }
    }

    private final List<ScreenDeclaration> screens;
    private final List<ThemeToken> themeTokens;

    private ExperienceContract(Builder builder) {
        super(builder.contractId, builder.name, builder.version,
              ContractFamily.EXPERIENCE, builder.metadata);
        this.screens = builder.screens != null ? List.copyOf(builder.screens) : List.of();
        this.themeTokens = builder.themeTokens != null ? List.copyOf(builder.themeTokens) : List.of();
        validate();
    }

    public List<ScreenDeclaration> getScreens() { return screens; }
    public List<ThemeToken> getThemeTokens() { return themeTokens; }

    @Override
    protected void validate() {
        super.validate();
        for (ScreenDeclaration screen : screens) {
            if (!screen.route().startsWith("/")) {
                throw new IllegalArgumentException(
                    "Screen route must start with /: " + screen.route());
            }
        }
    }

    /**
     * Creates a new builder for {@link ExperienceContract}.
     */
    public static Builder builder(String contractId, String name, String version) {
        return new Builder(contractId, name, version);
    }

    /**
     * Fluent builder for {@link ExperienceContract}.
     */
    public static final class Builder {
        private final String contractId;
        private final String name;
        private final String version;
        private Map<String, String> metadata = Map.of();
        private List<ScreenDeclaration> screens = List.of();
        private List<ThemeToken> themeTokens = List.of();

        private Builder(String contractId, String name, String version) {
            this.contractId = contractId;
            this.name = name;
            this.version = version;
        }

        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder screens(List<ScreenDeclaration> screens) { this.screens = screens; return this; }
        public Builder themeTokens(List<ThemeToken> themeTokens) { this.themeTokens = themeTokens; return this; }

        public ExperienceContract build() { return new ExperienceContract(this); }
    }
}
