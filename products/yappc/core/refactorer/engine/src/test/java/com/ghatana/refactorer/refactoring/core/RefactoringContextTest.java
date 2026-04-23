package com.ghatana.refactorer.refactoring.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles refactoring context test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class RefactoringContextTest {

    @Test
    void testBuilder() { // GH-90000
        RefactoringContext context =
                RefactoringContext.builder() // GH-90000
                        .sourceFile("src/main/java/Example.java")
                        .oldName("oldName")
                        .newName("newName")
                        .elementType("METHOD")
                        .lineNumber(42) // GH-90000
                        .offset(100) // GH-90000
                        .dryRun(true) // GH-90000
                        .property("key1", "value1") // GH-90000
                        .property("key2", 123) // GH-90000
                        .build(); // GH-90000

        assertEquals("src/main/java/Example.java", context.getSourceFile()); // GH-90000
        assertEquals("oldName", context.getOldName()); // GH-90000
        assertEquals("newName", context.getNewName()); // GH-90000
        assertEquals("METHOD", context.getElementType()); // GH-90000
        assertEquals(42, context.getLineNumber()); // GH-90000
        assertEquals(100, context.getOffset()); // GH-90000
        assertTrue(context.isDryRun()); // GH-90000
        assertEquals("value1", context.getProperty("key1"));
        assertEquals(Integer.valueOf(123), context.getProperty("key2"));
    }

    @Test
    void testEqualsAndHashCode() { // GH-90000
        RefactoringContext context1 =
                RefactoringContext.builder() // GH-90000
                        .sourceFile("file1.java")
                        .oldName("old")
                        .newName("new")
                        .build(); // GH-90000

        RefactoringContext context2 =
                RefactoringContext.builder() // GH-90000
                        .sourceFile("file1.java")
                        .oldName("old")
                        .newName("new")
                        .build(); // GH-90000

        RefactoringContext context3 =
                RefactoringContext.builder() // GH-90000
                        .sourceFile("different.java")
                        .oldName("old")
                        .newName("new")
                        .build(); // GH-90000

        assertEquals(context1, context2); // GH-90000
        assertEquals(context1.hashCode(), context2.hashCode()); // GH-90000
        assertNotEquals(context1, context3); // GH-90000
        assertNotEquals(context1.hashCode(), context3.hashCode()); // GH-90000
    }

    @Test
    void testPropertyAccess() { // GH-90000
        RefactoringContext context =
                RefactoringContext.builder() // GH-90000
                        .sourceFile("test.java")
                        .property("stringKey", "value") // GH-90000
                        .property("intKey", 123) // GH-90000
                        .build(); // GH-90000

        assertTrue(context.hasProperty("stringKey"));
        assertFalse(context.hasProperty("nonexistent"));
        assertEquals("value", context.getProperty("stringKey"));
        assertEquals(Integer.valueOf(123), context.getProperty("intKey"));
        assertNull(context.getProperty("nonexistent"));
        assertEquals("default", context.getProperty("nonexistent", "default")); // GH-90000
    }
}
