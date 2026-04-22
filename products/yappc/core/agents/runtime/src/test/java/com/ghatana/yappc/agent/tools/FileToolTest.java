package com.ghatana.yappc.agent.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FileTool}.
 *
 * <p>All tests use JUnit 5's {@link TempDir} to avoid touching the real
 * filesystem.  FileTool is a pure-sync utility, so no {@code EventloopTestBase}
 * is needed.
 */
@DisplayName("FileTool [GH-90000]")
class FileToolTest {

    @TempDir
    Path tempDir;

    // ──────────────────────────── read ────────────────────────────

    @Test
    @DisplayName("read returns file content when the file exists [GH-90000]")
    void readReturnsFileContent() throws IOException { // GH-90000
        Path file = tempDir.resolve("hello.txt [GH-90000]");
        Files.writeString(file, "hello world"); // GH-90000

        String result = FileTool.read(file.toString()); // GH-90000

        assertThat(result).isEqualTo("hello world [GH-90000]");
    }

    @Test
    @DisplayName("read returns ERROR prefix when file does not exist [GH-90000]")
    void readReturnsErrorForMissingFile() { // GH-90000
        String missing = tempDir.resolve("no-such-file.txt [GH-90000]").toString();

        String result = FileTool.read(missing); // GH-90000

        assertThat(result).startsWith("ERROR: [GH-90000]");
    }

    @Test
    @DisplayName("read returns full multi-line content intact [GH-90000]")
    void readReturnsMultiLineContent() throws IOException { // GH-90000
        Path file = tempDir.resolve("multi.txt [GH-90000]");
        String content = "line1\nline2\nline3";
        Files.writeString(file, content); // GH-90000

        String result = FileTool.read(file.toString()); // GH-90000

        assertThat(result).isEqualTo(content); // GH-90000
    }

    // ──────────────────────────── write ────────────────────────────

    @Test
    @DisplayName("write creates a new file and returns SUCCESS prefix [GH-90000]")
    void writeCreatesNewFile() throws IOException { // GH-90000
        Path file = tempDir.resolve("out.txt [GH-90000]");

        String result = FileTool.write(file.toString(), "content"); // GH-90000

        assertThat(result).startsWith("SUCCESS: [GH-90000]");
        assertThat(Files.readString(file)).isEqualTo("content [GH-90000]");
    }

    @Test
    @DisplayName("write overwrites an existing file [GH-90000]")
    void writeOverwritesExistingFile() throws IOException { // GH-90000
        Path file = tempDir.resolve("overwrite.txt [GH-90000]");
        Files.writeString(file, "old content"); // GH-90000

        FileTool.write(file.toString(), "new content"); // GH-90000

        assertThat(Files.readString(file)).isEqualTo("new content [GH-90000]");
    }

    @Test
    @DisplayName("write returns ERROR when parent directory does not exist [GH-90000]")
    void writeReturnsErrorForMissingParent() { // GH-90000
        String deepPath = tempDir.resolve("nonexistent-dir/file.txt [GH-90000]").toString();

        String result = FileTool.write(deepPath, "content"); // GH-90000

        assertThat(result).startsWith("ERROR: [GH-90000]");
    }

    @Test
    @DisplayName("write SUCCESS message contains the target path [GH-90000]")
    void writeSuccessMessageContainsPath() { // GH-90000
        Path file = tempDir.resolve("named.txt [GH-90000]");

        String result = FileTool.write(file.toString(), "data"); // GH-90000

        assertThat(result).contains(file.toString()); // GH-90000
    }

    // ──────────────────────────── exists ────────────────────────────

    @Test
    @DisplayName("exists returns 'true' when file exists [GH-90000]")
    void existsReturnsTrueForExistingFile() throws IOException { // GH-90000
        Path file = tempDir.resolve("present.txt [GH-90000]");
        Files.createFile(file); // GH-90000

        String result = FileTool.exists(file.toString()); // GH-90000

        assertThat(result).isEqualTo("true [GH-90000]");
    }

    @Test
    @DisplayName("exists returns 'false' when path does not exist [GH-90000]")
    void existsReturnsFalseForAbsentPath() { // GH-90000
        String absent = tempDir.resolve("absent.txt [GH-90000]").toString();

        String result = FileTool.exists(absent); // GH-90000

        assertThat(result).isEqualTo("false [GH-90000]");
    }

    @Test
    @DisplayName("exists returns 'true' for a directory [GH-90000]")
    void existsReturnsTrueForDirectory() { // GH-90000
        String result = FileTool.exists(tempDir.toString()); // GH-90000

        assertThat(result).isEqualTo("true [GH-90000]");
    }

    // ──────────────────────────── list ────────────────────────────

    @Test
    @DisplayName("list returns newline-separated paths for directory entries [GH-90000]")
    void listReturnsPaths() throws IOException { // GH-90000
        Path a = tempDir.resolve("a.txt [GH-90000]");
        Path b = tempDir.resolve("b.txt [GH-90000]");
        Files.createFile(a); // GH-90000
        Files.createFile(b); // GH-90000

        String result = FileTool.list(tempDir.toString()); // GH-90000

        assertThat(result).contains(a.toString()); // GH-90000
        assertThat(result).contains(b.toString()); // GH-90000
    }

    @Test
    @DisplayName("list returns empty string for an empty directory [GH-90000]")
    void listReturnsEmptyStringForEmptyDirectory() throws IOException { // GH-90000
        Path emptyDir = tempDir.resolve("empty [GH-90000]");
        Files.createDirectory(emptyDir); // GH-90000

        String result = FileTool.list(emptyDir.toString()); // GH-90000

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("list returns ERROR prefix when path does not exist [GH-90000]")
    void listReturnsErrorForNonexistentPath() { // GH-90000
        String absent = tempDir.resolve("no-such-dir [GH-90000]").toString();

        String result = FileTool.list(absent); // GH-90000

        assertThat(result).startsWith("ERROR: [GH-90000]");
    }

    @Test
    @DisplayName("list returns ERROR prefix when path is a regular file, not a directory [GH-90000]")
    void listReturnsErrorForFilePath() throws IOException { // GH-90000
        Path file = tempDir.resolve("regular.txt [GH-90000]");
        Files.createFile(file); // GH-90000

        String result = FileTool.list(file.toString()); // GH-90000

        assertThat(result).startsWith("ERROR: [GH-90000]");
    }
}
