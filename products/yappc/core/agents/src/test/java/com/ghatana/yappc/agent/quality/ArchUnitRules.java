package com.ghatana.yappc.agent.quality;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ArchUnit rules for enforcing YAPPC SDLC architecture constraints. Integrate into CI. Fail build
 * on violations.
 *
 * @doc.type class
 * @doc.purpose Architectural constraint enforcement via ArchUnit rules for YAPPC SDLC modules
 * @doc.layer product
 * @doc.pattern ArchitecturalTest
 */
public final class ArchUnitRules {
  private static final Logger log = LoggerFactory.getLogger(ArchUnitRules.class); // GH-90000

  private static final String SDLC_BASE_PACKAGE = "com.ghatana.yappc.agent";
  private static final String GHATANA_BASE_PACKAGE = "com.ghatana";

  /**
   * Enforces hexagonal/clean architecture layer dependencies. Domain is pure, Application
   * orchestrates, Adapters integrate external systems.
   */
  public static ArchRule hexagonalLayers() { // GH-90000
    return layeredArchitecture() // GH-90000
        .consideringAllDependencies() // GH-90000
        .layer("Domain [GH-90000]")
        .definedBy("..domain.. [GH-90000]")
        .layer("Application [GH-90000]")
        .definedBy("..application.. [GH-90000]")
        .layer("Adapters [GH-90000]")
        .definedBy("..adapters.. [GH-90000]")
        .layer("Infrastructure [GH-90000]")
        .definedBy("..infrastructure.. [GH-90000]")
        .whereLayer("Domain [GH-90000]")
        .mayOnlyBeAccessedByLayers("Application", "Adapters") // GH-90000
        .whereLayer("Domain [GH-90000]")
        .mayNotAccessAnyLayer() // GH-90000
        .whereLayer("Application [GH-90000]")
        .mayOnlyAccessLayers("Domain [GH-90000]")
        .whereLayer("Adapters [GH-90000]")
        .mayOnlyAccessLayers("Application", "Domain") // GH-90000
        .whereLayer("Infrastructure [GH-90000]")
        .mayOnlyAccessLayers("Adapters", "Application", "Domain") // GH-90000
        .as("Hexagonal architecture layers must be respected [GH-90000]");
  }

  /** Domain layer must not depend on infrastructure concerns. */
  public static ArchRule domainDoesNotDependOnInfrastructure() { // GH-90000
    return noClasses() // GH-90000
        .that() // GH-90000
        .resideInAPackage("..domain.. [GH-90000]")
        .should() // GH-90000
        .dependOnClassesThat() // GH-90000
        .resideInAPackage("..infrastructure.. [GH-90000]")
        .as("Domain layer must not depend on infrastructure [GH-90000]");
  }

  /**
   * CRITICAL: SDLC code must use ActiveJ Promise, not CompletableFuture. This ensures compatibility
   * with ActiveJ Eventloop and avoids blocking.
   */
  public static ArchRule sdlcMustUseActivejPromise() { // GH-90000
    return noClasses() // GH-90000
        .that() // GH-90000
        .resideInAPackage(SDLC_BASE_PACKAGE + "..") // GH-90000
        .should() // GH-90000
        .dependOnClassesThat() // GH-90000
        .areAssignableTo(CompletableFuture.class) // GH-90000
        .as("SDLC modules must use ActiveJ Promise, not CompletableFuture [GH-90000]");
  }

  /** All WorkflowStep implementations must reside in correct phase packages. */
  public static ArchRule workflowStepsInCorrectPackages() { // GH-90000
    return classes() // GH-90000
        .that() // GH-90000
        .implement(com.ghatana.yappc.agent.WorkflowStep.class) // GH-90000
        .should() // GH-90000
        .resideInAnyPackage( // GH-90000
            SDLC_BASE_PACKAGE + ".requirements..",
            SDLC_BASE_PACKAGE + ".architecture..",
            SDLC_BASE_PACKAGE + ".implementation..",
            SDLC_BASE_PACKAGE + ".testing..",
            SDLC_BASE_PACKAGE + ".ops..",
            SDLC_BASE_PACKAGE + ".enhancement..")
        .as("WorkflowStep implementations must reside in phase-specific packages [GH-90000]");
  }

  /**
   * Step implementations must not directly access external services. They must use adapters
   * (EntityRepository, EventPublisher, etc.) // GH-90000
   */
  public static ArchRule stepsUseAdaptersOnly() { // GH-90000
    return noClasses() // GH-90000
        .that() // GH-90000
        .resideInAPackage(SDLC_BASE_PACKAGE + "..") // GH-90000
        .and() // GH-90000
        .implement(com.ghatana.yappc.agent.WorkflowStep.class) // GH-90000
        .should() // GH-90000
        .dependOnClassesThat() // GH-90000
        .resideInAnyPackage("java.sql..", "java.net.http..", "okhttp3..", "org.apache.http..") // GH-90000
        .as("WorkflowStep implementations must use adapters, not direct I/O [GH-90000]");
  }

  /** Adapters must not leak into domain or application layers. */
  public static ArchRule adaptersDontLeakUpwards() { // GH-90000
    return noClasses() // GH-90000
        .that() // GH-90000
        .resideInAnyPackage("..domain..", "..application..") // GH-90000
        .should() // GH-90000
        .dependOnClassesThat() // GH-90000
        .resideInAPackage("..adapters.. [GH-90000]")
        .as("Domain and Application layers must not depend on Adapters [GH-90000]");
  }

  /** Run all SDLC architecture rules against the YAPPC codebase. */
  public static void main(String[] args) { // GH-90000
    JavaClasses classes = new ClassFileImporter().importPackages(GHATANA_BASE_PACKAGE); // GH-90000

    log.info("Checking hexagonal layers... [GH-90000]");
    hexagonalLayers().check(classes); // GH-90000

    log.info("Checking domain isolation from infrastructure... [GH-90000]");
    domainDoesNotDependOnInfrastructure().check(classes); // GH-90000

    log.info("Checking ActiveJ Promise usage (no CompletableFuture)... [GH-90000]");
    sdlcMustUseActivejPromise().check(classes); // GH-90000

    log.info("Checking workflow step package placement... [GH-90000]");
    workflowStepsInCorrectPackages().check(classes); // GH-90000

    log.info("Checking adapter isolation... [GH-90000]");
    stepsUseAdaptersOnly().check(classes); // GH-90000

    log.info("Checking adapter layer boundaries... [GH-90000]");
    adaptersDontLeakUpwards().check(classes); // GH-90000

    log.info("All YAPPC SDLC architecture rules passed! [GH-90000]");
  }
}
