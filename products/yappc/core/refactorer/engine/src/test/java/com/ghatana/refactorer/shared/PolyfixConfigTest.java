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
    void testBudgetsRecord() { 
        PolyfixConfig.Budgets budgets = new PolyfixConfig.Budgets(5, 10); 
        assertEquals(5, budgets.maxPasses()); 
        assertEquals(10, budgets.maxEditsPerFile()); 

        // Test toString() 
        assertNotNull(budgets.toString()); 
        assertTrue(budgets.toString().contains("maxPasses=5"));
        assertTrue(budgets.toString().contains("maxEditsPerFile=10"));

        // Test equals() and hashCode() 
        PolyfixConfig.Budgets sameBudgets = new PolyfixConfig.Budgets(5, 10); 
        PolyfixConfig.Budgets differentBudgets = new PolyfixConfig.Budgets(1, 2); 

        assertEquals(budgets, sameBudgets); 
        assertNotEquals(budgets, differentBudgets); 
        assertEquals(budgets.hashCode(), sameBudgets.hashCode()); 
        assertNotEquals(budgets.hashCode(), differentBudgets.hashCode()); 
    }

    @Test
    void testPoliciesRecord() { 
        PolyfixConfig.Policies policies = new PolyfixConfig.Policies(true, false, true, false); 
        assertTrue(policies.tsAllowTemporaryAny()); 
        assertFalse(policies.pythonAddMissingImports()); 
        assertTrue(policies.bashEnforceStrictMode()); 
        assertFalse(policies.jsonAutofillRequiredDefaults()); 

        // Test toString() 
        assertNotNull(policies.toString()); 
        assertTrue(policies.toString().contains("tsAllowTemporaryAny=true"));
        assertTrue(policies.toString().contains("pythonAddMissingImports=false"));

        // Test equals() and hashCode() 
        PolyfixConfig.Policies samePolicies = new PolyfixConfig.Policies(true, false, true, false); 
        PolyfixConfig.Policies differentPolicies =
                new PolyfixConfig.Policies(false, true, false, true); 

        assertEquals(policies, samePolicies); 
        assertNotEquals(policies, differentPolicies); 
        assertEquals(policies.hashCode(), samePolicies.hashCode()); 
        assertNotEquals(policies.hashCode(), differentPolicies.hashCode()); 
    }

    @Test
    void testToolsRecord() { 
        PolyfixConfig.Tools tools =
                new PolyfixConfig.Tools( 
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

        assertEquals(NODE, tools.node()); 
        assertEquals(ESLINT, tools.eslint()); 
        assertEquals(TSC, tools.tsc()); 

        // Test toString() 
        assertNotNull(tools.toString()); 
        assertTrue(tools.toString().contains("node=node"));
        assertTrue(tools.toString().contains("eslint=eslint"));

        // Test equals() and hashCode() 
        PolyfixConfig.Tools sameTools =
                new PolyfixConfig.Tools( 
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
                new PolyfixConfig.Tools( 
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

        assertEquals(tools, sameTools); 
        assertNotEquals(tools, differentTools); 
        assertEquals(tools.hashCode(), sameTools.hashCode()); 
        assertNotEquals(tools.hashCode(), differentTools.hashCode()); 
    }

    @Test
    void testMainRecord() { 
        List<String> languages = List.of("java", "typescript"); 
        List<String> schemaPaths = List.of("schemas");
        PolyfixConfig.Budgets budgets = new PolyfixConfig.Budgets(5, 10); 
        PolyfixConfig.Policies policies = new PolyfixConfig.Policies(true, true, true, false); 
        PolyfixConfig.Tools tools =
                new PolyfixConfig.Tools( 
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

        PolyfixConfig config = new PolyfixConfig(languages, schemaPaths, budgets, policies, tools); 

        assertEquals(languages, config.languages()); 
        assertEquals(schemaPaths, config.schemaPaths()); 
        assertEquals(budgets, config.budgets()); 
        assertEquals(policies, config.policies()); 
        assertEquals(tools, config.tools()); 

        // Test toString() 
        assertNotNull(config.toString()); 
        assertTrue(config.toString().contains("languages=[java, typescript]"));
        assertTrue(config.toString().contains("schemaPaths=[schemas]"));

        // Test equals() and hashCode() 
        PolyfixConfig sameConfig =
                new PolyfixConfig( 
                        List.of("java", "typescript"), 
                        List.of("schemas"),
                        new PolyfixConfig.Budgets(5, 10), 
                        new PolyfixConfig.Policies(true, true, true, false), 
                        new PolyfixConfig.Tools( 
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
                new PolyfixConfig( 
                        List.of("python"),
                        List.of("schemas"),
                        new PolyfixConfig.Budgets(1, 1), 
                        new PolyfixConfig.Policies(false, false, false, true), 
                        new PolyfixConfig.Tools( 
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

        assertEquals(config, sameConfig); 
        assertNotEquals(config, differentConfig); 
        assertEquals(config.hashCode(), sameConfig.hashCode()); 
        assertNotEquals(config.hashCode(), differentConfig.hashCode()); 
    }
}
