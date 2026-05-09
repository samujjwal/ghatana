package com.ghatana.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals

class ProductPackValidationConventionPluginTest {

    @Test
    fun extractComplianceRuleIds_matchesSimpleAndQualifiedConstructors() {
        val text = """
            return List.of(
                new ComplianceRule("FIN-TI-001", "A", "B", Severity.HIGH, "x == true"),
                new CompliancePlugin.ComplianceRule("FIN-TI-002", "A", "B", Severity.HIGH, "x == true"),
                new com.ghatana.plugin.compliance.CompliancePlugin.ComplianceRule("FIN-TI-003", "A", "B", Severity.HIGH, "x == true")
            );
        """.trimIndent()

        val ids = extractComplianceRuleIds(text)

        assertEquals(listOf("FIN-TI-001", "FIN-TI-002", "FIN-TI-003"), ids)
    }

    @Test
    fun extractComplianceRuleIds_ignoresNonConstructorPatterns() {
        val text = """
            ComplianceRule.of("FIN-TI-004");
            builder.expression("x == true");
            new OtherRule("FIN-TI-005");
        """.trimIndent()

        val ids = extractComplianceRuleIds(text)

        assertEquals(emptyList(), ids)
    }
}
