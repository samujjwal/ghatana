package com.ghatana.refactorer.refactoring.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles refactoring result test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class RefactoringResultTest {

    // Constants for duplicate literals
    private static final String FILE1_JAVA = "file1.java";

    @Test
    void testSuccessResult() { // GH-90000
        RefactoringResult result =
                RefactoringResult.success( // GH-90000
                        Arrays.asList(FILE1_JAVA, "file2.java"), // GH-90000
                        5,
                        "Refactoring completed successfully");

        assertTrue(result.isSuccess()); // GH-90000
        assertEquals(2, result.getModifiedFiles().size()); // GH-90000
        assertTrue(result.getModifiedFiles().contains(FILE1_JAVA)); // GH-90000
        assertEquals(5, result.getChangeCount()); // GH-90000
        assertEquals("Refactoring completed successfully", result.getSummary()); // GH-90000
        assertNull(result.getErrorMessage()); // GH-90000
    }

    @Test
    void testFailureResult() { // GH-90000
        RefactoringResult result = RefactoringResult.failure("File not found [GH-90000]");

        assertFalse(result.isSuccess()); // GH-90000
        assertTrue(result.getModifiedFiles().isEmpty()); // GH-90000
        assertEquals(0, result.getChangeCount()); // GH-90000
        assertEquals("File not found", result.getErrorMessage()); // GH-90000
    }

    @Test
    void testPartialResult() { // GH-90000
        RefactoringResult result =
                RefactoringResult.partial( // GH-90000
                        Collections.singletonList(FILE1_JAVA), // GH-90000
                        2,
                        "Some changes were made",
                        "Failed to update some references");

        assertFalse(result.isSuccess()); // GH-90000
        assertEquals(1, result.getModifiedFiles().size()); // GH-90000
        assertEquals(2, result.getChangeCount()); // GH-90000
        assertEquals("Some changes were made", result.getSummary()); // GH-90000
        assertEquals("Failed to update some references", result.getErrorMessage()); // GH-90000
    }

    @Test
    void testBuilder() { // GH-90000
        Map<String, Object> metadata = new HashMap<>(); // GH-90000
        metadata.put("warnings", 2); // GH-90000
        metadata.put("durationMs", 150); // GH-90000

        RefactoringResult result =
                RefactoringResult.builder() // GH-90000
                        .success(true) // GH-90000
                        .addModifiedFile(FILE1_JAVA) // GH-90000
                        .addModifiedFile("file2.java [GH-90000]")
                        .changeCount(3) // GH-90000
                        .summary("Refactoring completed [GH-90000]")
                        .errorMessage(null) // GH-90000
                        .metadata(metadata) // GH-90000
                        .metadata("additionalInfo", "test") // GH-90000
                        .build(); // GH-90000

        assertTrue(result.isSuccess()); // GH-90000
        assertEquals(2, result.getModifiedFiles().size()); // GH-90000
        assertEquals(3, result.getChangeCount()); // GH-90000
        assertEquals("Refactoring completed", result.getSummary()); // GH-90000
        assertNull(result.getErrorMessage()); // GH-90000
        assertEquals(2, ((Number) result.getMetadata("warnings [GH-90000]")).intValue());
        assertEquals(150L, ((Number) result.getMetadata("durationMs [GH-90000]")).longValue());
        assertEquals("test", result.getMetadata("additionalInfo [GH-90000]"));
    }

    @Test
    void testEqualsAndHashCode() { // GH-90000
        RefactoringResult result1 =
                RefactoringResult.success(Collections.singletonList(FILE1_JAVA), 1, "Test"); // GH-90000

        RefactoringResult result2 =
                RefactoringResult.success(Collections.singletonList(FILE1_JAVA), 1, "Test"); // GH-90000

        RefactoringResult result3 = RefactoringResult.failure("Error [GH-90000]");

        assertEquals(result1, result2); // GH-90000
        assertEquals(result1.hashCode(), result2.hashCode()); // GH-90000
        assertNotEquals(result1, result3); // GH-90000
        assertNotEquals(result1.hashCode(), result3.hashCode()); // GH-90000
    }
}
