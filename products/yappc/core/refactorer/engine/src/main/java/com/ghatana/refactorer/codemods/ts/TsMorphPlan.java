package com.ghatana.refactorer.codemods.ts;

import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.codemods.CodeAction;
import com.ghatana.refactorer.shared.codemods.CodeModificationPlan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a plan for modifying TypeScript/JavaScript code using ts-morph. This implementation
 * focuses on adding missing imports.
 
 * @doc.type class
 * @doc.purpose Handles ts morph plan operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class TsMorphPlan implements CodeModificationPlan {
    @SuppressWarnings("unused")
    private static final Logger logger = LogManager.getLogger(TsMorphPlan.class);

    private final Path filePath;
    private final List<ImportToAdd> importsToAdd;
    private final List<UnifiedDiagnostic> fixedDiagnostics;

    public TsMorphPlan(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null");
        this.importsToAdd = new ArrayList<>();
        this.fixedDiagnostics = new ArrayList<>();
    }

    /**
     * Adds an import to be added to the file.
     *
     * @param moduleSpecifier The module to import from (e.g., "react")
     * @param namedImports List of named imports (e.g., ["useState", "useEffect"])
     * @param defaultImport Default import name (optional)
     * @param isTypeOnly Whether this is a type-only import
     * @param diagnostic The diagnostic this import fixes (optional)
     * @return This plan for method chaining
     */
    public TsMorphPlan addImport(
            String moduleSpecifier,
            List<String> namedImports,
            String defaultImport,
            boolean isTypeOnly,
            UnifiedDiagnostic diagnostic) {

        if (moduleSpecifier == null || moduleSpecifier.isBlank()) {
            throw new IllegalArgumentException("moduleSpecifier cannot be null or blank");
        }

        // Check if we already have this import
        boolean exists =
                importsToAdd.stream()
                        .anyMatch(
                                imp ->
                                        imp.moduleSpecifier.equals(moduleSpecifier)
                                                && imp.isTypeOnly == isTypeOnly);

        if (!exists) {
            importsToAdd.add(
                    new ImportToAdd(
                            moduleSpecifier,
                            namedImports != null ? List.copyOf(namedImports) : List.of(),
                            defaultImport,
                            isTypeOnly));

            if (diagnostic != null) {
                fixedDiagnostics.add(diagnostic);
            }
        }

        return this;
    }

    @Override
    public Path getFilePath() {
        return filePath;
    }

    @Override
    public List<CodeAction> getActions() {
        List<CodeAction> actions = new ArrayList<>();

        for (ImportToAdd imp : importsToAdd) {
            String description = String.format("Add import from '%s'", imp.moduleSpecifier);
            if (!imp.namedImports.isEmpty()) {
                description += " { " + String.join(", ", imp.namedImports) + " }";
            }
            if (imp.defaultImport != null) {
                description =
                        String.format(
                                "Import %s from '%s'", imp.defaultImport, imp.moduleSpecifier);
            }

            actions.add(new CodeAction(description, "add-import"));
        }

        return actions;
    }

    @Override
    public List<UnifiedDiagnostic> getFixedDiagnostics() {
        return new ArrayList<>(fixedDiagnostics);
    }

    @Override
    public boolean isEmpty() {
        return importsToAdd.isEmpty();
    }

    /** Gets all imports that will be added by this plan. */
    public List<ImportToAdd> getImportsToAdd() {
        return new ArrayList<>(importsToAdd);
    }

    /** Represents an import statement to be added to a file. */
    public static class ImportToAdd {
        public final String moduleSpecifier;
        public final List<String> namedImports;
        public final String defaultImport;
        public final boolean isTypeOnly;

        public ImportToAdd(
                String moduleSpecifier,
                List<String> namedImports,
                String defaultImport,
                boolean isTypeOnly) {
            this.moduleSpecifier = moduleSpecifier;
            this.namedImports = namedImports != null ? List.copyOf(namedImports) : List.of();
            this.defaultImport = defaultImport;
            this.isTypeOnly = isTypeOnly;
        }
    }
}
