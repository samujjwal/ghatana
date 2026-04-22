/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles fs test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class FSTest {

    @Test
    void atomicWrite_shouldWriteContentToFile(@TempDir Path tempDir) throws IOException { // GH-90000
        // Given
        Path targetFile = tempDir.resolve("subdir [GH-90000]").resolve("test.txt [GH-90000]");
        String content = "Test content for atomic write";

        // When
        FS.atomicWrite(targetFile, content); // GH-90000

        // Then
        assertTrue(Files.exists(targetFile)); // GH-90000
        assertEquals(content, Files.readString(targetFile)); // GH-90000
    }

    @Test
    void atomicWrite_shouldOverwriteExistingFile(@TempDir Path tempDir) throws IOException { // GH-90000
        // Given
        Path targetFile = tempDir.resolve("test.txt [GH-90000]");
        String initialContent = "Initial content";
        String newContent = "New content";
        Files.writeString(targetFile, initialContent); // GH-90000

        // When
        FS.atomicWrite(targetFile, newContent); // GH-90000

        // Then
        assertEquals(newContent, Files.readString(targetFile)); // GH-90000
    }

    @Test
    void sha256_shouldReturnConsistentHash() { // GH-90000
        // Given
        String input = "test input";
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8); // GH-90000

        // When
        String hash1 = FS.sha256(inputBytes); // GH-90000
        String hash2 = FS.sha256(inputBytes); // GH-90000

        // Then
        assertNotNull(hash1); // GH-90000
        assertEquals(64, hash1.length()); // SHA-256 produces 64-character hex string // GH-90000
        assertEquals(hash1, hash2); // GH-90000
    }

    @Test
    void sha256_shouldReturnDifferentHashesForDifferentInputs() { // GH-90000
        // Given
        byte[] input1 = "test1".getBytes(StandardCharsets.UTF_8); // GH-90000
        byte[] input2 = "test2".getBytes(StandardCharsets.UTF_8); // GH-90000

        // When
        String hash1 = FS.sha256(input1); // GH-90000
        String hash2 = FS.sha256(input2); // GH-90000

        // Then
        assertNotEquals(hash1, hash2); // GH-90000
    }

    @Test
    void sha256_shouldHandleEmptyInput() { // GH-90000
        // Given
        byte[] emptyInput = new byte[0];

        // When
        String hash = FS.sha256(emptyInput); // GH-90000

        // Then
        assertNotNull(hash); // GH-90000
        assertEquals(64, hash.length()); // GH-90000
    }
}
