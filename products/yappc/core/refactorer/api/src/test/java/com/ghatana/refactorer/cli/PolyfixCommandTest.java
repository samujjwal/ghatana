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
    void buildContextWrapsInvalidConfigurationFailures() throws IOException { // GH-90000
        Files.writeString(tempDir.resolve("polyfix.json [GH-90000]"), "{ invalid json }");

        assertThatThrownBy(() -> PolyfixCommand.buildContext(tempDir)) // GH-90000
                .isInstanceOf(RefactorerOperationException.class) // GH-90000
                .hasMessageContaining("Failed to load configuration [GH-90000]")
                .hasMessageContaining(tempDir.toString()) // GH-90000
                .hasCauseInstanceOf(IOException.class); // GH-90000
    }
}
