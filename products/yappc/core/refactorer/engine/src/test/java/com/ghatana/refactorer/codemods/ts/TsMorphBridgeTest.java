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

    @TempDir static Path tempDir;
    private TsMorphBridge bridge;
    private PolyfixProjectContext context;

    @BeforeEach
    void setUp() {
        // Enable debug logging for tests
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
        ctx.updateLoggers(config);

        this.context =
                new PolyfixProjectContext(
                        tempDir,
                        null,
                        List.of(),
                        null,
                        LogManager.getLogger(TsMorphBridgeTest.class));
        this.bridge = new TsMorphBridge(context);
    }

    @Test
    void testIsAvailable() {
        // This test will pass if ts-morph is available
        boolean isAvailable = bridge.isAvailable();
        System.out.println("ts-morph is " + (isAvailable ? "available" : "not available"));

        // We don't fail the test if not available, just log it
        if (!isAvailable) {
            System.out.println("Skipping ts-morph tests because ts-morph is not available");
        }
    }

    @Test
    void testApplyEmptyPlan() throws ExecutionException, InterruptedException {
        // Create an empty plan
        TsMorphPlan plan = new TsMorphPlan(tempDir.resolve("test.ts"));

        // Apply the plan (should do nothing)
        runPromise(() -> bridge.apply(plan));

        // No exceptions should be thrown
        assertTrue(plan.isEmpty());
    }

    @Test
    void testPlanWithImports() throws Exception {
        if (!bridge.isAvailable()) {
            System.out.println("Skipping testPlanWithImports: ts-morph not available");
            return;
        }

        // Create a test TypeScript file
        Path testFile = tempDir.resolve("test.ts");
        Files.writeString(testFile, "// Test file\nconsole.log('Hello, world!');");

        // Create a plan to add imports
        TsMorphPlan plan = new TsMorphPlan(testFile);
        plan.addImport("react", List.of("useState", "useEffect"), null, false, null);
        plan.addImport("@/components/Button", null, "Button", false, null);

        // Verify the plan
        assertFalse(plan.isEmpty());
        assertEquals(2, plan.getActions().size());

        // Apply the plan
        runPromise(() -> bridge.apply(plan));

        // Verify the file was modified
        String content = Files.readString(testFile);
        assertTrue(content.contains("import { useState, useEffect } from 'react'"));
        assertTrue(content.contains("import Button from '@/components/Button'"));
    }

    @Test
    void testPlanWithDiagnostics() {
        // Create a test diagnostic
        UnifiedDiagnostic diagnostic =
                UnifiedDiagnostic.builder()
                        .tool("typescript")
                        .code("TS2304")
                        .message("Cannot find name 'React'.")
                        .file(tempDir.resolve("test.ts"))
                        .startLine(1)
                        .startColumn(10)
                        .endLine(1)
                        .endColumn(15)
                        .build();

        // Create a plan that fixes the diagnostic
        TsMorphPlan plan = new TsMorphPlan(tempDir.resolve("test.ts"));
        plan.addImport("react", null, "React", false, diagnostic);

        // Verify the diagnostic is associated with the plan
        assertEquals(1, plan.getFixedDiagnostics().size());
        assertEquals(diagnostic, plan.getFixedDiagnostics().get(0));
    }
}
