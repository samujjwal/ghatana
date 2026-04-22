/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.platform.domain.Severity;

import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles rule test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class RuleTest {

    // Constants for duplicate literals
    private static final String TEST_RULE_ID = "test-rule";
    private static final String TEST_RULE_NAME = "Test Rule";

    @Test
    void testRuleCreation() { // GH-90000
        Rule rule = new Rule(TEST_RULE_ID, TEST_RULE_NAME, "This is a test rule", Severity.WARNING); // GH-90000

        assertAll( // GH-90000
                "Rule properties should match constructor arguments",
                () -> assertEquals(TEST_RULE_ID, rule.getId()), // GH-90000
                () -> assertEquals(TEST_RULE_NAME, rule.getName()), // GH-90000
                () -> assertEquals("This is a test rule", rule.getDescription()), // GH-90000
                () -> assertEquals(Severity.WARNING, rule.getSeverity())); // GH-90000
    }

    @Test
    void testToString() { // GH-90000
        Rule rule = new Rule(TEST_RULE_ID, TEST_RULE_NAME, "Description", Severity.ERROR); // GH-90000
        assertEquals(TEST_RULE_NAME + " (ERROR)", rule.toString()); // GH-90000
    }

    @Test
    void testEquality() { // GH-90000
        Rule rule1 = new Rule(TEST_RULE_ID, TEST_RULE_NAME, "Desc", Severity.INFO); // GH-90000
        Rule rule2 = new Rule(TEST_RULE_ID, "Different Name", "Different Desc", Severity.WARNING); // GH-90000

        // Rules with same ID should be considered equal regardless of other fields
        assertEquals(rule1, rule2); // GH-90000
        assertEquals(rule1.hashCode(), rule2.hashCode()); // GH-90000
    }

    @Test
    void testInequality() { // GH-90000
        Rule rule1 = new Rule("rule-1", "Rule 1", "Desc", Severity.INFO); // GH-90000
        Rule rule2 = new Rule("rule-2", "Rule 2", "Desc", Severity.INFO); // GH-90000

        assertNotEquals(rule1, rule2); // GH-90000
        assertNotEquals(rule1.hashCode(), rule2.hashCode()); // GH-90000
    }
}
