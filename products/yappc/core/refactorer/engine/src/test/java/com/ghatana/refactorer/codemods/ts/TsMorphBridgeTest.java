package com.ghatana.refactorer.codemods.ts;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link TsMorphBridge}.
 * @doc.type class
 * @doc.purpose Handles ts morph bridge test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class TsMorphBridgeTest extends EventloopTestBase {
    private static final String TEST_TS = "test.ts";

    @TempDir static Path tempDir;
    private TsMorphBridge bridge;
    private PolyfixProjectContext context;

    @BeforeEach
    void setUp() { // GH-90000
        // Enable debug logging for tests
        try (LoggerContext ctx = (LoggerContext) LogManager.getContext(false)) { // GH-90000
            Configuration config = ctx.getConfiguration(); // GH-90000
            config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.DEBUG); // GH-90000
            ctx.updateLoggers(config); // GH-90000
        }

        this.context =
                new PolyfixProjectContext( // GH-90000
                        tempDir,
                        null,
                        List.of(), // GH-90000
                        null,
                        LogManager.getLogger(TsMorphBridgeTest.class)); // GH-90000
        this.bridge = new TsMorphBridge(context); // GH-90000
    }

    @Test
    void testIsAvailable() { // GH-90000
        // This test will pass if ts-morph is available
        boolean isAvailable = bridge.isAvailable(); // GH-90000
        System.out.println("ts-morph is " + (isAvailable ? "available" : "not available")); // GH-90000

        // We don't fail the test if not available, just log it
        if (!isAvailable) { // GH-90000
            System.out.println("Skipping ts-morph tests because ts-morph is not available [GH-90000]");
        }
    }

    @Test
    void testApplyEmptyPlan() throws ExecutionException, InterruptedException { // GH-90000
        // Create an empty plan
        TsMorphPlan plan = new TsMorphPlan(tempDir.resolve(TEST_TS)); // GH-90000

        // Apply the plan (should do nothing) // GH-90000
        runPromise(() -> bridge.apply(plan)); // GH-90000

        // No exceptions should be thrown
        assertTrue(plan.isEmpty()); // GH-90000
    }

    @Test
    void testPlanWithImports() throws Exception { // GH-90000
        if (!bridge.isAvailable()) { // GH-90000
            System.out.println("Skipping testPlanWithImports: ts-morph not available [GH-90000]");
            return;
        }

        // Create a test TypeScript file
        Path testFile = tempDir.resolve(TEST_TS); // GH-90000
        Files.writeString(testFile, "// Test file\nconsole.log('Hello, world!');"); // GH-90000

        // Create a plan to add imports
        TsMorphPlan plan = new TsMorphPlan(testFile); // GH-90000
        plan.addImport("react", List.of("useState", "useEffect"), null, false, null); // GH-90000
        plan.addImport("@/components/Button", null, "Button", false, null); // GH-90000

        // Verify the plan
        assertFalse(plan.isEmpty()); // GH-90000
        assertEquals(2, plan.getActions().size()); // GH-90000

        // Apply the plan
        runPromise(() -> bridge.apply(plan)); // GH-90000

        // Verify the file was modified
        String content = Files.readString(testFile); // GH-90000
        assertTrue(content.contains("import { useState, useEffect } from 'react' [GH-90000]"));
        assertTrue(content.contains("import Button from '@/components/Button' [GH-90000]"));
    }

    @Test
    void testPlanWithDiagnostics() { // GH-90000
        // Create a test diagnostic
        UnifiedDiagnostic diagnostic =
                UnifiedDiagnostic.builder() // GH-90000
                        .tool("typescript [GH-90000]")
                        .code("TS2304 [GH-90000]")
                        .message("Cannot find name 'React'. [GH-90000]")
                        .file(tempDir.resolve(TEST_TS)) // GH-90000
                        .startLine(1) // GH-90000
                        .startColumn(10) // GH-90000
                        .endLine(1) // GH-90000
                        .endColumn(15) // GH-90000
                        .build(); // GH-90000

        // Create a plan that fixes the diagnostic
        TsMorphPlan plan = new TsMorphPlan(tempDir.resolve(TEST_TS)); // GH-90000
        plan.addImport("react", null, "React", false, diagnostic); // GH-90000

        // Verify the diagnostic is associated with the plan
        assertEquals(1, plan.getFixedDiagnostics().size()); // GH-90000
        assertEquals(diagnostic, plan.getFixedDiagnostics().get(0)); // GH-90000
    }
}
