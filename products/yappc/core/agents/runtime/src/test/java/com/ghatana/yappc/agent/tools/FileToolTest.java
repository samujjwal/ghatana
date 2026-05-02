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
@DisplayName("FileTool")
class FileToolTest {

    @TempDir
    Path tempDir;

    // ──────────────────────────── read ────────────────────────────

    @Test
    @DisplayName("read returns file content when the file exists")
    void readReturnsFileContent() throws IOException { 
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world"); 

        String result = FileTool.read(file.toString()); 

        assertThat(result).isEqualTo("hello world");
    }

    @Test
    @DisplayName("read returns ERROR prefix when file does not exist")
    void readReturnsErrorForMissingFile() { 
        String missing = tempDir.resolve("no-such-file.txt").toString();

        String result = FileTool.read(missing); 

        assertThat(result).startsWith("ERROR:");
    }

    @Test
    @DisplayName("read returns full multi-line content intact")
    void readReturnsMultiLineContent() throws IOException { 
        Path file = tempDir.resolve("multi.txt");
        String content = "line1\nline2\nline3";
        Files.writeString(file, content); 

        String result = FileTool.read(file.toString()); 

        assertThat(result).isEqualTo(content); 
    }

    // ──────────────────────────── write ────────────────────────────

    @Test
    @DisplayName("write creates a new file and returns SUCCESS prefix")
    void writeCreatesNewFile() throws IOException { 
        Path file = tempDir.resolve("out.txt");

        String result = FileTool.write(file.toString(), "content"); 

        assertThat(result).startsWith("SUCCESS:");
        assertThat(Files.readString(file)).isEqualTo("content");
    }

    @Test
    @DisplayName("write overwrites an existing file")
    void writeOverwritesExistingFile() throws IOException { 
        Path file = tempDir.resolve("overwrite.txt");
        Files.writeString(file, "old content"); 

        FileTool.write(file.toString(), "new content"); 

        assertThat(Files.readString(file)).isEqualTo("new content");
    }

    @Test
    @DisplayName("write returns ERROR when parent directory does not exist")
    void writeReturnsErrorForMissingParent() { 
        String deepPath = tempDir.resolve("nonexistent-dir/file.txt").toString();

        String result = FileTool.write(deepPath, "content"); 

        assertThat(result).startsWith("ERROR:");
    }

    @Test
    @DisplayName("write SUCCESS message contains the target path")
    void writeSuccessMessageContainsPath() { 
        Path file = tempDir.resolve("named.txt");

        String result = FileTool.write(file.toString(), "data"); 

        assertThat(result).contains(file.toString()); 
    }

    // ──────────────────────────── exists ────────────────────────────

    @Test
    @DisplayName("exists returns 'true' when file exists")
    void existsReturnsTrueForExistingFile() throws IOException { 
        Path file = tempDir.resolve("present.txt");
        Files.createFile(file); 

        String result = FileTool.exists(file.toString()); 

        assertThat(result).isEqualTo("true");
    }

    @Test
    @DisplayName("exists returns 'false' when path does not exist")
    void existsReturnsFalseForAbsentPath() { 
        String absent = tempDir.resolve("absent.txt").toString();

        String result = FileTool.exists(absent); 

        assertThat(result).isEqualTo("false");
    }

    @Test
    @DisplayName("exists returns 'true' for a directory")
    void existsReturnsTrueForDirectory() { 
        String result = FileTool.exists(tempDir.toString()); 

        assertThat(result).isEqualTo("true");
    }

    // ──────────────────────────── list ────────────────────────────

    @Test
    @DisplayName("list returns newline-separated paths for directory entries")
    void listReturnsPaths() throws IOException { 
        Path a = tempDir.resolve("a.txt");
        Path b = tempDir.resolve("b.txt");
        Files.createFile(a); 
        Files.createFile(b); 

        String result = FileTool.list(tempDir.toString()); 

        assertThat(result).contains(a.toString()); 
        assertThat(result).contains(b.toString()); 
    }

    @Test
    @DisplayName("list returns empty string for an empty directory")
    void listReturnsEmptyStringForEmptyDirectory() throws IOException { 
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectory(emptyDir); 

        String result = FileTool.list(emptyDir.toString()); 

        assertThat(result).isEmpty(); 
    }

    @Test
    @DisplayName("list returns ERROR prefix when path does not exist")
    void listReturnsErrorForNonexistentPath() { 
        String absent = tempDir.resolve("no-such-dir").toString();

        String result = FileTool.list(absent); 

        assertThat(result).startsWith("ERROR:");
    }

    @Test
    @DisplayName("list returns ERROR prefix when path is a regular file, not a directory")
    void listReturnsErrorForFilePath() throws IOException { 
        Path file = tempDir.resolve("regular.txt");
        Files.createFile(file); 

        String result = FileTool.list(file.toString()); 

        assertThat(result).startsWith("ERROR:");
    }
}
