/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validation report produced by {@link AepCentralCatalogService} after
 * loading and validating agent catalogs across all product roots.
 *
 * @doc.type record
 * @doc.purpose Catalog validation report with errors and warnings
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CatalogValidationReport(
    Map<String, Integer> loadedCatalogs,
    List<ValidationIssue> errors,
    List<ValidationIssue> warnings
) {

    public int catalogCount() {
        return loadedCatalogs.size();
    }

    public int totalAgents() {
        return loadedCatalogs.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public record ValidationIssue(String code, String message) {}

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final Map<String, Integer> catalogs = new HashMap<>();
        private final List<ValidationIssue> errors = new ArrayList<>();
        private final List<ValidationIssue> warnings = new ArrayList<>();

        Builder addLoadedCatalog(String catalogId, int agentCount) {
            catalogs.put(catalogId, agentCount);
            return this;
        }

        Builder addError(String code, String message) {
            errors.add(new ValidationIssue(code, message));
            return this;
        }

        Builder addWarning(String code, String message) {
            warnings.add(new ValidationIssue(code, message));
            return this;
        }

        CatalogValidationReport build() {
            return new CatalogValidationReport(
                    Collections.unmodifiableMap(new HashMap<>(catalogs)),
                    Collections.unmodifiableList(new ArrayList<>(errors)),
                    Collections.unmodifiableList(new ArrayList<>(warnings))
            );
        }
    }
}
