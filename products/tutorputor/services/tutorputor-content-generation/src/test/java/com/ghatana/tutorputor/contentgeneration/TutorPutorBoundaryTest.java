package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.platform.testing.arch.GhatanaBoundaryRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit fitness functions enforcing TutorPutor content-generation boundary rules.
 *
 * <h2>Covered rules</h2>
 * <ul>
 *   <li>TutorPutor must not import directly from other product namespaces.</li>
 *   <li>AI gateway implementations must not bleed into the domain layer.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit boundary tests for TutorPutor content-generation module
 * @doc.layer product
 * @doc.pattern TestSuite
 */
@AnalyzeClasses(
        packages = "com.ghatana.tutorputor",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class TutorPutorBoundaryTest {

    /**
     * TutorPutor must not depend on other product namespaces directly.
     */
    @ArchTest
    static final ArchRule tutorputor_must_not_depend_on_other_products =
            noClasses()
                    .that().resideInAPackage("com.ghatana.tutorputor..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ghatana.aep..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.yappc..",
                            "com.ghatana.flashit..",
                            "com.ghatana.dcmaar..",
                            "com.ghatana.virtualorg..",
                            "com.ghatana.softwareorg..",
                            "com.ghatana.finance.."
                    )
                    .allowEmptyShould(true)
                    .because("TutorPutor must not depend on other product modules. "
                            + "Cross-product integration belongs in platform contracts. "
                            + "See docs/MONOREPO_ARCHITECTURE.md §dependency-rules.");

    /**
     * AI gateway adapters must not leak into the domain package.
     * Domain classes must only depend on the AIGateway port interface, not OpenAI/Anthropic adapters.
     */
    @ArchTest
    static final ArchRule domain_must_not_import_ai_adapters =
            noClasses()
                    .that().resideInAPackage("com.ghatana.tutorputor.contentgeneration.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ghatana.tutorputor.contentgeneration.ai.impl..",
                            "com.ghatana.tutorputor.contentgeneration.openai..",
                            "com.ghatana.tutorputor.contentgeneration.anthropic.."
                    )
                    .allowEmptyShould(true)
                    .because("Domain must only depend on the AIGateway port interface, "
                            + "not on specific AI provider adapter implementations.");
}
