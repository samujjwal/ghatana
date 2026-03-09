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
    void testBuilder() {
        RefactoringContext context =
                RefactoringContext.builder()
                        .sourceFile("src/main/java/Example.java")
                        .oldName("oldName")
                        .newName("newName")
                        .elementType("METHOD")
                        .lineNumber(42)
                        .offset(100)
                        .dryRun(true)
                        .property("key1", "value1")
                        .property("key2", 123)
                        .build();

        assertEquals("src/main/java/Example.java", context.getSourceFile());
        assertEquals("oldName", context.getOldName());
        assertEquals("newName", context.getNewName());
        assertEquals("METHOD", context.getElementType());
        assertEquals(42, context.getLineNumber());
        assertEquals(100, context.getOffset());
        assertTrue(context.isDryRun());
        assertEquals("value1", context.getProperty("key1"));
        assertEquals(Integer.valueOf(123), context.getProperty("key2"));
    }

    @Test
    void testEqualsAndHashCode() {
        RefactoringContext context1 =
                RefactoringContext.builder()
                        .sourceFile("file1.java")
                        .oldName("old")
                        .newName("new")
                        .build();

        RefactoringContext context2 =
                RefactoringContext.builder()
                        .sourceFile("file1.java")
                        .oldName("old")
                        .newName("new")
                        .build();

        RefactoringContext context3 =
                RefactoringContext.builder()
                        .sourceFile("different.java")
                        .oldName("old")
                        .newName("new")
                        .build();

        assertEquals(context1, context2);
        assertEquals(context1.hashCode(), context2.hashCode());
        assertNotEquals(context1, context3);
        assertNotEquals(context1.hashCode(), context3.hashCode());
    }

    @Test
    void testPropertyAccess() {
        RefactoringContext context =
                RefactoringContext.builder()
                        .sourceFile("test.java")
                        .property("stringKey", "value")
                        .property("intKey", 123)
                        .build();

        assertTrue(context.hasProperty("stringKey"));
        assertFalse(context.hasProperty("nonexistent"));
        assertEquals("value", context.getProperty("stringKey"));
        assertEquals(Integer.valueOf(123), context.getProperty("intKey"));
        assertNull(context.getProperty("nonexistent"));
        assertEquals("default", context.getProperty("nonexistent", "default"));
    }
}
