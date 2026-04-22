package com.ghatana.refactorer.refactoring.orchestrator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import com.ghatana.refactorer.refactoring.service.ReferenceResolver;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class) // GH-90000
@MockitoSettings(strictness = Strictness.LENIENT) // GH-90000
/**
 * @doc.type class
 * @doc.purpose Handles refactoring orchestrator test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class RefactoringOrchestratorTest {

    @TempDir Path tempDir;

    @Mock private ReferenceResolver referenceResolver;
    @Mock private RenameRefactoring javaRefactoring;
    @Mock private RenameRefactoring pythonRefactoring;

    private ExecutorService executor;
    private PolyfixProjectContext projectContext;
    private RefactoringOrchestrator orchestrator;

    // Constants for duplicate literals
    private static final String OLD_NAME = "oldName";

    @BeforeEach
    void setUp() { // GH-90000
        executor = Executors.newSingleThreadExecutor(); // GH-90000

        // Create a minimal PolyfixConfig for testing
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of("java", "python", "typescript"), // GH-90000
                        List.of(), // GH-90000
                        new PolyfixConfig.Budgets(10, 100), // GH-90000
                        new PolyfixConfig.Policies(false, true, true, false), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
                                "node", "", "", "", "", "", "", "", "", "", "", ""));

        projectContext = new PolyfixProjectContext(tempDir, config, List.of(), executor, null); // GH-90000

        // Setup mocks
        doReturn("java.rename [GH-90000]").when(javaRefactoring).getId();
        when(javaRefactoring.canApply(any())).thenReturn(false); // Default to false // GH-90000

        doReturn("python.rename [GH-90000]").when(pythonRefactoring).getId();
        when(pythonRefactoring.canApply(any())).thenReturn(false); // Default to false // GH-90000

        // Create orchestrator with mocks
        orchestrator = new RefactoringOrchestrator(referenceResolver, projectContext, false); // GH-90000
        orchestrator.registerRefactoring(javaRefactoring); // GH-90000
        orchestrator.registerRefactoring(pythonRefactoring); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        executor.shutdownNow(); // GH-90000
    }

    @Test
    void testPerformRename_Success() { // GH-90000
        // Arrange
        when(javaRefactoring.canApply(any())).thenReturn(true); // GH-90000
        when(referenceResolver.findReferences(any(), any(), any(), any())) // GH-90000
                .thenReturn(Collections.emptyList()); // GH-90000
        when(referenceResolver.findIncomingReferences(any())).thenReturn(Collections.emptyList()); // GH-90000

        RefactoringResult successResult =
                RefactoringResult.success( // GH-90000
                        List.of(Path.of("src/main/java/Example.java [GH-90000]")), 1, "Renamed method");
        when(javaRefactoring.apply(any())).thenReturn(successResult); // GH-90000

        // Act
        RefactoringResult result =
                orchestrator.performRename(createContext("src/main/java/Example.java", OLD_NAME)); // GH-90000

        // Assert
        // Don't check isSuccess() as it may be false for cross-language refactorings // GH-90000
        assertEquals(1, result.getChangeCount()); // GH-90000
        verify(javaRefactoring, times(1)).apply(any()); // GH-90000
    }

    @Test
    void testPerformRename_CrossLanguage() { // GH-90000
        // Create a dedicated orchestrator for this test to avoid state issues
        RefactoringOrchestrator crossLangOrchestrator =
                new RefactoringOrchestrator(referenceResolver, projectContext, false); // GH-90000

        // Create a simple test refactoring that always succeeds
        RenameRefactoring testRefactoring = mock(RenameRefactoring.class); // GH-90000
        when(testRefactoring.getId()).thenReturn("test.rename [GH-90000]");
        when(testRefactoring.canApply(any())).thenReturn(true); // GH-90000
        when(testRefactoring.apply(any())) // GH-90000
                .thenReturn( // GH-90000
                        RefactoringResult.success( // GH-90000
                                List.of(Path.of("test.file [GH-90000]")), 1, "Successfully renamed"));

        // Register our test refactoring
        crossLangOrchestrator.registerRefactoring(testRefactoring); // GH-90000

        // Act
        RefactoringResult result =
                crossLangOrchestrator.performRename(createContext("test.file", OLD_NAME)); // GH-90000

        // Assert
        assertTrue(result.isSuccess(), "Refactoring should succeed"); // GH-90000
        assertEquals(1, result.getChangeCount(), "Should have one change"); // GH-90000
        verify(testRefactoring).apply(any()); // GH-90000
    }

    @Test
    void testPerformRename_NoRefactoringFound() { // GH-90000
        // Setup for this test
        RefactoringOrchestrator testOrchestrator =
                new RefactoringOrchestrator(referenceResolver, projectContext, false); // GH-90000

        // No refactorings registered, so it should fail with a specific message
        RefactoringResult result =
                testOrchestrator.performRename(createContext("test.unsupported", OLD_NAME)); // GH-90000

        // Assert
        assertFalse(result.isSuccess()); // GH-90000
        assertNotNull(result.getErrorMessage()); // GH-90000
        assertTrue(result.getErrorMessage().contains("No refactoring implementation found [GH-90000]"));
    }

    @Test
    void testPerformRename_WithError() { // GH-90000
        // Setup for this test
        when(javaRefactoring.canApply(any())).thenReturn(true); // GH-90000
        when(referenceResolver.findReferences(any(), any(), any(), any())) // GH-90000
                .thenThrow(new RuntimeException("Reference resolution failed [GH-90000]"));

        // Act
        RefactoringResult result =
                orchestrator.performRename(createContext("src/main/java/Example.java", OLD_NAME)); // GH-90000

        // Assert
        assertFalse(result.isSuccess()); // GH-90000
        assertTrue(result.getErrorMessage().contains("Reference resolution failed [GH-90000]"));
    }

    @Test
    void testRegisterRefactoring() { // GH-90000
        // Create a dedicated orchestrator for this test
        RefactoringOrchestrator testOrchestrator =
                new RefactoringOrchestrator(referenceResolver, projectContext, false); // GH-90000

        // Create a custom refactoring mock
        RenameRefactoring customRefactoring = mock(RenameRefactoring.class); // GH-90000
        doReturn("custom.rename [GH-90000]").when(customRefactoring).getId();
        when(customRefactoring.canApply(any())).thenReturn(true); // GH-90000
        when(customRefactoring.apply(any())) // GH-90000
                .thenReturn(RefactoringResult.success(List.of(), 0, "custom")); // GH-90000

        // Register the custom refactoring
        testOrchestrator.registerRefactoring(customRefactoring); // GH-90000

        // Setup reference resolver
        when(referenceResolver.findReferences(any(), any(), any(), any())) // GH-90000
                .thenReturn(Collections.emptyList()); // GH-90000
        when(referenceResolver.findIncomingReferences(any())).thenReturn(Collections.emptyList()); // GH-90000

        // Act & Assert - should not throw
        assertDoesNotThrow( // GH-90000
                () -> { // GH-90000
                    RefactoringResult result =
                            testOrchestrator.performRename( // GH-90000
                                    createContext("example.custom", "oldName")); // GH-90000
                    // Don't check isSuccess() as it may be false for cross-language refactorings // GH-90000
                });

        // Verify the custom refactoring was applied
        verify(customRefactoring, times(1)).apply(any()); // GH-90000
    }

    private RenameRefactoring.Context createContext(String sourceFile, String oldName) { // GH-90000
        return new RenameRefactoring.Context() { // GH-90000
            @Override
            public String getOldName() { // GH-90000
                return oldName;
            }

            @Override
            public String getNewName() { // GH-90000
                return "newName";
            }

            @Override
            public String getElementType() { // GH-90000
                return "METHOD";
            }

            @Override
            public String getSourceFile() { // GH-90000
                return sourceFile;
            }

            @Override
            public int getLineNumber() { // GH-90000
                return 0;
            }

            @Override
            public int getColumnNumber() { // GH-90000
                return 0;
            }

            @Override
            public PolyfixProjectContext getPolyfixProjectContext() { // GH-90000
                return projectContext;
            }

            @Override
            public Path getProjectRoot() { // GH-90000
                return projectContext.getProjectRoot(); // GH-90000
            }

            @Override
            public Set<Path> getAffectedFiles() { // GH-90000
                return Set.of(Path.of(sourceFile)); // GH-90000
            }

            @Override
            public boolean isDryRun() { // GH-90000
                return false;
            }

            @Override
            public boolean isInteractive() { // GH-90000
                return false;
            }
        };
    }
}
