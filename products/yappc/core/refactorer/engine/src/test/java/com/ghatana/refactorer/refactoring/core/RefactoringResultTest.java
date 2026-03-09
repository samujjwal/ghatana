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

    @Test
    void testSuccessResult() {
        RefactoringResult result =
                RefactoringResult.success(
                        Arrays.asList("file1.java", "file2.java"),
                        5,
                        "Refactoring completed successfully");

        assertTrue(result.isSuccess());
        assertEquals(2, result.getModifiedFiles().size());
        assertTrue(result.getModifiedFiles().contains("file1.java"));
        assertEquals(5, result.getChangeCount());
        assertEquals("Refactoring completed successfully", result.getSummary());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testFailureResult() {
        RefactoringResult result = RefactoringResult.failure("File not found");

        assertFalse(result.isSuccess());
        assertTrue(result.getModifiedFiles().isEmpty());
        assertEquals(0, result.getChangeCount());
        assertEquals("File not found", result.getErrorMessage());
    }

    @Test
    void testPartialResult() {
        RefactoringResult result =
                RefactoringResult.partial(
                        Collections.singletonList("file1.java"),
                        2,
                        "Some changes were made",
                        "Failed to update some references");

        assertFalse(result.isSuccess());
        assertEquals(1, result.getModifiedFiles().size());
        assertEquals(2, result.getChangeCount());
        assertEquals("Some changes were made", result.getSummary());
        assertEquals("Failed to update some references", result.getErrorMessage());
    }

    @Test
    void testBuilder() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("warnings", 2);
        metadata.put("durationMs", 150);

        RefactoringResult result =
                RefactoringResult.builder()
                        .success(true)
                        .addModifiedFile("file1.java")
                        .addModifiedFile("file2.java")
                        .changeCount(3)
                        .summary("Refactoring completed")
                        .errorMessage(null)
                        .metadata(metadata)
                        .metadata("additionalInfo", "test")
                        .build();

        assertTrue(result.isSuccess());
        assertEquals(2, result.getModifiedFiles().size());
        assertEquals(3, result.getChangeCount());
        assertEquals("Refactoring completed", result.getSummary());
        assertNull(result.getErrorMessage());
        assertEquals(2, ((Number) result.getMetadata("warnings")).intValue());
        assertEquals(150L, ((Number) result.getMetadata("durationMs")).longValue());
        assertEquals("test", result.getMetadata("additionalInfo"));
    }

    @Test
    void testEqualsAndHashCode() {
        RefactoringResult result1 =
                RefactoringResult.success(Collections.singletonList("file1.java"), 1, "Test");

        RefactoringResult result2 =
                RefactoringResult.success(Collections.singletonList("file1.java"), 1, "Test");

        RefactoringResult result3 = RefactoringResult.failure("Error");

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1, result3);
        assertNotEquals(result1.hashCode(), result3.hashCode());
    }
}
