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
    private static final Logger logger = LogManager.getLogger(NodeIndexerTest.class);

    @TempDir Path tempDir;
    private PolyfixProjectContext context;
    private NodeIndexer nodeIndexer;
    private SymbolStore symbolStore;

    @BeforeAll
    static void setupLogging() {
        // Configure Log4j for testing
        ConfigurationBuilder<BuiltConfiguration> builder =
                ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.add(builder.newRootLogger(Level.INFO));
        LoggerContext ctx = Configurator.initialize(builder.build());
    }

    @BeforeEach
    void setUp() {
        // Setup mock PolyfixProjectContext with a real logger
        context = mock(PolyfixProjectContext.class);
        Logger logger = LogManager.getLogger(NodeIndexerTest.class);
        when(context.log()).thenReturn(logger);

        symbolStore = new SymbolStore(context);
        nodeIndexer = new NodeIndexer(context, symbolStore);
    }

    @Test
    void index_shouldFindExports() throws Exception {
        // Skip test if we can't create files in the temp directory
        if (!Files.isWritable(tempDir)) {
            System.out.println("Skipping test - cannot write to temp directory: " + tempDir);
            return;
        }

        // Enable debug logging for this test
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
        config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
        ctx.updateLoggers(config);

        logger.info("Starting test in directory: {}", tempDir);

        // Create a simple TypeScript file
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        logger.debug("Created source directory: {}", srcDir);

        // Create a simple TypeScript file with exports
        String tsContent =
                """
                // Regular named export
                export function add(a: number, b: number): number {
                    return a + b;
                }

                // Default export
                export default function multiply(a: number, b: number): number {
                    return a * b;
                }

                // Named exports with different styles
                const PI = 3.14159;
                const E = 2.71828;

                export { PI, E as EulerNumber };

                // Type export
                export type Operation = (a: number, b: number) => number;
                """;

        Path tsFile = srcDir.resolve("math.ts");
        Files.writeString(tsFile, tsContent);
        logger.debug("Created TypeScript file: {}", tsFile);

        // Verify file was created
        assertTrue(Files.exists(tsFile), "TypeScript file should exist");
        assertTrue(Files.size(tsFile) > 0, "TypeScript file should not be empty");

        // Index the directory
        logger.info("Starting to index directory: {}", tempDir);
        nodeIndexer.index(tempDir, symbolStore);
        logger.info("Completed indexing directory");

        // Debug: Print all exports found
        logger.debug("All exports found:");
        symbolStore.debugPrintExports();

        // Verify the exports were found
        var exports = symbolStore.findTsExports("add");
        assertFalse(exports.isEmpty(), "Should find 'add' export");

        // Check for default export (multiply)
        exports = symbolStore.findTsExports("default");
        assertFalse(exports.isEmpty(), "Should find 'default' export");

        exports = symbolStore.findTsExports("PI");
        assertFalse(exports.isEmpty(), "Should find 'PI' export");

        exports = symbolStore.findTsExports("EulerNumber");
        assertFalse(exports.isEmpty(), "Should find 'EulerNumber' export");
    }
}
