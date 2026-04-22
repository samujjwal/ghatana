/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles polyfix config test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PolyfixConfigTest {

    // Constants for duplicate literals
    private static final String NODE = "node";
    private static final String ESLINT = "eslint";
    private static final String TSC = "tsc";
    private static final String PRETTIER = "prettier";
    private static final String RUFF = "ruff";
    private static final String BLACK = "black";
    private static final String MYPY = "mypy";
    private static final String SHELLCHECK = "shellcheck";
    private static final String SHFMT = "shfmt";
    private static final String CARGO = "cargo";
    private static final String RUSTFMT = "rustfmt";
    private static final String SEMGREP = "semgrep";

    @Test
    void testBudgetsRecord() { // GH-90000
        PolyfixConfig.Budgets budgets = new PolyfixConfig.Budgets(5, 10); // GH-90000
        assertEquals(5, budgets.maxPasses()); // GH-90000
        assertEquals(10, budgets.maxEditsPerFile()); // GH-90000

        // Test toString() // GH-90000
        assertNotNull(budgets.toString()); // GH-90000
        assertTrue(budgets.toString().contains("maxPasses=5 [GH-90000]"));
        assertTrue(budgets.toString().contains("maxEditsPerFile=10 [GH-90000]"));

        // Test equals() and hashCode() // GH-90000
        PolyfixConfig.Budgets sameBudgets = new PolyfixConfig.Budgets(5, 10); // GH-90000
        PolyfixConfig.Budgets differentBudgets = new PolyfixConfig.Budgets(1, 2); // GH-90000

        assertEquals(budgets, sameBudgets); // GH-90000
        assertNotEquals(budgets, differentBudgets); // GH-90000
        assertEquals(budgets.hashCode(), sameBudgets.hashCode()); // GH-90000
        assertNotEquals(budgets.hashCode(), differentBudgets.hashCode()); // GH-90000
    }

    @Test
    void testPoliciesRecord() { // GH-90000
        PolyfixConfig.Policies policies = new PolyfixConfig.Policies(true, false, true, false); // GH-90000
        assertTrue(policies.tsAllowTemporaryAny()); // GH-90000
        assertFalse(policies.pythonAddMissingImports()); // GH-90000
        assertTrue(policies.bashEnforceStrictMode()); // GH-90000
        assertFalse(policies.jsonAutofillRequiredDefaults()); // GH-90000

        // Test toString() // GH-90000
        assertNotNull(policies.toString()); // GH-90000
        assertTrue(policies.toString().contains("tsAllowTemporaryAny=true [GH-90000]"));
        assertTrue(policies.toString().contains("pythonAddMissingImports=false [GH-90000]"));

        // Test equals() and hashCode() // GH-90000
        PolyfixConfig.Policies samePolicies = new PolyfixConfig.Policies(true, false, true, false); // GH-90000
        PolyfixConfig.Policies differentPolicies =
                new PolyfixConfig.Policies(false, true, false, true); // GH-90000

        assertEquals(policies, samePolicies); // GH-90000
        assertNotEquals(policies, differentPolicies); // GH-90000
        assertEquals(policies.hashCode(), samePolicies.hashCode()); // GH-90000
        assertNotEquals(policies.hashCode(), differentPolicies.hashCode()); // GH-90000
    }

    @Test
    void testToolsRecord() { // GH-90000
        PolyfixConfig.Tools tools =
                new PolyfixConfig.Tools( // GH-90000
                        NODE,
                        ESLINT,
                        TSC,
                        PRETTIER,
                        RUFF,
                        BLACK,
                        MYPY,
                        SHELLCHECK,
                        SHFMT,
                        CARGO,
                        RUSTFMT,
                        SEMGREP);

        assertEquals(NODE, tools.node()); // GH-90000
        assertEquals(ESLINT, tools.eslint()); // GH-90000
        assertEquals(TSC, tools.tsc()); // GH-90000

        // Test toString() // GH-90000
        assertNotNull(tools.toString()); // GH-90000
        assertTrue(tools.toString().contains("node=node [GH-90000]"));
        assertTrue(tools.toString().contains("eslint=eslint [GH-90000]"));

        // Test equals() and hashCode() // GH-90000
        PolyfixConfig.Tools sameTools =
                new PolyfixConfig.Tools( // GH-90000
                        NODE,
                        ESLINT,
                        TSC,
                        PRETTIER,
                        RUFF,
                        BLACK,
                        MYPY,
                        SHELLCHECK,
                        SHFMT,
                        CARGO,
                        RUSTFMT,
                        SEMGREP);

        PolyfixConfig.Tools differentTools =
                new PolyfixConfig.Tools( // GH-90000
                        "node2",
                        "eslint2",
                        "tsc2",
                        "prettier2",
                        "ruff2",
                        "black2",
                        "mypy2",
                        "shellcheck2",
                        "shfmt2",
                        "cargo2",
                        "rustfmt2",
                        "semgrep2");

        assertEquals(tools, sameTools); // GH-90000
        assertNotEquals(tools, differentTools); // GH-90000
        assertEquals(tools.hashCode(), sameTools.hashCode()); // GH-90000
        assertNotEquals(tools.hashCode(), differentTools.hashCode()); // GH-90000
    }

    @Test
    void testMainRecord() { // GH-90000
        List<String> languages = List.of("java", "typescript"); // GH-90000
        List<String> schemaPaths = List.of("schemas [GH-90000]");
        PolyfixConfig.Budgets budgets = new PolyfixConfig.Budgets(5, 10); // GH-90000
        PolyfixConfig.Policies policies = new PolyfixConfig.Policies(true, true, true, false); // GH-90000
        PolyfixConfig.Tools tools =
                new PolyfixConfig.Tools( // GH-90000
                        NODE,
                        ESLINT,
                        TSC,
                        PRETTIER,
                        RUFF,
                        BLACK,
                        MYPY,
                        SHELLCHECK,
                        SHFMT,
                        CARGO,
                        RUSTFMT,
                        SEMGREP);

        PolyfixConfig config = new PolyfixConfig(languages, schemaPaths, budgets, policies, tools); // GH-90000

        assertEquals(languages, config.languages()); // GH-90000
        assertEquals(schemaPaths, config.schemaPaths()); // GH-90000
        assertEquals(budgets, config.budgets()); // GH-90000
        assertEquals(policies, config.policies()); // GH-90000
        assertEquals(tools, config.tools()); // GH-90000

        // Test toString() // GH-90000
        assertNotNull(config.toString()); // GH-90000
        assertTrue(config.toString().contains("languages=[java, typescript] [GH-90000]"));
        assertTrue(config.toString().contains("schemaPaths=[schemas] [GH-90000]"));

        // Test equals() and hashCode() // GH-90000
        PolyfixConfig sameConfig =
                new PolyfixConfig( // GH-90000
                        List.of("java", "typescript"), // GH-90000
                        List.of("schemas [GH-90000]"),
                        new PolyfixConfig.Budgets(5, 10), // GH-90000
                        new PolyfixConfig.Policies(true, true, true, false), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
                                NODE,
                                ESLINT,
                                TSC,
                                PRETTIER,
                                RUFF,
                                BLACK,
                                MYPY,
                                SHELLCHECK,
                                SHFMT,
                                CARGO,
                                RUSTFMT,
                                SEMGREP));

        PolyfixConfig differentConfig =
                new PolyfixConfig( // GH-90000
                        List.of("python [GH-90000]"),
                        List.of("schemas [GH-90000]"),
                        new PolyfixConfig.Budgets(1, 1), // GH-90000
                        new PolyfixConfig.Policies(false, false, false, true), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
                                "node2",
                                "eslint2",
                                "tsc2",
                                "prettier2",
                                "ruff2",
                                "black2",
                                "mypy2",
                                "shellcheck2",
                                "shfmt2",
                                "cargo2",
                                "rustfmt2",
                                "semgrep2"));

        assertEquals(config, sameConfig); // GH-90000
        assertNotEquals(config, differentConfig); // GH-90000
        assertEquals(config.hashCode(), sameConfig.hashCode()); // GH-90000
        assertNotEquals(config.hashCode(), differentConfig.hashCode()); // GH-90000
    }
}
