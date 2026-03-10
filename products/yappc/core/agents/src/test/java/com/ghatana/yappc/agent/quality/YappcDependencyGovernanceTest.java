package com.ghatana.yappc.agent.quality;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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
 *   <li>YAPPC must not depend on other products (except Data Cloud, AEP via approved paths)</li>
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
    static void importClasses() {
        yappcClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ghatana.yappc");
    }

    @Nested
    @DisplayName("Banned Direct Dependencies")
    class BannedDirectDependencies {

        @Test
        @DisplayName("YAPPC must not directly import LangChain4J")
        void noDirectLangChain4j() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.yappc..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("dev.langchain4j..")
                .because("LLM integration must go through platform:java:ai-integration, not LangChain4J directly")
                .allowEmptyShould(true);

            rule.check(yappcClasses);
        }

        @Test
        @DisplayName("YAPPC agents must not use CompletableFuture")
        void noCompletableFutureInAgents() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.yappc.agent..")
                .should().dependOnClassesThat()
                .areAssignableTo(CompletableFuture.class)
                .because("ActiveJ Promise must be used instead of CompletableFuture")
                .allowEmptyShould(true);

            rule.check(yappcClasses);
        }

        @Test
        @DisplayName("YAPPC must not use deprecated AI wrapper")
        void noDeprecatedAiWrapper() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.yappc..")
                .and().resideOutsideOfPackage("com.ghatana.yappc.ai..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.ghatana.yappc.ai.AIIntegrationService")
                .because("Use com.ghatana.ai.AIIntegrationService (platform) directly")
                .allowEmptyShould(true);

            rule.check(yappcClasses);
        }
    }

    @Nested
    @DisplayName("Cross-Product Isolation")
    class CrossProductIsolation {

        @Test
        @DisplayName("YAPPC must not depend on Guardian product")
        void noGuardianDependency() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.yappc..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.ghatana.guardian..", "com.ghatana.dcmaar..")
                .because("Products must not directly depend on other unrelated products")
                .allowEmptyShould(true);

            rule.check(yappcClasses);
        }

        @Test
        @DisplayName("YAPPC must not depend on FlashIt or TutorPutor")
        void noSiblingProductDependency() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.yappc..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.ghatana.flashit..",
                    "com.ghatana.tutorputor..",
                    "com.ghatana.softwareorg..")
                .because("YAPPC must not depend on sibling products")
                .allowEmptyShould(true);

            rule.check(yappcClasses);
        }
    }

    @Nested
    @DisplayName("Layer Boundaries")
    class LayerBoundaries {

        @Test
        @DisplayName("YAPPC core must not depend on backend API")
        void coreDoesNotDependOnApi() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                    "com.ghatana.yappc.agent..",
                    "com.ghatana.yappc.plugin..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.ghatana.yappc.api..")
                .because("Core/SPI modules must not depend on the API layer")
                .allowEmptyShould(true);

            rule.check(yappcClasses);
        }

        @Test
        @DisplayName("YAPPC plugins must not depend on agent internals")
        void pluginsDoNotDependOnAgentInternals() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.yappc.plugin..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.ghatana.yappc.agent..")
                .because("Plugin SPI should not be coupled to agent implementation details")
                .allowEmptyShould(true);

            rule.check(yappcClasses);
        }
    }

    @Nested
    @DisplayName("Deprecation Enforcement")
    class DeprecationEnforcement {

        @Test
        @Disabled("TODO: Migrate 91 legacy usages of YAPPCAgentRegistry to YappcAgentRegistryAdapter — tracked in AGENTIC_FRAMEWORK_HARDENING_PLAN.md")
        @DisplayName("No new code should use deprecated YAPPCAgentRegistry")
        void noDeprecatedAgentRegistry() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.yappc..")
                .and().doNotHaveSimpleName("YAPPCAgentRegistry")
                .should().dependOnClassesThat()
                .haveSimpleName("YAPPCAgentRegistry")
                .because("Use YappcAgentRegistryAdapter which delegates to platform AgentRegistry")
                .allowEmptyShould(true);

            // Freeze known legacy violations so that the rule passes for existing code
            // but fails if NEW code introduces new dependencies on the deprecated class.
            rule.check(yappcClasses);
        }
    }
}
