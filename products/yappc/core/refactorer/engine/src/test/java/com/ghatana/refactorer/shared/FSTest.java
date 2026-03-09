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
    void atomicWrite_shouldWriteContentToFile(@TempDir Path tempDir) throws IOException {
        // Given
        Path targetFile = tempDir.resolve("subdir").resolve("test.txt");
        String content = "Test content for atomic write";

        // When
        FS.atomicWrite(targetFile, content);

        // Then
        assertTrue(Files.exists(targetFile));
        assertEquals(content, Files.readString(targetFile));
    }

    @Test
    void atomicWrite_shouldOverwriteExistingFile(@TempDir Path tempDir) throws IOException {
        // Given
        Path targetFile = tempDir.resolve("test.txt");
        String initialContent = "Initial content";
        String newContent = "New content";
        Files.writeString(targetFile, initialContent);

        // When
        FS.atomicWrite(targetFile, newContent);

        // Then
        assertEquals(newContent, Files.readString(targetFile));
    }

    @Test
    void sha256_shouldReturnConsistentHash() {
        // Given
        String input = "test input";
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);

        // When
        String hash1 = FS.sha256(inputBytes);
        String hash2 = FS.sha256(inputBytes);

        // Then
        assertNotNull(hash1);
        assertEquals(64, hash1.length()); // SHA-256 produces 64-character hex string
        assertEquals(hash1, hash2);
    }

    @Test
    void sha256_shouldReturnDifferentHashesForDifferentInputs() {
        // Given
        byte[] input1 = "test1".getBytes(StandardCharsets.UTF_8);
        byte[] input2 = "test2".getBytes(StandardCharsets.UTF_8);

        // When
        String hash1 = FS.sha256(input1);
        String hash2 = FS.sha256(input2);

        // Then
        assertNotEquals(hash1, hash2);
    }

    @Test
    void sha256_shouldHandleEmptyInput() {
        // Given
        byte[] emptyInput = new byte[0];

        // When
        String hash = FS.sha256(emptyInput);

        // Then
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
}
