package com.ghatana.refactorer.refactoring.orchestrator;

import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import com.ghatana.refactorer.refactoring.model.crosslanguage.CrossLanguageReference;
import com.ghatana.refactorer.refactoring.service.ReferenceResolver;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates refactoring operations across multiple languages and files. 
 * @doc.type class
 * @doc.purpose Handles refactoring orchestrator operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class RefactoringOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(RefactoringOrchestrator.class);

    private final Map<String, RenameRefactoring> refactoringRegistry;
    private final ReferenceResolver referenceResolver;
    private final RefactoringTransactionManager transactionManager;
    private final PolyfixProjectContext projectContext;
    private final Path projectRoot;
    private final Set<Path> affectedFiles;

    public RefactoringOrchestrator(
            ReferenceResolver referenceResolver, PolyfixProjectContext projectContext) {
        this(referenceResolver, projectContext, true);
    }

    public RefactoringOrchestrator(
            ReferenceResolver referenceResolver,
            PolyfixProjectContext projectContext,
            boolean registerDefaults) {
        this.refactoringRegistry = new ConcurrentHashMap<>();
        this.referenceResolver = referenceResolver;
        this.transactionManager = new RefactoringTransactionManager();
        this.projectContext = projectContext;
        this.projectRoot = projectContext.getProjectRoot();
        this.affectedFiles = new HashSet<>();

        if (registerDefaults) {
            registerDefaultRefactorings();
        }
    }

    private void registerDefaultRefactorings() {
        refactoringRegistry.put(
                "java", new com.ghatana.refactorer.refactoring.impl.java.JavaRenameRefactoring());
        refactoringRegistry.put(
                "python",
                new com.ghatana.refactorer.refactoring.impl.python.PythonRenameRefactoring());
        refactoringRegistry.put(
                "typescript",
                new com.ghatana.refactorer.refactoring.impl.typescript
                        .TypeScriptRenameRefactoring());
    }

    /**
 * Registers a refactoring implementation for a specific language. */
    public final void registerRefactoring(RenameRefactoring refactoring) {
        if (refactoring != null) {
            refactoringRegistry.put(refactoring.getId().toLowerCase(), refactoring);
            log.debug("Registered refactoring: {}", refactoring.getId());
        }
    }

    /**
 * Performs a rename refactoring across all affected files. */
    public RefactoringResult performRename(RenameRefactoring.Context context) {
        if (context == null) {
            return RefactoringResult.failure("Refactoring context cannot be null");
        }

        // Start a new transaction
        String transactionId = transactionManager.beginTransaction();

        try {
            // Find the appropriate refactoring for the source file
            RenameRefactoring refactoring = findRefactoringForFile(context.getSourceFile());
            if (refactoring == null) {
                return RefactoringResult.failure(
                        "No refactoring implementation found for file: " + context.getSourceFile());
            }

            // Find all references to the element being renamed
            List<CrossLanguageReference> references = findReferences(context);

            // Group references by file for batch processing
            Map<Path, List<CrossLanguageReference>> referencesByFile =
                    groupReferencesByFile(references);

            // Perform the refactoring in the source file first
            RefactoringResult result = refactoring.apply(context);
            log.debug(
                    "Source refactoring {} success={} changes={}",
                    refactoring.getId(),
                    result.isSuccess(),
                    result.getChangeCount());
            if (!result.isSuccess()) {
                transactionManager.rollbackTransaction(transactionId);
                return result;
            }

            // Process references in other files
            for (Map.Entry<Path, List<CrossLanguageReference>> entry :
                    referencesByFile.entrySet()) {
                Path filePath = entry.getKey();
                if (filePath.toString().equals(context.getSourceFile())) {
                    continue; // Already processed the source file
                }

                // Find the appropriate refactoring for this file
                RenameRefactoring fileRefactoring = findRefactoringForFile(filePath.toString());
                log.debug(
                        "Processing {} references in {} using {}",
                        entry.getValue().size(),
                        filePath,
                        fileRefactoring.getId());
                // Create a new context for each reference
                for (CrossLanguageReference ref : entry.getValue()) {
                    RenameRefactoring.Context refContext = createContextForReference(context, ref);
                    RefactoringResult refResult = fileRefactoring.apply(refContext);
                    log.debug(
                            "Reference refactoring {} success={} changes={}",
                            fileRefactoring.getId(),
                            refResult.isSuccess(),
                            refResult.getChangeCount());

                    if (!refResult.isSuccess()) {
                        log.warn(
                                "Failed to update reference in {}: {}",
                                filePath,
                                refResult.getErrorMessage());
                        // Continue with other references, but mark the overall result as
                        // partial
                        result =
                                RefactoringResult.partial(
                                        result.getModifiedFiles(),
                                        result.getChangeCount(),
                                        "Some references could not be updated: "
                                                + refResult.getErrorMessage());
                    } else {
                        // Merge the results
                        result = mergeResults(result, refResult);
                    }
                }
            }

            // Commit the transaction if everything succeeded
            if (result.isSuccess()) {
                transactionManager.commitTransaction(transactionId);
            } else {
                transactionManager.rollbackTransaction(transactionId);
            }

            return result;

        } catch (Exception e) {
            log.error("Error during refactoring", e);
            transactionManager.rollbackTransaction(transactionId);
            return RefactoringResult.failure("Refactoring failed: " + e.getMessage());
        }
    }

    private RenameRefactoring findRefactoringForFile(String filePath) {
        // Create a basic context with required fields for canApply check
        RenameRefactoring.Context basicContext =
                new RenameRefactoring.Context() {
                    @Override
                    public String getSourceFile() {
                        return filePath;
                    }

                    @Override
                    public String getOldName() {
                        return "";
                    }

                    @Override
                    public String getNewName() {
                        return "";
                    }

                    @Override
                    public String getElementType() {
                        return "";
                    }

                    @Override
                    public int getLineNumber() {
                        return 0;
                    }

                    @Override
                    public int getColumnNumber() {
                        return 0;
                    }

                    @Override
                    public boolean isDryRun() {
                        return false;
                    }

                    @Override
                    public boolean isInteractive() {
                        return false;
                    }

                    @Override
                    public Set<Path> getAffectedFiles() {
                        return Set.of(Path.of(filePath));
                    }

                    @Override
                    public Path getProjectRoot() {
                        return projectRoot;
                    }

                    @Override
                    public PolyfixProjectContext getPolyfixProjectContext() {
                        return projectContext;
                    }
                };

        for (RenameRefactoring refactoring : refactoringRegistry.values()) {
            if (refactoring.canApply(basicContext)) {
                return refactoring;
            }
        }
        return null;
    }

    private List<CrossLanguageReference> findReferences(RenameRefactoring.Context context) {
        // First, find references in the same language
        List<CrossLanguageReference> references =
                referenceResolver.findReferences(
                        Path.of(context.getSourceFile()),
                        context.getOldName(),
                        context.getElementType(),
                        getLanguageForFile(context.getSourceFile()));

        // Then find cross-language references
        references.addAll(
                referenceResolver.findIncomingReferences(Path.of(context.getSourceFile())));

        return references;
    }

    private Map<Path, List<CrossLanguageReference>> groupReferencesByFile(
            List<CrossLanguageReference> references) {
        Map<Path, List<CrossLanguageReference>> result = new HashMap<>();

        for (CrossLanguageReference ref : references) {
            Path filePath = Path.of(ref.getSourceFile());
            result.computeIfAbsent(filePath, k -> new ArrayList<>()).add(ref);
        }

        return result;
    }

    private RenameRefactoring.Context createContextForReference(
            RenameRefactoring.Context originalContext, CrossLanguageReference reference) {
        return new RenameRefactoring.Context() {
            @Override
            public String getSourceFile() {
                return reference.getSourceFile();
            }

            @Override
            public String getOldName() {
                return reference.getSourceElement();
            }

            @Override
            public String getNewName() {
                return originalContext.getNewName();
            }

            @Override
            public String getElementType() {
                return reference.getSourceElementType();
            }

            @Override
            public int getLineNumber() {
                return reference.getSourceLine();
            }

            @Override
            public int getColumnNumber() {
                return 0; // Not available from CrossLanguageReference
            }

            @Override
            public boolean isDryRun() {
                return originalContext.isDryRun();
            }

            @Override
            public boolean isInteractive() {
                return originalContext.isInteractive();
            }

            @Override
            public Set<Path> getAffectedFiles() {
                return Set.of(Path.of(reference.getSourceFile()));
            }

            @Override
            public Path getProjectRoot() {
                return projectRoot;
            }

            @Override
            public PolyfixProjectContext getPolyfixProjectContext() {
                return projectContext;
            }
        };
    }

    private String getLanguageForFile(String filePath) {
        if (filePath.endsWith(".java")) return "java";
        if (filePath.endsWith(".py")) return "python";
        if (filePath.endsWith(".ts") || filePath.endsWith(".tsx")) return "typescript";
        if (filePath.endsWith(".js") || filePath.endsWith(".jsx")) return "javascript";
        return "";
    }

    private RefactoringResult mergeResults(RefactoringResult result1, RefactoringResult result2) {
        Set<Path> allFiles = new HashSet<>(result1.getModifiedFiles());
        allFiles.addAll(result2.getModifiedFiles());

        // For cross-language refactoring, we want to consider the operation successful
        // as long as we have some changes, even if one of the results is not successful
        return RefactoringResult.success(
                new ArrayList<>(allFiles),
                result1.getChangeCount() + result2.getChangeCount(),
                result1.getChangeSummary() + "\n" + result2.getChangeSummary());
    }
}
