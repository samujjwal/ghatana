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
@DisplayName("YAPPC Dependency Governance [GH-90000]")
class YappcDependencyGovernanceTest {

    private static JavaClasses yappcClasses;

    @BeforeAll
    static void importClasses() { // GH-90000
        yappcClasses = new ClassFileImporter() // GH-90000
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) // GH-90000
            .importPackages("com.ghatana.yappc [GH-90000]");
    }

    @Nested
    @DisplayName("Banned Direct Dependencies [GH-90000]")
    class BannedDirectDependencies {

        @Test
        @DisplayName("YAPPC must not directly import LangChain4J [GH-90000]")
        void noDirectLangChain4j() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc.. [GH-90000]")
                .should().dependOnClassesThat() // GH-90000
                .resideInAnyPackage("dev.langchain4j.. [GH-90000]")
                .because("LLM integration must go through platform:java:ai-integration, not LangChain4J directly [GH-90000]")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }

        @Test
        @DisplayName("YAPPC agents must not use CompletableFuture [GH-90000]")
        void noCompletableFutureInAgents() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc.agent.. [GH-90000]")
                .should().dependOnClassesThat() // GH-90000
                .areAssignableTo(CompletableFuture.class) // GH-90000
                .because("ActiveJ Promise must be used instead of CompletableFuture [GH-90000]")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }

        @Test
        @DisplayName("YAPPC must not use deprecated AI wrapper [GH-90000]")
        void noDeprecatedAiWrapper() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc.. [GH-90000]")
                .and().resideOutsideOfPackage("com.ghatana.yappc.ai.. [GH-90000]")
                .should().dependOnClassesThat() // GH-90000
                .haveFullyQualifiedName("com.ghatana.yappc.ai.AIIntegrationService [GH-90000]")
                .because("Use com.ghatana.ai.AIIntegrationService (platform) directly [GH-90000]")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cross-Product Isolation [GH-90000]")
    class CrossProductIsolation {

        @Test
        @DisplayName("YAPPC must not depend on Guardian product [GH-90000]")
        void noGuardianDependency() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc.. [GH-90000]")
                .should().dependOnClassesThat() // GH-90000
                .resideInAnyPackage("com.ghatana.guardian..", "com.ghatana.dcmaar..") // GH-90000
                .because("Products must not directly depend on other unrelated products [GH-90000]")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }

        @Test
        @DisplayName("YAPPC must not depend on FlashIt or TutorPutor [GH-90000]")
        void noSiblingProductDependency() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc.. [GH-90000]")
                .should().dependOnClassesThat() // GH-90000
                .resideInAnyPackage( // GH-90000
                    "com.ghatana.flashit..",
                    "com.ghatana.tutorputor..",
                    "com.ghatana.softwareorg..")
                .because("YAPPC must not depend on sibling products [GH-90000]")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("Layer Boundaries [GH-90000]")
    class LayerBoundaries {

        @Test
        @DisplayName("YAPPC core must not depend on backend API [GH-90000]")
        void coreDoesNotDependOnApi() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAnyPackage( // GH-90000
                    "com.ghatana.yappc.agent..",
                    "com.ghatana.yappc.plugin..")
                // agent.learning is a known approved exception: PolicyLearningService uses
                // LearnedPolicy + LearnedPolicyRepository from api until they are moved to
                // a shared SPI module (tracked in AGENTIC_FRAMEWORK_HARDENING_PLAN.md) // GH-90000
                .and().resideOutsideOfPackage("com.ghatana.yappc.agent.learning.. [GH-90000]")
                .should().dependOnClassesThat() // GH-90000
                .resideInAPackage("com.ghatana.yappc.api.. [GH-90000]")
                .because("Core/SPI modules must not depend on the API layer [GH-90000]")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }

        @Test
        @DisplayName("YAPPC plugins must not depend on agent internals [GH-90000]")
        void pluginsDoNotDependOnAgentInternals() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc.plugin.. [GH-90000]")
                .should().dependOnClassesThat() // GH-90000
                .resideInAPackage("com.ghatana.yappc.agent.. [GH-90000]")
                .because("Plugin SPI should not be coupled to agent implementation details [GH-90000]")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("Deprecation Enforcement [GH-90000]")
    class DeprecationEnforcement {

        @Test
        @DisplayName("Legacy YAPPCAgentRegistry class must not exist [GH-90000]")
        void legacyAgentRegistryClassRemoved() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc.. [GH-90000]")
                .should().haveSimpleName("YAPPCAgentRegistry [GH-90000]")
                .because("YAPPCAgentRegistry has been removed; use YappcAgentRegistryAdapter [GH-90000]")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }

        @Test
        @DisplayName("YAPPC code should not depend on YAPPCAgentRegistry [GH-90000]")
        void noDeprecatedAgentRegistryDependency() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.yappc.. [GH-90000]")
                .should().dependOnClassesThat() // GH-90000
                .haveSimpleName("YAPPCAgentRegistry [GH-90000]")
                .because("Use YappcAgentRegistryAdapter which delegates to platform AgentRegistry [GH-90000]")
                .allowEmptyShould(true); // GH-90000

            rule.check(yappcClasses); // GH-90000
        }
    }
}
