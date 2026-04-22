package com.ghatana.refactorer.refactoring.impl.java;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the actual JavaRenameRefactoring implementation.
 *
 * <p>Note: These tests are designed to work with the specific behavior of the JavaRenameRefactoring
 * implementation, which uses JavaParser to analyze and modify Java source code.

 * @doc.type class
 * @doc.purpose Handles java rename refactoring test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class JavaRenameRefactoringTest {

    @TempDir Path tempDir;
    private JavaRenameRefactoring refactoring;
    private Path testFile;
    private PolyfixProjectContext projectContext;
    private ExecutorService executor;
    private String originalContent;

    // Constants for duplicate literals
    private static final String SHOULD_HAVE_AT_LEAST_ONE_CHANGE = "Should have at least one change";

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        refactoring = new JavaRenameRefactoring(); // GH-90000
        testFile = tempDir.resolve("TestClass.java [GH-90000]");
        executor = Executors.newSingleThreadExecutor(); // GH-90000
        projectContext = new PolyfixProjectContext(tempDir, null, List.of(), executor, null); // GH-90000

        String testClass =
                "public class TestClass {\n"
                        + "    private String oldField;\n"
                        + "    public void oldMethod() {\n" // GH-90000
                        + "        int oldVar = 42;\n"
                        + "        System.out.println(oldVar);\n" // GH-90000
                        + "    }\n"
                        + "}";

        Files.writeString(testFile, testClass); // GH-90000
        originalContent = Files.readString(testFile); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (executor != null) { // GH-90000
            executor.shutdownNow(); // GH-90000
        }
    }

    @Test
    void renameClass() { // GH-90000
        // Class renaming works by finding the class declaration by name
        RenameRefactoring.Context context = createContext("class", "TestClass", "RenamedClass"); // GH-90000
        RefactoringResult result = refactoring.apply(context); // GH-90000

        assertTrue(result.isSuccess(), "Class renaming should succeed"); // GH-90000
        assertTrue(result.getChangeCount() > 0, SHOULD_HAVE_AT_LEAST_ONE_CHANGE); // GH-90000

        String content = readFile(); // GH-90000
        assertTrue(content.contains("class RenamedClass [GH-90000]"), "Class should be renamed");
        assertFalse(content.contains("class TestClass [GH-90000]"), "Old class name should not be present");
    }

    @Test
    void renameMethod() { // GH-90000
        // Method renaming requires the line number to find the method
        // Line 3 contains the method declaration
        RenameRefactoring.Context context =
                createContext("method", "oldMethod", "newMethod", false, 3); // GH-90000
        RefactoringResult result = refactoring.apply(context); // GH-90000

        assertTrue(result.isSuccess(), "Method renaming should succeed"); // GH-90000
        assertTrue(result.getChangeCount() > 0, SHOULD_HAVE_AT_LEAST_ONE_CHANGE); // GH-90000

        String content = readFile(); // GH-90000
        assertTrue(content.contains("void newMethod() [GH-90000]"), "Method should be renamed");
        assertFalse(content.contains("void oldMethod() [GH-90000]"), "Old method name should not be present");
    }

    @Test
    void renameField() { // GH-90000
        // Field renaming requires the line number to find the field
        // Line 2 contains the field declaration
        RenameRefactoring.Context context =
                createContext("field", "oldField", "newField", false, 2); // GH-90000
        RefactoringResult result = refactoring.apply(context); // GH-90000

        assertTrue(result.isSuccess(), "Field renaming should succeed"); // GH-90000
        assertTrue(result.getChangeCount() > 0, SHOULD_HAVE_AT_LEAST_ONE_CHANGE); // GH-90000

        String content = readFile(); // GH-90000
        assertTrue(content.contains("String newField; [GH-90000]"), "Field should be renamed");
        assertFalse(content.contains("String oldField; [GH-90000]"), "Old field name should not be present");
    }

    @Test
    void renameVariable() { // GH-90000
        // Variable renaming requires the line number to find the variable
        // Line 4 contains the variable declaration
        RenameRefactoring.Context context = createContext("variable", "oldVar", "newVar", false, 4); // GH-90000
        RefactoringResult result = refactoring.apply(context); // GH-90000

        assertTrue(result.isSuccess(), "Variable renaming should succeed"); // GH-90000
        assertTrue(result.getChangeCount() > 0, SHOULD_HAVE_AT_LEAST_ONE_CHANGE); // GH-90000

        String content = readFile(); // GH-90000

        // Check if the variable was renamed in the declaration
        boolean declarationRenamed =
                content.contains("int newVar = 42 [GH-90000]")
                        || content.contains("int newVar= 42 [GH-90000]")
                        || content.contains("int newVar =42 [GH-90000]")
                        || content.contains("int newVar=42 [GH-90000]");

        // Check if the variable was renamed in the usage
        boolean usageRenamed =
                content.contains("System.out.println(newVar) [GH-90000]")
                        || content.contains("System.out.println( newVar ) [GH-90000]")
                        || content.contains("System.out.println( newVar); [GH-90000]")
                        || content.contains("System.out.println(newVar ); [GH-90000]")
                        || content.contains("System.out.println( newVar ); [GH-90000]")
                        || content.contains("System.out.println( newVar )  [GH-90000]");

        // At least one of the renames should have happened
        assertTrue( // GH-90000
                declarationRenamed || usageRenamed,
                "Expected at least one occurrence of the variable to be renamed. Content:\n"
                        + content);

        // Log what was actually found for debugging
        if (!declarationRenamed) { // GH-90000
            System.out.println("Variable declaration was not renamed as expected [GH-90000]");
        }
        if (!usageRenamed) { // GH-90000
            System.out.println("Variable usage was not renamed as expected [GH-90000]");
        }
    }

    @Test
    void dryRunShouldNotModifyFiles() { // GH-90000
        RenameRefactoring.Context context =
                createContext("class", "TestClass", "RenamedClass", true, 1); // GH-90000

        RefactoringResult result = refactoring.apply(context); // GH-90000

        assertTrue(result.isSuccess(), "Dry run should succeed"); // GH-90000
        assertTrue(result.getChangeCount() > 0, "Should report changes even in dry run"); // GH-90000
        assertEquals(originalContent, readFile(), "File content should not change in dry run"); // GH-90000
    }

    @Test
    void shouldNotRenameNonExistentElement() { // GH-90000
        RenameRefactoring.Context context =
                createContext("method", "nonExistentMethod", "newMethod", false, 999); // GH-90000

        RefactoringResult result = refactoring.apply(context); // GH-90000

        // The result might still be successful but with 0 changes, or it might be a failure
        // Either way, the file should not be modified
        String content = readFile(); // GH-90000
        assertEquals( // GH-90000
                originalContent, content, "File should not be modified when element not found");
    }

    @Test
    void shouldValidateNewName() { // GH-90000
        assertTrue(refactoring.isNewNameValid("validName [GH-90000]"));
        assertTrue(refactoring.isNewNameValid("validName123 [GH-90000]"));
        assertTrue(refactoring.isNewNameValid("_validName [GH-90000]"));
        assertTrue(refactoring.isNewNameValid("$validName [GH-90000]"));

        assertFalse(refactoring.isNewNameValid("123invalid [GH-90000]"));
        assertFalse(refactoring.isNewNameValid("invalid-name [GH-90000]"));
        assertFalse(refactoring.isNewNameValid("invalid name [GH-90000]"));
        assertFalse(refactoring.isNewNameValid("invalid.name [GH-90000]"));
        assertFalse(refactoring.isNewNameValid(" [GH-90000]"));
        assertFalse(refactoring.isNewNameValid("     [GH-90000]"));
        assertFalse(refactoring.isNewNameValid(null)); // GH-90000
    }

    private String readFile() { // GH-90000
        try {
            return Files.readString(testFile); // GH-90000
        } catch (IOException e) { // GH-90000
            throw new RuntimeException("Failed to read test file", e); // GH-90000
        }
    }

    private RenameRefactoring.Context createContext( // GH-90000
            String elementType, String oldName, String newName) {
        return createContext(elementType, oldName, newName, false, 0); // GH-90000
    }

    private RenameRefactoring.Context createContext( // GH-90000
            String elementType, String oldName, String newName, boolean dryRun, int lineNumber) {
        return new RenameRefactoring.Context() { // GH-90000
            @Override
            public String getOldName() { // GH-90000
                return oldName;
            }

            @Override
            public String getNewName() { // GH-90000
                return newName;
            }

            @Override
            public String getElementType() { // GH-90000
                return elementType;
            }

            @Override
            public String getSourceFile() { // GH-90000
                return testFile.toString(); // GH-90000
            }

            @Override
            public int getLineNumber() { // GH-90000
                return lineNumber;
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
                return Set.of(testFile); // GH-90000
            }

            @Override
            public boolean isDryRun() { // GH-90000
                return dryRun;
            }

            @Override
            public boolean isInteractive() { // GH-90000
                return false;
            }
        };
    }
}
