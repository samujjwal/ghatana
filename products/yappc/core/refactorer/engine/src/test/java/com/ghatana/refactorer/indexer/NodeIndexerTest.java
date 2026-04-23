package com.ghatana.refactorer.indexer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link NodeIndexer}.
 * @doc.type class
 * @doc.purpose Handles node indexer test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class NodeIndexerTest {
    private static final Logger logger = LogManager.getLogger(NodeIndexerTest.class); // GH-90000

    @TempDir Path tempDir;
    private PolyfixProjectContext context;
    private NodeIndexer nodeIndexer;
    private SymbolStore symbolStore;

    @BeforeAll
    static void setupLogging() { // GH-90000
        // Configure Log4j for testing
        ConfigurationBuilder<BuiltConfiguration> builder =
                ConfigurationBuilderFactory.newConfigurationBuilder(); // GH-90000
        builder.add(builder.newRootLogger(Level.INFO)); // GH-90000
        try (LoggerContext ctx = Configurator.initialize(builder.build())) { // GH-90000
            // Logger context will be automatically closed
        }
    }

    @BeforeEach
    void setUp() { // GH-90000
        // Setup mock PolyfixProjectContext with a real logger
        context = mock(PolyfixProjectContext.class); // GH-90000
        Logger logger = LogManager.getLogger(NodeIndexerTest.class); // GH-90000
        when(context.log()).thenReturn(logger); // GH-90000

        symbolStore = new SymbolStore(context); // GH-90000
        nodeIndexer = new NodeIndexer(context, symbolStore); // GH-90000
    }

    @Test
    void index_shouldFindExports() throws Exception { // GH-90000
        // Skip test if we can't create files in the temp directory
        if (!Files.isWritable(tempDir)) { // GH-90000
            System.out.println("Skipping test - cannot write to temp directory: " + tempDir); // GH-90000
            return;
        }

        // Enable debug logging for this test
        try (LoggerContext ctx = (LoggerContext) LogManager.getContext(false)) { // GH-90000
            org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration(); // GH-90000
            config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.DEBUG); // GH-90000
            ctx.updateLoggers(config); // GH-90000
        }

        logger.info("Starting test in directory: {}", tempDir); // GH-90000

        // Create a simple TypeScript file
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir); // GH-90000
        logger.debug("Created source directory: {}", srcDir); // GH-90000

        // Create a simple TypeScript file with exports
        String tsContent =
                """
                // Regular named export
                export function add(a: number, b: number): number { // GH-90000
                    return a + b;
                }

                // Default export
                export default function multiply(a: number, b: number): number { // GH-90000
                    return a * b;
                }

                // Named exports with different styles
                const PI = 3.14159;
                const E = 2.71828;

                export { PI, E as EulerNumber };

                // Type export
                export type Operation = (a: number, b: number) => number; // GH-90000
                """;

        Path tsFile = srcDir.resolve("math.ts");
        Files.writeString(tsFile, tsContent); // GH-90000
        logger.debug("Created TypeScript file: {}", tsFile); // GH-90000

        // Verify file was created
        assertTrue(Files.exists(tsFile), "TypeScript file should exist"); // GH-90000
        assertTrue(Files.size(tsFile) > 0, "TypeScript file should not be empty"); // GH-90000

        // Index the directory
        logger.info("Starting to index directory: {}", tempDir); // GH-90000
        nodeIndexer.index(tempDir, symbolStore); // GH-90000
        logger.info("Completed indexing directory");

        // Debug: Print all exports found
        logger.debug("All exports found:");
        symbolStore.debugPrintExports(); // GH-90000

        // Verify the exports were found
        var exports = symbolStore.findTsExports("add");
        assertFalse(exports.isEmpty(), "Should find 'add' export"); // GH-90000

        // Check for default export (multiply) // GH-90000
        exports = symbolStore.findTsExports("default");
        assertFalse(exports.isEmpty(), "Should find 'default' export"); // GH-90000

        exports = symbolStore.findTsExports("PI");
        assertFalse(exports.isEmpty(), "Should find 'PI' export"); // GH-90000

        exports = symbolStore.findTsExports("EulerNumber");
        assertFalse(exports.isEmpty(), "Should find 'EulerNumber' export"); // GH-90000
    }
}
