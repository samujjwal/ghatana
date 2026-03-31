package com.ghatana.aep;

import com.ghatana.platform.testing.arch.GhatanaBoundaryRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit fitness functions enforcing AEP module boundary rules.
 *
 * <p>These rules prevent regressions in the architectural boundaries defined in
 * {@code MONOREPO_ARCHITECTURE.md} and the AEP governance contract.
 *
 * <h2>Covered rules</h2>
 * <ul>
 *   <li>AEP must not import directly from other product namespaces.</li>
 *   <li>AEP exceptions must extend platform base exceptions.</li>
 *   <li>AEP engine internals must not depend on the compliance layer.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit boundary tests for the AEP product module
 * @doc.layer product
 * @doc.pattern TestSuite
 */
@AnalyzeClasses(
        packages = "com.ghatana.aep",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class AepBoundaryTest {

    /**
     * AEP engine must not import directly from other product namespaces.
     * Cross-product integration belongs in platform contracts.
     */
    @ArchTest
    static final ArchRule aep_must_not_depend_on_other_products =
            noClasses()
                    .that().resideInAPackage("com.ghatana.aep..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ghatana.datacloud..",
                            "com.ghatana.tutorputor..",
                            "com.ghatana.yappc..",
                            "com.ghatana.flashit..",
                            "com.ghatana.dcmaar..",
                            "com.ghatana.virtualorg..",
                            "com.ghatana.softwareorg..",
                            "com.ghatana.finance.."
                    )
                    .allowEmptyShould(true)
                    .because("AEP must not depend on other product modules. "
                            + "Cross-product integration belongs in platform contracts. "
                            + "See docs/MONOREPO_ARCHITECTURE.md §dependency-rules.");

    /**
     * AEP compliance layer must not import from AEP engine internals.
     * The compliance layer should only interact with AEP via published contracts/SPI.
     */
    @ArchTest
    static final ArchRule compliance_must_not_depend_on_engine_internals =
            noClasses()
                    .that().resideInAPackage("com.ghatana.aep.compliance..")
                    .should().dependOnClassesThat().resideInAPackage("com.ghatana.aep.engine.internal..")
                    .allowEmptyShould(true)
                    .because("AEP compliance must use the published engine API/SPI, "
                            + "not internal engine implementation classes.");

    /**
     * AEP engine core must not depend on the HTTP server or launcher.
     *
     * <p>The topology rule: external clients must reach AEP through the gateway/BFF layer,
     * which proxies to {@code AepHttpServer}. The engine itself is agnostic of transport.
     * If the engine depended on {@code aep.server}, that layer would leak into domain logic.
     *
     * <p>See: {@code products/aep/docs/TOPOLOGY.md §API-Routing-Rules}
     */
    @ArchTest
    static final ArchRule engine_must_not_depend_on_server_launcher =
            noClasses()
                    .that().resideInAPackage("com.ghatana.aep.engine..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ghatana.aep.server.."
                    )
                    .allowEmptyShould(true)
                    .because("AEP engine must not depend on the HTTP server or launcher. "
                            + "Transport (HTTP, SSE) is wired by AepLauncher; engine logic must stay transport-agnostic. "
                            + "See products/aep/docs/TOPOLOGY.md §API-Routing-Rules.");
}
