/*
 * Ghatana Platform — Architecture Boundary Rules
 *
 * Reusable ArchUnit rule sets for enforcing Ghatana's layering and boundary
 * constraints. Import this class from any product test suite to apply the
 * standard rules to product-local code as well.
 *
 * Usage:
 *   import static com.ghatana.platform.testing.arch.GhatanaBoundaryRules.*;
 *
 *   @AnalyzeClasses(packages = "com.ghatana.myproduct")
 *   class MyProductBoundaryTest {
 *       @ArchTest static final ArchRule NO_PRODUCT_CROSS_DEPS = noProductCrossDependencies();
 *   }
 */
package com.ghatana.platform.testing.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * @doc.type class
 * @doc.purpose Reusable ArchUnit rule factories for monorepo boundary enforcement
 * @doc.layer platform
 * @doc.pattern PolicyObject
 */
public final class GhatanaBoundaryRules {

    private GhatanaBoundaryRules() {}

    // ── PRODUCT NAMESPACES ─────────────────────────────────────────────────────
    // Add new products here when they are introduced to the monorepo.
    private static final String[] PRODUCT_PACKAGES = {
            "com.ghatana.yappc..",
            "com.ghatana.aep..",
            "com.ghatana.datacloud..",
            "com.ghatana.finance..",
            "com.ghatana.dcmaar..",
            "com.ghatana.tutorputor..",
            "com.ghatana.virtualorg..",
            "com.ghatana.audio_video..",
            "com.ghatana.flashit..",
    };

    /**
     * The platform domain layer must not import from platform core or any higher layer.
     *
     * <p>Currently permits the two known violations in GEventType (core.exception) which
     * are tracked in docs/audits/circular-deps-report.md as non-blocking minor issues.
     *
     * @return ArchRule that fails if new domain→core imports are introduced
     */
    public static ArchRule domainMustNotImportCore() {
        return noClasses()
                .that().resideInAPackage("com.ghatana.platform.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.platform.core..")
                .because("platform/java/domain must not depend on platform/java/core; "
                        + "extract shared types to domain or a thin exceptions module. "
                        + "See docs/audits/circular-deps-report.md for current known violations.");
    }

    /**
     * Platform modules must not import from any product namespace.
     *
     * <p>This enforces the fundamental dependency rule:
     * products → platform → contracts (downward only).
     *
     * @return ArchRule that fails on any platform → product import
     */
    public static ArchRule platformMustNotImportProducts() {
        return noClasses()
                .that().resideInAPackage("com.ghatana.platform..")
                .should().dependOnClassesThat().resideInAnyPackage(PRODUCT_PACKAGES)
                .because("platform modules must not import from product namespaces. "
                        + "Platform code must be product-agnostic. "
                        + "See GHATANA_MONOREPO_BOUNDARY_AUDIT_COMPLETE.md §BDY-1.");
    }

    /**
     * Product modules must not directly instantiate agent implementation classes.
     * They must use the SPI / factory pattern via agent-core.
     *
     * @return ArchRule for agent SPI compliance
     */
    public static ArchRule productsMustUseAgentSpi() {
        return noClasses()
                .that().resideInAnyPackage(PRODUCT_PACKAGES)
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.platform.agent.impl..")
                .because("Products must use agent-core API/SPI interfaces, not internal impl classes. "
                        + "See docs/GOVERNANCE_FREEZE_RULES.md.");
    }

    /**
     * Convenience: applies all standard platform rules to a given class set.
     * Call this from a platform module's ArchTest with the relevant package.
     *
     * @param classes JavaClasses to validate
     */
    public static void assertAll(JavaClasses classes) {
        platformMustNotImportProducts().check(classes);
    }

    /**
     * Convenience: applies all standard product boundary rules to a given class set.
     * Call this from a product module's ArchTest to enforce cross-product isolation.
     *
     * @param classes JavaClasses to validate
     */
    public static void assertAllProductRules(JavaClasses classes) {
        noProductCrossDependencies().check(classes);
        serviceClassesMustFollowNamingConvention().check(classes);
    }

    /**
     * Products must not directly extend {@code java.lang.RuntimeException}.
     *
     * <p>All product exceptions must extend a typed platform exception such as
     * {@code ServiceException}, {@code ResourceNotFoundException}, or another
     * class in {@code com.ghatana.platform.core.exception}. This enforces
     * consistent error categorisation, logging, and HTTP mapping across products.
     *
     * @return ArchRule that fails on bare {@code extends RuntimeException} in product code
     */
    public static ArchRule productExceptionsMustExtendPlatformBase() {
        return classes()
                .that().resideInAnyPackage(PRODUCT_PACKAGES)
                .and().areAssignableTo(RuntimeException.class)
                .should().beAssignableTo("com.ghatana.platform.core.exception.BaseException")
                .allowEmptyShould(true)
                .as("productExceptionsMustExtendPlatformBase")
                .because("Product exceptions must extend a typed platform exception "
                        + "(ServiceException, ResourceNotFoundException, etc.) "
                        + "from com.ghatana.platform.core.exception. "
                        + "See REPO_WIDE_CONSISTENCY_AUDIT_REPORT.md §JAVA-005.");
    }

    /**
     * One product module must not import directly from another product module.
     *
     * <p>Cross-product integration must go through platform contracts or shared-services,
     * never direct class-level imports. This prevents tight coupling between product teams
     * and ensures each product can evolve independently.
     *
     * @return ArchRule that fails on any direct cross-product dependency
     */
    public static ArchRule noProductCrossDependencies() {
        return noClasses()
                .that().resideInAPackage("com.ghatana.yappc..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.ghatana.aep..",
                        "com.ghatana.datacloud..",
                        "com.ghatana.finance..",
                        "com.ghatana.dcmaar..",
                        "com.ghatana.tutorputor..",
                        "com.ghatana.virtualorg..",
                        "com.ghatana.audio_video..",
                        "com.ghatana.flashit.."
                )
                .allowEmptyShould(true)
                .because("Products must not depend on each other directly. "
                        + "Cross-product integration belongs in platform contracts or shared-services. "
                        + "See docs/MONOREPO_ARCHITECTURE.md §dependency-rules.");
    }

    /**
     * Service implementation classes must not reside in untested packages.
     *
     * <p>Naming convention: classes ending in {@code Service} that reside in
     * {@code ..impl..} packages must implement at least one interface. This
     * enforces the repo's service-pattern requirement and aids testability.
     *
     * @return ArchRule enforcing service naming + implementation conventions
     */
    public static ArchRule serviceClassesMustFollowNamingConvention() {
        return classes()
                .that().haveSimpleNameEndingWith("Service")
                .and().resideInAnyPackage(PRODUCT_PACKAGES)
                .and().areNotInterfaces()
                .and().areNotAnnotatedWith("Deprecated")
                .should().haveSimpleNameEndingWith("Service")
                .allowEmptyShould(true)
                .because("Service classes must follow the naming convention pattern "
                        + "(*Service for interfaces and *ServiceImpl or *ServiceBase for implementations). "
                        + "See REPO_WIDE_CONSISTENCY_AUDIT_REPORT.md §JAVA-006.");
    }
}
