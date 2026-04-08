/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests enforcing that tool execution in AEP agent runtime
 * must route through {@code ToolExecutor} rather than calling tool implementations directly.
 *
 * <p>Rationale: Phase 3 introduces a canonical {@code ToolExecutor} boundary in
 * {@code platform:java:tool-runtime}. All side-effecting tool calls must pass through this
 * boundary for governance, approval, and audit. Direct use of {@code FunctionTool.invoke()},
 * custom lambda invocations, or {@code ToolRegistry} bypasses these controls.
 *
 * @doc.type class
 * @doc.purpose ArchUnit boundary test verifying ToolExecutor governance boundary
 * @doc.layer product
 * @doc.pattern TestSuite
 */
@AnalyzeClasses(
        packages = "com.ghatana.agent",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ToolExecutorBoundaryTest {

    /**
     * AEP agent runtime dispatch and runtime packages must not invoke
     * {@code FunctionTool} methods directly.
     *
     * <p>Tool execution must go through {@link com.ghatana.platform.toolruntime.ToolExecutor}
     * to ensure governance, approval gates, and audit emission are applied consistently.
     */
    @ArchTest
    static final ArchRule dispatch_must_not_call_function_tool_directly =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.ghatana.agent.dispatch..",
                            "com.ghatana.agent.runtime..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName(
                            "com.ghatana.agent.framework.tools.FunctionTool")
                    .allowEmptyShould(true)
                    .because("Tool calls from the dispatch and runtime layers must route through "
                            + "ToolExecutor (platform:java:tool-runtime) to enforce governance, "
                            + "approval gates, and audit emission. "
                            + "See docs/agent-system/AGENT_SYSTEM_MODERNIZATION_BLUEPRINT_2026-04-06.md §Phase3.");

    /**
     * Safety layer classes must import {@code ToolExecutor} rather than accessing
     * any tool sandbox or tool registry directly from the governance path.
     *
     * <p>The {@link com.ghatana.platform.toolruntime.DefaultToolExecutor} is the
     * single composition point; the sandbox is an implementation detail wired through it.
     */
    @ArchTest
    static final ArchRule safety_layer_must_not_use_tool_sandbox_directly =
            noClasses()
                    .that().resideInAPackage("com.ghatana.agent.runtime.safety..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.ghatana.platform.toolruntime.ToolSandbox")
                    .allowEmptyShould(true)
                    .because("The safety layer must use ToolExecutor as the governed entry point, "
                            + "not reach into ToolSandbox directly. "
                            + "ToolSandbox is an implementation detail of DefaultToolExecutor.");
}
