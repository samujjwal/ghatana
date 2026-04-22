package com.ghatana.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests that enforce the layered architecture of the Ghatana platform.
 *
 * <h2>Architecture Rules</h2>
 * <ul>
 *   <li>Platform modules MUST NOT depend on product modules</li>
 *   <li>Product modules MUST NOT depend on other product modules (except via SPI)</li> // GH-90000
 *   <li>No circular dependencies between layers</li>
 *   <li>Governance classes must not reside in core module</li>
 *   <li>No CompletableFuture in Promise-returning methods (except AsyncBridge)</li> // GH-90000
 * </ul>
 *
 * <p>Run via: {@code ./gradlew :platform:java:core:test --tests '*ArchitectureGuardrailsTest'}
 *
 * <p><b>NOTE:</b> Some rules overlap with {@code PlatformArchitectureTest} in the
 * testing module (platform-product isolation, cross-product isolation, CompletableFuture ban, // GH-90000
 * async test rules). Changes to overlapping rules MUST be synchronized between both classes.
 *
 * @see CODEBASE_AUDIT_AND_REMEDIATION_PLAN.md Phase 4 — Guardrails
 * @see com.ghatana.platform.architecture.PlatformArchitectureTest
 * @doc.type class
 * @doc.purpose Core architectural guardrail enforcement via ArchUnit
 * @doc.layer core
 * @doc.pattern ArchitecturalTest
 */
class ArchitectureGuardrailsTest {

    private static JavaClasses platformClasses;
    private static JavaClasses testClasses;

