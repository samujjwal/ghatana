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
    void clearConfigFileOverride() { 
        System.clearProperty("config.file");
        ConfigFactory.invalidateCaches(); 
    }

    @Test
    void load_wrapsInvalidConfigurationInTypedException(@TempDir Path tempDir) 
            throws IOException {
        Path configFile = tempDir.resolve("invalid.conf");
        Files.writeString( 
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
        System.setProperty("config.file", configFile.toString()); 
        ConfigFactory.invalidateCaches(); 

        RefactorerOperationException exception =
                assertThrows(RefactorerOperationException.class, ConfigLoader::load); 

        assertEquals("Configuration loading failed", exception.getMessage()); 
        assertInstanceOf(IllegalArgumentException.class, exception.getCause()); 
        assertTrue(exception.getCause().getMessage().contains("Invalid HTTP port"));
    }
}
