package com.ghatana.platform.testing.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Utility class for managing test resources.
 *
 * @doc.type class
 * @doc.purpose Utilities for reading, copying, and managing test resource files
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class TestResourceUtils {

    private TestResourceUtils() {
        // Utility class
    }

    /**
     * Reads a resource file as a string.
     *
     * @param resourcePath the path to the resource
     * @return the content of the resource as a string
     * @throws IOException if the resource cannot be read
     */
    public static String readResourceAsString(String resourcePath) throws IOException {
        try (InputStream is = getResourceAsStream(resourcePath)) {
            return new String(Objects.requireNonNull(is, "Resource not found: " + resourcePath)
                    .readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Gets an input stream for a resource.
     *
     * @param resourcePath the path to the resource
     * @return the input stream
     */
    public static InputStream getResourceAsStream(String resourcePath) {
        return Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourcePath);
    }

    /**
     * Creates a temporary directory with the given prefix.
     *
     * @param prefix the prefix for the directory name
     * @return the path to the created directory
     * @throws IOException if the directory cannot be created
     */
    public static Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    /**
     * Creates a temporary file with the given prefix and suffix.
     *
     * @param prefix the prefix for the file name
     * @param suffix the suffix for the file name
     * @return the path to the created file
     * @throws IOException if the file cannot be created
     */
    public static Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(prefix, suffix);
    }

    /**
     * Copies a resource to a temporary file.
     *
     * @param resourcePath the path to the resource
     * @return the path to the temporary file
     * @throws IOException if the resource cannot be read or the file cannot be written
     */
    public static Path copyResourceToTempFile(String resourcePath) throws IOException {
        String fileName = Paths.get(resourcePath).getFileName().toString();
        Path tempFile = createTempFile("test-", "-" + fileName);
        try (InputStream is = getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    /**
     * Deletes a directory and all its contents recursively.
     *
     * @param directory the directory to delete
     * @throws IOException if an I/O error occurs
     */
    public static void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + path, e);
                        }
                    });
        }
    }
}
