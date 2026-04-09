package com.ghatana.refactorer.cli;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.refactorer.shared.RefactorerOperationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * @doc.type class
 * @doc.purpose Verifies typed CLI failures for invalid Polyfix configuration loading
 * @doc.layer core
 * @doc.pattern Test
 */
class PolyfixCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void buildContextWrapsInvalidConfigurationFailures() throws IOException {
        Files.writeString(tempDir.resolve("polyfix.json"), "{ invalid json }");

        assertThatThrownBy(() -> PolyfixCommand.buildContext(tempDir))
                .isInstanceOf(RefactorerOperationException.class)
                .hasMessageContaining("Failed to load configuration")
                .hasMessageContaining(tempDir.toString())
                .hasCauseInstanceOf(IOException.class);
    }
}
