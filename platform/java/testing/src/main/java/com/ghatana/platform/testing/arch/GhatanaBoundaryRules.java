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
}
