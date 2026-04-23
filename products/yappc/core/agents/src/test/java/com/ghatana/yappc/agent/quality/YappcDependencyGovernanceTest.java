package com.ghatana.yappc.agent.quality;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * YAPPC dependency governance enforcement via ArchUnit.
 *
 * <p>Enforces the architectural boundaries identified in the
 * YAPPC Ecosystem Dependency Governance Report:
 * <ul>
 *   <li>No direct LangChain4J imports in YAPPC code</li>
 *   <li>No CompletableFuture in YAPPC agent code</li>
 *   <li>YAPPC must not use deprecated internal AI wrapper</li>
 *   <li>Backend API must not directly import platform core internals</li>
 *   <li>YAPPC must not depend on other products (except Data Cloud, AEP via approved paths)</li> // GH-90000
 * </ul>
 *
 * @doc.type class
 * @doc.purpose YAPPC dependency governance enforcement
 * @doc.layer product
 * @doc.pattern ArchitecturalTest
 */
@DisplayName("YAPPC Dependency Governance")
class YappcDependencyGovernanceTest {

    private static JavaClasses yappcClasses;

    @BeforeAll
    static void importClasses() { // GH-90000
        yappcClasses = new ClassFileImporter() // GH-90000
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) // GH-90000
            .importPackages("com.ghatana.yappc");
    }

    @Nested
    @DisplayName("Banned Direct Dependencies")
    class BannedDirectDependencies {

        @Test
        @DisplayName("YAPPC must not directly import LangChain4J")
        void noDirectLangChain4j() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc..")
                .should().dependOnClassesThat() // GH-90000
                .resideInAnyPackage("dev.langchain4j..")
                .because("LLM integration must go through platform:java:ai-integration, not LangChain4J directly")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }

        @Test
        @DisplayName("YAPPC agents must not use CompletableFuture")
        void noCompletableFutureInAgents() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc.agent..")
                .should().dependOnClassesThat() // GH-90000
                .areAssignableTo(CompletableFuture.class) // GH-90000
                .because("ActiveJ Promise must be used instead of CompletableFuture")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }

        @Test
        @DisplayName("YAPPC must not use deprecated AI wrapper")
        void noDeprecatedAiWrapper() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc..")
                .and().resideOutsideOfPackage("com.ghatana.yappc.ai..")
                .should().dependOnClassesThat() // GH-90000
                .haveFullyQualifiedName("com.ghatana.yappc.ai.AIIntegrationService")
                .because("Use com.ghatana.ai.AIIntegrationService (platform) directly")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cross-Product Isolation")
    class CrossProductIsolation {

        @Test
        @DisplayName("YAPPC must not depend on Guardian product")
        void noGuardianDependency() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc..")
                .should().dependOnClassesThat() // GH-90000
                .resideInAnyPackage("com.ghatana.guardian..", "com.ghatana.dcmaar..") // GH-90000
                .because("Products must not directly depend on other unrelated products")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }

        @Test
        @DisplayName("YAPPC must not depend on FlashIt or TutorPutor")
        void noSiblingProductDependency() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc..")
                .should().dependOnClassesThat() // GH-90000
                .resideInAnyPackage( // GH-90000
                    "com.ghatana.flashit..",
                    "com.ghatana.tutorputor..",
                    "com.ghatana.softwareorg..")
                .because("YAPPC must not depend on sibling products")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("Layer Boundaries")
    class LayerBoundaries {

        @Test
        @DisplayName("YAPPC core must not depend on backend API")
        void coreDoesNotDependOnApi() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAnyPackage( // GH-90000
                    "com.ghatana.yappc.agent..",
                    "com.ghatana.yappc.plugin..")
                // agent.learning is a known approved exception: PolicyLearningService uses
                // LearnedPolicy + LearnedPolicyRepository from api until they are moved to
                // a shared SPI module (tracked in AGENTIC_FRAMEWORK_HARDENING_PLAN.md) // GH-90000
                .and().resideOutsideOfPackage("com.ghatana.yappc.agent.learning..")
                .should().dependOnClassesThat() // GH-90000
                .resideInAPackage("com.ghatana.yappc.api..")
                .because("Core/SPI modules must not depend on the API layer")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }

        @Test
        @DisplayName("YAPPC plugins must not depend on agent internals")
        void pluginsDoNotDependOnAgentInternals() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc.plugin..")
                .should().dependOnClassesThat() // GH-90000
                .resideInAPackage("com.ghatana.yappc.agent..")
                .because("Plugin SPI should not be coupled to agent implementation details")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("Deprecation Enforcement")
    class DeprecationEnforcement {

        @Test
        @DisplayName("Legacy YAPPCAgentRegistry class must not exist")
        void legacyAgentRegistryClassRemoved() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc..")
                .should().haveSimpleName("YAPPCAgentRegistry")
                .because("YAPPCAgentRegistry has been removed; use YappcAgentRegistryAdapter")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }

        @Test
        @DisplayName("YAPPC code should not depend on YAPPCAgentRegistry")
        void noDeprecatedAgentRegistryDependency() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc..")
                .should().dependOnClassesThat() // GH-90000
                .haveSimpleName("YAPPCAgentRegistry")
                .because("Use YappcAgentRegistryAdapter which delegates to platform AgentRegistry")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }
    }
}
