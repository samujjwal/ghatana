/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.platform.domain.domain.Severity;

import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles rule test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class RuleTest {

    @Test
    void testRuleCreation() {
        Rule rule = new Rule("test-rule", "Test Rule", "This is a test rule", Severity.WARNING);

        assertAll(
                "Rule properties should match constructor arguments",
                () -> assertEquals("test-rule", rule.getId()),
                () -> assertEquals("Test Rule", rule.getName()),
                () -> assertEquals("This is a test rule", rule.getDescription()),
                () -> assertEquals(Severity.WARNING, rule.getSeverity()));
    }

    @Test
    void testToString() {
        Rule rule = new Rule("test-rule", "Test Rule", "Description", Severity.ERROR);
        assertEquals("Test Rule (ERROR)", rule.toString());
    }

    @Test
    void testEquality() {
        Rule rule1 = new Rule("test-rule", "Test Rule", "Desc", Severity.INFO);
        Rule rule2 = new Rule("test-rule", "Different Name", "Different Desc", Severity.WARNING);

        // Rules with same ID should be considered equal regardless of other fields
        assertEquals(rule1, rule2);
        assertEquals(rule1.hashCode(), rule2.hashCode());
    }

    @Test
    void testInequality() {
        Rule rule1 = new Rule("rule-1", "Rule 1", "Desc", Severity.INFO);
        Rule rule2 = new Rule("rule-2", "Rule 2", "Desc", Severity.INFO);

        assertNotEquals(rule1, rule2);
        assertNotEquals(rule1.hashCode(), rule2.hashCode());
    }
}
