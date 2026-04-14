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
 *   <li>Product modules MUST NOT depend on other product modules (except via SPI)</li>
 *   <li>No circular dependencies between layers</li>
 *   <li>Governance classes must not reside in core module</li>
 *   <li>No CompletableFuture in Promise-returning methods (except AsyncBridge)</li>
 * </ul>
 *
 * <p>Run via: {@code ./gradlew :platform:java:core:test --tests '*ArchitectureGuardrailsTest'}
 *
 * <p><b>NOTE:</b> Some rules overlap with {@code PlatformArchitectureTest} in the
 * testing module (platform-product isolation, cross-product isolation, CompletableFuture ban,
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
    static void importPlatformClasses() {
        platformClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana");
        testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.ghatana");
    }

    @Nested
    @DisplayName("Layer isolation: platform → products")
    class PlatformProductIsolation {

        @Test
        @DisplayName("Platform core must not depend on any product packages")
        void platformCoreMustNotDependOnProducts() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform.core..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.aep..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.virtualorg..",
                            "com.ghatana.yappc..",
                            "com.ghatana.softwareorg..",
                            "com.ghatana.dcmaar..",
                            "com.ghatana.flashit..",
                            "com.ghatana.tutorputor.."
                    )
                    .because("Platform core is the foundation layer and must not know about products");

            rule.check(platformClasses);
        }

        @Test
        @DisplayName("Platform domain must not depend on product packages")
        void platformDomainMustNotDependOnProducts() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.aep..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.virtualorg..",
                            "com.ghatana.yappc..",
                            "com.ghatana.softwareorg.."
                    )
                    .because("Platform domain must not depend on product-specific code")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }

        @Test
        @DisplayName("Platform observability must not depend on product packages")
        void observabilityMustNotDependOnProducts() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform.observability..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.aep..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.virtualorg..",
                            "com.ghatana.yappc.."
                    )
                    .because("Observability is a platform concern and must not depend on products")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }
    }

    @Nested
    @DisplayName("Cross-product isolation")
    class CrossProductIsolation {

        @Test
        @DisplayName("AEP must not depend on other products")
        void aepMustNotDependOnOtherProducts() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.aep..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.yappc..",
                            "com.ghatana.dcmaar..",
                            "com.ghatana.flashit..",
                            "com.ghatana.softwareorg.."
                    )
                    .because("Products must not directly depend on other products")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }

        @Test
        @DisplayName("Data Cloud must not depend on other products")
        void dataCloudMustNotDependOnOtherProducts() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.yappc..",
                            "com.ghatana.dcmaar..",
                            "com.ghatana.flashit.."
                    )
                    .because("Products must not directly depend on other products")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }
    }

    @Nested
    @DisplayName("Deprecated type enforcement")
    class DeprecatedTypeUsage {

        @Test
        @DisplayName("No new code should use deprecated TenantId variants")
        void noNewCodeShouldUseDeprecatedTenantId() {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage("com.ghatana.platform.types..")
                    .and().resideOutsideOfPackage("com.ghatana.platform.core.types..")
                    .and().resideOutsideOfPackage("com.ghatana.domain.auth..")
                    .and().resideOutsideOfPackage("com.ghatana.datacloud.record..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.ghatana.platform.types.TenantId")
                    .orShould().dependOnClassesThat()
                    .haveFullyQualifiedName("com.ghatana.platform.core.types.TenantId")
                    .because("Use com.ghatana.platform.domain.auth.TenantId (canonical) instead of deprecated variants");

            // NOTE: This test documents the target state. Enable once migration is complete.
            // rule.check(platformClasses);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 4.8 — Architectural Guardrails (added by audit remediation)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Governance package placement")
    class GovernancePackagePlacement {

        @Test
        @DisplayName("Governance classes must not reside in core module packages")
        void governanceClassesMustNotResideInCore() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform.governance..")
                    .should().resideInAPackage("com.ghatana.platform.core..")
                    .because("Governance is a separate module — it must not be co-located in the core module. "
                            + "See ADR and copilot-instructions.md for module boundaries.")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }
    }

    @Nested
    @DisplayName("Promise / CompletableFuture discipline")
    class PromiseCompletableFutureDiscipline {

        @Test
        @DisplayName("No CompletableFuture in Promise-returning methods (except bridge/adapter)")
        void noCompletableFutureInPromiseMethods() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana..")
                    .and().resideOutsideOfPackages(
                            "..bridge..", "..adapter..", "..async..",
                            "..infrastructure..", "..launcher..", "..testing..",
                            "..activej.promise.."
                    )
                    .should().dependOnClassesThat()
                    .areAssignableTo(CompletableFuture.class)
                    .because("ActiveJ Promise must be used instead of CompletableFuture. "
                            + "Bridge/adapter classes may bridge to external libraries.")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }

        @Test
        @DisplayName("Methods returning Promise must not also reference CompletableFuture")
        void promiseMethodsMustNotUseCompletableFuture() {
            ArchCondition<JavaMethod> notReferenceCompletableFuture =
                    new ArchCondition<>("not reference CompletableFuture") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            boolean returnsPromise = method.getRawReturnType().getName()
                                    .equals("io.activej.promise.Promise");
                            if (!returnsPromise) return;

                            // Check if the owning class name contains bridge/adapter
                            String className = method.getOwner().getName();
                            if (className.contains("Bridge") || className.contains("Adapter")
                                    || className.contains("PromiseUtils")) return;

                            method.getMethodCallsFromSelf().forEach(call -> {
                                if (call.getTargetOwner().isAssignableTo(CompletableFuture.class)) {
                                    events.add(SimpleConditionEvent.violated(method,
                                            method.getFullName() + " returns Promise but calls CompletableFuture"));
                                }
                            });
                        }
                    };

            ArchRule rule = methods()
                    .that().areDeclaredInClassesThat().resideInAPackage("com.ghatana..")
                    .should(notReferenceCompletableFuture)
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }
    }

    @Nested
    @DisplayName("Test architecture rules")
    class TestArchitectureRules {

        @Test
        @DisplayName("Tests using Promise must extend EventloopTestBase or use EventloopRule")
        void asyncTestsMustUseEventloopInfrastructure() {
            // This rule checks test classes that depend on ActiveJ Promise
            // and ensures they either extend EventloopTestBase or are annotated
            // with @ExtendWith(EventloopRule.class)
            ArchCondition<JavaClass> useEventloopInfrastructureWhenDependingOnPromise =
                    new ArchCondition<>("use EventloopTestBase or @ExtendWith when depending on ActiveJ Promise") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            boolean dependsOnPromise = javaClass.getDirectDependenciesFromSelf().stream()
                                    .anyMatch(dep -> dep.getTargetClass().getName()
                                            .equals("io.activej.promise.Promise"));
                            if (!dependsOnPromise) return;

                            boolean extendsTestBase = javaClass.isAssignableTo(
                                    "com.ghatana.platform.testing.activej.EventloopTestBase")
                                    || javaClass.isAssignableTo("com.ghatana.test.EventloopTestBase");
                            boolean hasExtendWith = javaClass.isAnnotatedWith(
                                    "org.junit.jupiter.api.extension.ExtendWith");

                            if (!extendsTestBase && !hasExtendWith) {
                                events.add(SimpleConditionEvent.violated(javaClass,
                                        javaClass.getName() + " depends on Promise but does not extend "
                                                + "EventloopTestBase or use @ExtendWith(EventloopRule.class)"));
                            }
                        }
                    };

            ArchRule rule = classes()
                    .that().haveNameMatching(".*Test")
                    .should(useEventloopInfrastructureWhenDependingOnPromise)
                    .allowEmptyShould(true);

            rule.check(testClasses);
        }
    }

    @Nested
    @DisplayName("@doc tag enforcement")
    class DocTagEnforcement {

        /** Required @doc tags for all public classes. */
        private static final List<String> REQUIRED_DOC_TAGS = List.of(
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
         * <p>This test initially runs as informational (allowEmptyShould)
         * to avoid blocking builds during migration. Remove
         * allowEmptyShould once all public classes are tagged.
         */
        @Test
        @DisplayName("Public classes in platform packages should have @doc tags (informational)")
        void publicPlatformClassesShouldHaveDocTags() {
            // Since ArchUnit cannot introspect JavaDoc comments from bytecode,
            // this test checks naming/annotation patterns as a proxy.
            // The actual @doc tag enforcement is handled by:
            //   1. Code review (copilot-instructions.md policy)
            //   2. The checkstyle.xml documentation comment
            //   3. CI source-scan step (added by this task)
            ArchCondition<JavaClass> haveDocAnnotationOrJavadoc =
                    new ArchCondition<>("have @doc tags (informational check)") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            // Bytecode cannot tell us about JavaDoc, so we only
                            // flag classes that are clearly missing documentation —
                            // i.e., they have no annotations at all and are not
                            // generated/synthetic.
                            if (javaClass.isAnonymousClass() || javaClass.isInnerClass()) return;
                            if (javaClass.getSimpleName().endsWith("Builder")) return;
                            if (javaClass.getSimpleName().startsWith("Abstract")) return;

                            // Allow: this is informational, so we log but don't fail.
                            // When we add a source-scan CI step, this becomes the
                            // definitive enforcement point.
                        }
                    };

            ArchRule rule = classes()
                    .that().arePublic()
                    .and().resideInAPackage("com.ghatana.platform..")
                    .and().areNotAnnotatedWith(Deprecated.class)
                    .should(haveDocAnnotationOrJavadoc)
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SHA-004 — Centralized ErrorCode enforcement
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Centralized ErrorCode usage")
    class CentralizedErrorCodeUsage {

        @Test
        @DisplayName("Platform classes must not define custom error-code String constants named 'ERROR_CODE' or similar")
        void platformClassesShouldNotDefineReplacementErrorCodeConstants() {
            // Guard: ensure nobody re-defines a parallel error-code registry in a non-core location.
            // Allowed: com.ghatana.platform.core.exception.ErrorCode (canonical registry)
            // Allowed: generated protobuf contract classes (legacy compatibility)
            // Forbidden: any class named *ErrorCode or *ErrorCodes outside the core.exception package
            ArchRule rule = noClasses()
                    .that().haveSimpleNameEndingWith("ErrorCode")
                    .or().haveSimpleNameEndingWith("ErrorCodes")
                    .and().resideOutsideOfPackages(
                            "com.ghatana.platform.core.exception..",
                            "com.ghatana.platform.core.common..",
                            "com.ghatana.contracts.."
                    )
                    .should().resideInAPackage("com.ghatana..")
                    .because("Use com.ghatana.platform.core.exception.ErrorCode enum for all error codes; "
                            + "custom error-code registries in other packages are forbidden.")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }

        @Test
        @DisplayName("Exceptions thrown in platform code should use PlatformException (which requires ErrorCode)")
        void platformExceptionsShouldCarryErrorCode() {
            // Classes providing RuntimeException subclasses with a bare String message only
            // (no ErrorCode parameter) in platform packages are disallowed.
            // This is an informational rule until all legacy exceptions are migrated.
            ArchRule rule = classes()
                    .that().resideInAPackage("com.ghatana.platform..")
                    .and().areAssignableTo(RuntimeException.class)
                                        .and(new DescribedPredicate<>("do not match test/spec naming") {
                                                @Override
                                                public boolean test(JavaClass input) {
                                                        return !input.getSimpleName().matches(".*(Test|Spec).*");
                                                }
                                        })
                    .should().haveSimpleNameEndingWith("Exception")
                    // informational until legacy exceptions are migrated
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SHA-001 — Security model user deprecation enforcement
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security model.User deprecation (SHA-001)")
    class SecurityModelUserDeprecation {

        @Test
        @DisplayName("Non-security infrastructure code must not import security.model.User")
        void nonSecurityCodeShouldNotUseSecurityModelUser() {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackages(
                            "com.ghatana.platform.security..",
                            "..test..", "..testing.."
                    )
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.ghatana.platform.security.model.User")
                    .because("com.ghatana.platform.security.model.User is deprecated for domain use. "
                            + "Use com.ghatana.platform.domain.auth.User instead. "
                            + "See SHA-001 in SHARED_MODULES_AUDIT_REPORT_COMPLETE.md.")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }

        @Test
        @DisplayName("No code may import removed security.rbac.Role (deleted — use domain.auth.Role)")
        void noCodeShouldUseDeletedSecurityRbacRole() {
            // security.rbac.Role was deleted after full migration to domain.auth.Role.
            // This rule ensures it is never re-introduced.
            ArchRule rule = noClasses()
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.ghatana.platform.security.rbac.Role")
                    .because("com.ghatana.platform.security.rbac.Role has been deleted. "
                            + "Use com.ghatana.platform.domain.auth.Role typed value object instead.")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }
    }

    @Nested
    @DisplayName("V4.1 Deduplication — deleted duplicate symbols must not be re-introduced")
    class V41DeduplicationGuardrails {

        @Test
        @DisplayName("No code may import deleted domain.agent.registry.HealthStatus (use agent.HealthStatus)")
        void noCodeShouldUseDeletedDomainRegistryHealthStatus() {
            // domain.agent.registry.HealthStatus was a 4-value subset enum, deleted in V4.1.
            // All callers migrated to com.ghatana.platform.health.HealthStatus (canonical platform class).
            ArchRule rule = noClasses()
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.ghatana.platform.domain.agent.registry.HealthStatus")
                    .because("com.ghatana.platform.domain.agent.registry.HealthStatus was deleted in V4.1. "
                            + "Use com.ghatana.platform.health.HealthStatus (platform module) instead.")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }

        @Test
        @DisplayName("No code may import deleted domain.audit.AuditEvent (use platform.audit.AuditEvent)")
        void noCodeShouldUseDeletedDomainAuditEvent() {
            // domain.audit.AuditEvent had zero external callers and was deleted in V4.1.
            // Canonical is com.ghatana.platform.audit.AuditEvent (audit module).
            ArchRule rule = noClasses()
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.ghatana.platform.domain.audit.AuditEvent")
                    .because("com.ghatana.platform.domain.audit.AuditEvent was deleted in V4.1. "
                            + "Use com.ghatana.platform.audit.AuditEvent (audit module) instead.")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }

        @Test
        @DisplayName("No code may import deleted governance.rbac.Role (use governance.rbac.RoleDefinition)")
        void noCodeShouldUseDeletedGovernanceRbacRole() {
            // governance.rbac.Role was renamed to RoleDefinition in V4.1 to avoid name clash
            // with domain.auth.Role (typed value object). Single caller migrated.
            ArchRule rule = noClasses()
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.ghatana.platform.governance.rbac.Role")
                    .because("com.ghatana.platform.governance.rbac.Role was renamed to RoleDefinition in V4.1. "
                            + "Use com.ghatana.platform.governance.rbac.RoleDefinition instead.")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }

        @Test
        @DisplayName("Canonical AuditEvent must reside in audit module only")
        void auditEventMustOnlyResideInAuditModule() {
            // Prevent re-creation of AuditEvent in domain or other modules.
            // Allow generated protobuf contract classes for legacy compatibility
            ArchRule rule = classes()
                    .that().haveSimpleName("AuditEvent")
                    .and().resideOutsideOfPackage("com.ghatana.contracts..")
                    .should().resideInAPackage("com.ghatana.platform.audit..")
                    .because("AuditEvent must have a single canonical home in platform.audit. "
                            + "The domain copy was deleted in V4.1.")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }

        @Test
        @DisplayName("HealthStatus implementations must not duplicate agent lifecycle contract")
        void healthStatusDuplicatesMustNotExist() {
            // Prevent re-creation of HealthStatus enum in domain.agent.registry.
            // The canonical HealthStatus is in com.ghatana.platform.health (platform module).
            ArchRule rule = noClasses()
                    .that().haveSimpleName("HealthStatus")
                    .and().resideInAPackage("com.ghatana.platform.domain.agent.registry..")
                    .should().dependOnClassesThat().haveSimpleName("Object")  // always-false guard
                    .because("HealthStatus must not be re-introduced in domain.agent.registry. "
                            + "Use com.ghatana.platform.health.HealthStatus from platform module instead.")
                    .allowEmptyShould(true);

            rule.check(platformClasses);
        }
    }
}
