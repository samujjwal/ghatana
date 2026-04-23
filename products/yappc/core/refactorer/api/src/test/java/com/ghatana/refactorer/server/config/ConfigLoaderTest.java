package com.ghatana.refactorer.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ghatana.refactorer.shared.RefactorerOperationException;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * @doc.type class
 * @doc.purpose Verifies typed configuration loading failures for refactorer server startup
 * @doc.layer core
 * @doc.pattern Test
 */
class ConfigLoaderTest {

    @AfterEach
    void clearConfigFileOverride() { // GH-90000
        System.clearProperty("config.file");
        ConfigFactory.invalidateCaches(); // GH-90000
    }

    @Test
    void load_wrapsInvalidConfigurationInTypedException(@TempDir Path tempDir) // GH-90000
            throws IOException {
        Path configFile = tempDir.resolve("invalid.conf");
        Files.writeString( // GH-90000
                configFile,
                """
                server {
                  httpPort = 70000
                  grpcPort = 8090
                }
                auth.jwt {
                  issuer = \"issuer\"
                  audience = \"audience\"
                }
                """);
        System.setProperty("config.file", configFile.toString()); // GH-90000
        ConfigFactory.invalidateCaches(); // GH-90000

        RefactorerOperationException exception =
                assertThrows(RefactorerOperationException.class, ConfigLoader::load); // GH-90000

        assertEquals("Configuration loading failed", exception.getMessage()); // GH-90000
        assertInstanceOf(IllegalArgumentException.class, exception.getCause()); // GH-90000
        assertTrue(exception.getCause().getMessage().contains("Invalid HTTP port"));
    }
}