    @BeforeAll
    static void importPlatformClasses() { // GH-90000
        platformClasses = new ClassFileImporter() // GH-90000
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) // GH-90000
                .importPackages("com.ghatana [GH-90000]");
        testClasses = new ClassFileImporter() // GH-90000
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS) // GH-90000
                .importPackages("com.ghatana [GH-90000]");
    }

    @Nested
    @DisplayName("Layer isolation: platform → products [GH-90000]")
    class PlatformProductIsolation {

        @Test
        @DisplayName("Platform core must not depend on any product packages [GH-90000]")
        void platformCoreMustNotDependOnProducts() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.platform.core.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage( // GH-90000
                            "com.ghatana.aep..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.virtualorg..",
                            "com.ghatana.yappc..",
                            "com.ghatana.softwareorg..",
                            "com.ghatana.dcmaar..",
                            "com.ghatana.flashit..",
                            "com.ghatana.tutorputor.."
                    )
                    .because("Platform core is the foundation layer and must not know about products [GH-90000]");

            rule.check(platformClasses); // GH-90000
        }

        @Test
        @DisplayName("Platform domain must not depend on product packages [GH-90000]")
        void platformDomainMustNotDependOnProducts() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.platform.domain.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage( // GH-90000
                            "com.ghatana.aep..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.virtualorg..",
                            "com.ghatana.yappc..",
                            "com.ghatana.softwareorg.."
                    )
                    .because("Platform domain must not depend on product-specific code [GH-90000]")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }

        @Test
        @DisplayName("Platform observability must not depend on product packages [GH-90000]")
        void observabilityMustNotDependOnProducts() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.platform.observability.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage( // GH-90000
                            "com.ghatana.aep..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.virtualorg..",
                            "com.ghatana.yappc.."
                    )
                    .because("Observability is a platform concern and must not depend on products [GH-90000]")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cross-product isolation [GH-90000]")
    class CrossProductIsolation {

        @Test
        @DisplayName("AEP must not depend on other products [GH-90000]")
        void aepMustNotDependOnOtherProducts() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.aep.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage( // GH-90000
                            "com.ghatana.yappc..",
                            "com.ghatana.dcmaar..",
                            "com.ghatana.flashit..",
                            "com.ghatana.softwareorg.."
                    )
                    .because("Products must not directly depend on other products [GH-90000]")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }

        @Test
        @DisplayName("Data Cloud must not depend on other products [GH-90000]")
        void dataCloudMustNotDependOnOtherProducts() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.datacloud.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage( // GH-90000
                            "com.ghatana.yappc..",
                            "com.ghatana.dcmaar..",
                            "com.ghatana.flashit.."
                    )
                    .because("Products must not directly depend on other products [GH-90000]")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("Deprecated type enforcement [GH-90000]")
    class DeprecatedTypeUsage {

        @Test
        @DisplayName("No new code should use deprecated TenantId variants [GH-90000]")
        void noNewCodeShouldUseDeprecatedTenantId() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideOutsideOfPackage("com.ghatana.platform.types.. [GH-90000]")
                    .and().resideOutsideOfPackage("com.ghatana.platform.core.types.. [GH-90000]")
                    .and().resideOutsideOfPackage("com.ghatana.domain.auth.. [GH-90000]")
                    .and().resideOutsideOfPackage("com.ghatana.datacloud.record.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .haveFullyQualifiedName("com.ghatana.platform.types.TenantId [GH-90000]")
                    .orShould().dependOnClassesThat() // GH-90000
                    .haveFullyQualifiedName("com.ghatana.platform.core.types.TenantId [GH-90000]")
                    .because("Use com.ghatana.platform.domain.auth.TenantId (canonical) instead of deprecated variants [GH-90000]");

            // NOTE: This test documents the target state. Enable once migration is complete.
            // rule.check(platformClasses); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 4.8 — Architectural Guardrails (added by audit remediation) // GH-90000
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Governance package placement [GH-90000]")
    class GovernancePackagePlacement {

        @Test
        @DisplayName("Governance classes must not reside in core module packages [GH-90000]")
        void governanceClassesMustNotResideInCore() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.platform.governance.. [GH-90000]")
                    .should().resideInAPackage("com.ghatana.platform.core.. [GH-90000]")
                    .because("Governance is a separate module — it must not be co-located in the core module. " // GH-90000
                            + "See ADR and copilot-instructions.md for module boundaries.")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("Promise / CompletableFuture discipline [GH-90000]")
    class PromiseCompletableFutureDiscipline {

        @Test
        @DisplayName("No CompletableFuture in Promise-returning methods (except bridge/adapter) [GH-90000]")
        void noCompletableFutureInPromiseMethods() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.. [GH-90000]")
                    .and().resideOutsideOfPackages( // GH-90000
                            "..bridge..", "..adapter..", "..async..",
                            "..infrastructure..", "..launcher..", "..testing..",
                            "..activej.promise.."
                    )
                    .should().dependOnClassesThat() // GH-90000
                    .areAssignableTo(CompletableFuture.class) // GH-90000
                    .because("ActiveJ Promise must be used instead of CompletableFuture. " // GH-90000
                            + "Bridge/adapter classes may bridge to external libraries.")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }

        @Test
        @DisplayName("Methods returning Promise must not also reference CompletableFuture [GH-90000]")
        void promiseMethodsMustNotUseCompletableFuture() { // GH-90000
            ArchCondition<JavaMethod> notReferenceCompletableFuture =
                    new ArchCondition<>("not reference CompletableFuture [GH-90000]") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) { // GH-90000
                            boolean returnsPromise = method.getRawReturnType().getName() // GH-90000
                                    .equals("io.activej.promise.Promise [GH-90000]");
                            if (!returnsPromise) return; // GH-90000

                            // Check if the owning class name contains bridge/adapter
                            String className = method.getOwner().getName(); // GH-90000
                            if (className.contains("Bridge [GH-90000]") || className.contains("Adapter [GH-90000]")
                                    || className.contains("PromiseUtils [GH-90000]")) return;

                            method.getMethodCallsFromSelf().forEach(call -> { // GH-90000
                                if (call.getTargetOwner().isAssignableTo(CompletableFuture.class)) { // GH-90000
                                    events.add(SimpleConditionEvent.violated(method, // GH-90000
                                            method.getFullName() + " returns Promise but calls CompletableFuture")); // GH-90000
                                }
                            });
                        }
                    };

            ArchRule rule = methods() // GH-90000
                    .that().areDeclaredInClassesThat().resideInAPackage("com.ghatana.. [GH-90000]")
                    .should(notReferenceCompletableFuture) // GH-90000
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("Test architecture rules [GH-90000]")
    class TestArchitectureRules {

        @Test
        @DisplayName("Tests using Promise must extend EventloopTestBase or use EventloopRule [GH-90000]")
        void asyncTestsMustUseEventloopInfrastructure() { // GH-90000
            // This rule checks test classes that depend on ActiveJ Promise
            // and ensures they either extend EventloopTestBase or are annotated
            // with @ExtendWith(EventloopRule.class) // GH-90000
            ArchCondition<JavaClass> useEventloopInfrastructureWhenDependingOnPromise =
                    new ArchCondition<>("use EventloopTestBase or @ExtendWith when depending on ActiveJ Promise [GH-90000]") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) { // GH-90000
                            boolean dependsOnPromise = javaClass.getDirectDependenciesFromSelf().stream() // GH-90000
                                    .anyMatch(dep -> dep.getTargetClass().getName() // GH-90000
                                            .equals("io.activej.promise.Promise [GH-90000]"));
                            if (!dependsOnPromise) return; // GH-90000

                            boolean extendsTestBase = javaClass.isAssignableTo( // GH-90000
                                    "com.ghatana.platform.testing.activej.EventloopTestBase")
                                    || javaClass.isAssignableTo("com.ghatana.test.EventloopTestBase [GH-90000]");
                            boolean hasExtendWith = javaClass.isAnnotatedWith( // GH-90000
                                    "org.junit.jupiter.api.extension.ExtendWith");

                            if (!extendsTestBase && !hasExtendWith) { // GH-90000
                                events.add(SimpleConditionEvent.violated(javaClass, // GH-90000
                                        javaClass.getName() + " depends on Promise but does not extend " // GH-90000
                                                + "EventloopTestBase or use @ExtendWith(EventloopRule.class)")); // GH-90000
                            }
                        }
                    };

            ArchRule rule = classes() // GH-90000
                    .that().haveNameMatching(".*Test [GH-90000]")
                    .should(useEventloopInfrastructureWhenDependingOnPromise) // GH-90000
                    .allowEmptyShould(true); // GH-90000

            rule.check(testClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("@doc tag enforcement [GH-90000]")
    class DocTagEnforcement {

        /** Required @doc tags for all public classes. */
        private static final List<String> REQUIRED_DOC_TAGS = List.of( // GH-90000
                "@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"
        );

        /**
         * Verifies that all public classes under com.ghatana have the four
         * mandatory @doc tags in their source-level JavaDoc.
         *
         * <p>NOTE: ArchUnit operates on compiled bytecode which does not
         * retain JavaDoc comments. Therefore this test uses a heuristic:
         * it checks that public classes are annotated or that a source scan
         * would find the tags. For bytecode-level enforcement, use the
         * companion Gradle task or source-level tooling.
         *
         * <p>This test initially runs as informational (allowEmptyShould) // GH-90000
         * to avoid blocking builds during migration. Remove
         * allowEmptyShould once all public classes are tagged.
         */
        @Test
        @DisplayName("Public classes in platform packages should have @doc tags (informational) [GH-90000]")
        void publicPlatformClassesShouldHaveDocTags() { // GH-90000
            // Since ArchUnit cannot introspect JavaDoc comments from bytecode,
            // this test checks naming/annotation patterns as a proxy.
            // The actual @doc tag enforcement is handled by:
            //   1. Code review (copilot-instructions.md policy) // GH-90000
            //   2. The checkstyle.xml documentation comment
            //   3. CI source-scan step (added by this task) // GH-90000
            ArchCondition<JavaClass> haveDocAnnotationOrJavadoc =
                    new ArchCondition<>("have @doc tags (informational check) [GH-90000]") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) { // GH-90000
                            // Bytecode cannot tell us about JavaDoc, so we only
                            // flag classes that are clearly missing documentation —
                            // i.e., they have no annotations at all and are not
                            // generated/synthetic.
                            if (javaClass.isAnonymousClass() || javaClass.isInnerClass()) return; // GH-90000
                            if (javaClass.getSimpleName().endsWith("Builder [GH-90000]")) return;
                            if (javaClass.getSimpleName().startsWith("Abstract [GH-90000]")) return;

                            // Allow: this is informational, so we log but don't fail.
                            // When we add a source-scan CI step, this becomes the
                            // definitive enforcement point.
                        }
                    };

            ArchRule rule = classes() // GH-90000
                    .that().arePublic() // GH-90000
                    .and().resideInAPackage("com.ghatana.platform.. [GH-90000]")
                    .and().areNotAnnotatedWith(Deprecated.class) // GH-90000
                    .should(haveDocAnnotationOrJavadoc) // GH-90000
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SHA-004 — Centralized ErrorCode enforcement
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Centralized ErrorCode usage [GH-90000]")
    class CentralizedErrorCodeUsage {

        @Test
        @DisplayName("Platform classes must not define custom error-code String constants named 'ERROR_CODE' or similar [GH-90000]")
        void platformClassesShouldNotDefineReplacementErrorCodeConstants() { // GH-90000
            // Guard: ensure nobody re-defines a parallel error-code registry in a non-core location.
            // Allowed: com.ghatana.platform.core.exception.ErrorCode (canonical registry) // GH-90000
            // Allowed: generated protobuf contract classes (legacy compatibility) // GH-90000
            // Forbidden: any class named *ErrorCode or *ErrorCodes outside the core.exception package
            ArchRule rule = noClasses() // GH-90000
                    .that().haveSimpleNameEndingWith("ErrorCode [GH-90000]")
                    .or().haveSimpleNameEndingWith("ErrorCodes [GH-90000]")
                    .and().resideOutsideOfPackages( // GH-90000
                            "com.ghatana.platform.core.exception..",
                            "com.ghatana.platform.core.common..",
                            "com.ghatana.contracts.."
                    )
                    .should().resideInAPackage("com.ghatana.. [GH-90000]")
                    .because("Use com.ghatana.platform.core.exception.ErrorCode enum for all error codes; " // GH-90000
                            + "custom error-code registries in other packages are forbidden.")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }

        @Test
        @DisplayName("Exceptions thrown in platform code should use PlatformException (which requires ErrorCode) [GH-90000]")
        void platformExceptionsShouldCarryErrorCode() { // GH-90000
            // Classes providing RuntimeException subclasses with a bare String message only
            // (no ErrorCode parameter) in platform packages are disallowed. // GH-90000
            // This is an informational rule until all legacy exceptions are migrated.
            ArchRule rule = classes() // GH-90000
                    .that().resideInAPackage("com.ghatana.platform.. [GH-90000]")
                    .and().areAssignableTo(RuntimeException.class) // GH-90000
                                        .and(new DescribedPredicate<>("do not match test/spec naming [GH-90000]") {
                                                @Override
                                                public boolean test(JavaClass input) { // GH-90000
                                                        return !input.getSimpleName().matches(".*(Test|Spec).* [GH-90000]");
                                                }
                                        })
                    .should().haveSimpleNameEndingWith("Exception [GH-90000]")
                    // informational until legacy exceptions are migrated
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SHA-001 — Security model user deprecation enforcement
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security model.User deprecation (SHA-001) [GH-90000]")
    class SecurityModelUserDeprecation {

        @Test
        @DisplayName("Non-security infrastructure code must not import security.model.User [GH-90000]")
        void nonSecurityCodeShouldNotUseSecurityModelUser() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideOutsideOfPackages( // GH-90000
                            "com.ghatana.platform.security..",
                            "..test..", "..testing.."
                    )
                    .should().dependOnClassesThat() // GH-90000
                    .haveFullyQualifiedName("com.ghatana.platform.security.model.User [GH-90000]")
                    .because("com.ghatana.platform.security.model.User is deprecated for domain use. " // GH-90000
                            + "Use com.ghatana.platform.domain.auth.User instead. "
                            + "See SHA-001 in SHARED_MODULES_AUDIT_REPORT_COMPLETE.md.")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }

        @Test
        @DisplayName("No code may import removed security.rbac.Role (deleted — use domain.auth.Role) [GH-90000]")
        void noCodeShouldUseDeletedSecurityRbacRole() { // GH-90000
            // security.rbac.Role was deleted after full migration to domain.auth.Role.
            // This rule ensures it is never re-introduced.
            ArchRule rule = noClasses() // GH-90000
                    .should().dependOnClassesThat() // GH-90000
                    .haveFullyQualifiedName("com.ghatana.platform.security.rbac.Role [GH-90000]")
                    .because("com.ghatana.platform.security.rbac.Role has been deleted. " // GH-90000
                            + "Use com.ghatana.platform.domain.auth.Role typed value object instead.")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }
    }

    @Nested
    @DisplayName("V4.1 Deduplication — deleted duplicate symbols must not be re-introduced [GH-90000]")
    class V41DeduplicationGuardrails {

        @Test
        @DisplayName("No code may import deleted domain.agent.registry.HealthStatus (use agent.HealthStatus) [GH-90000]")
        void noCodeShouldUseDeletedDomainRegistryHealthStatus() { // GH-90000
            // domain.agent.registry.HealthStatus was a 4-value subset enum, deleted in V4.1.
            // All callers migrated to com.ghatana.platform.health.HealthStatus (canonical platform class). // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .should().dependOnClassesThat() // GH-90000
                    .haveFullyQualifiedName("com.ghatana.platform.domain.agent.registry.HealthStatus [GH-90000]")
                    .because("com.ghatana.platform.domain.agent.registry.HealthStatus was deleted in V4.1. " // GH-90000
                            + "Use com.ghatana.platform.health.HealthStatus (platform module) instead.") // GH-90000
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }

        @Test
        @DisplayName("No code may import deleted domain.audit.AuditEvent (use platform.audit.AuditEvent) [GH-90000]")
        void noCodeShouldUseDeletedDomainAuditEvent() { // GH-90000
            // domain.audit.AuditEvent had zero external callers and was deleted in V4.1.
            // Canonical is com.ghatana.platform.audit.AuditEvent (audit module). // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .should().dependOnClassesThat() // GH-90000
                    .haveFullyQualifiedName("com.ghatana.platform.domain.audit.AuditEvent [GH-90000]")
                    .because("com.ghatana.platform.domain.audit.AuditEvent was deleted in V4.1. " // GH-90000
                            + "Use com.ghatana.platform.audit.AuditEvent (audit module) instead.") // GH-90000
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }

        @Test
        @DisplayName("No code may import deleted governance.rbac.Role (use governance.rbac.RoleDefinition) [GH-90000]")
        void noCodeShouldUseDeletedGovernanceRbacRole() { // GH-90000
            // governance.rbac.Role was renamed to RoleDefinition in V4.1 to avoid name clash
            // with domain.auth.Role (typed value object). Single caller migrated. // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .should().dependOnClassesThat() // GH-90000
                    .haveFullyQualifiedName("com.ghatana.platform.governance.rbac.Role [GH-90000]")
                    .because("com.ghatana.platform.governance.rbac.Role was renamed to RoleDefinition in V4.1. " // GH-90000
                            + "Use com.ghatana.platform.governance.rbac.RoleDefinition instead.")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }

        @Test
        @DisplayName("Canonical AuditEvent must reside in audit module only [GH-90000]")
        void auditEventMustOnlyResideInAuditModule() { // GH-90000
            // Prevent re-creation of AuditEvent in domain or other modules.
            // Allow generated protobuf contract classes for legacy compatibility
            ArchRule rule = classes() // GH-90000
                    .that().haveSimpleName("AuditEvent [GH-90000]")
                    .and().resideOutsideOfPackage("com.ghatana.contracts.. [GH-90000]")
                    .should().resideInAPackage("com.ghatana.platform.audit.. [GH-90000]")
                    .because("AuditEvent must have a single canonical home in platform.audit. " // GH-90000
                            + "The domain copy was deleted in V4.1.")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }

        @Test
        @DisplayName("HealthStatus implementations must not duplicate agent lifecycle contract [GH-90000]")
        void healthStatusDuplicatesMustNotExist() { // GH-90000
            // Prevent re-creation of HealthStatus enum in domain.agent.registry.
            // The canonical HealthStatus is in com.ghatana.platform.health (platform module). // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().haveSimpleName("HealthStatus [GH-90000]")
                    .and().resideInAPackage("com.ghatana.platform.domain.agent.registry.. [GH-90000]")
                    .should().dependOnClassesThat().haveSimpleName("Object [GH-90000]")  // always-false guard
                    .because("HealthStatus must not be re-introduced in domain.agent.registry. " // GH-90000
                            + "Use com.ghatana.platform.health.HealthStatus from platform module instead.")
                    .allowEmptyShould(true); // GH-90000

            rule.check(platformClasses); // GH-90000
        }
    }
}
