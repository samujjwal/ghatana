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
        .layer("Domain")
        .definedBy("..domain..")
        .layer("Application")
        .definedBy("..application..")
        .layer("Adapters")
        .definedBy("..adapters..")
        .layer("Infrastructure")
        .definedBy("..infrastructure..")
        .whereLayer("Domain")
        .mayOnlyBeAccessedByLayers("Application", "Adapters") // GH-90000
        .whereLayer("Domain")
        .mayNotAccessAnyLayer() // GH-90000
        .whereLayer("Application")
        .mayOnlyAccessLayers("Domain")
        .whereLayer("Adapters")
        .mayOnlyAccessLayers("Application", "Domain") // GH-90000
        .whereLayer("Infrastructure")
        .mayOnlyAccessLayers("Adapters", "Application", "Domain") // GH-90000
        .as("Hexagonal architecture layers must be respected");
  }

  /** Domain layer must not depend on infrastructure concerns. */
  public static ArchRule domainDoesNotDependOnInfrastructure() { // GH-90000
    return noClasses() // GH-90000
        .that() // GH-90000
        .resideInAPackage("..domain..")
        .should() // GH-90000
        .dependOnClassesThat() // GH-90000
        .resideInAPackage("..infrastructure..")
        .as("Domain layer must not depend on infrastructure");
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
        .as("SDLC modules must use ActiveJ Promise, not CompletableFuture");
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
        .as("WorkflowStep implementations must reside in phase-specific packages");
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
        .as("WorkflowStep implementations must use adapters, not direct I/O");
  }

  /** Adapters must not leak into domain or application layers. */
  public static ArchRule adaptersDontLeakUpwards() { // GH-90000
    return noClasses() // GH-90000
        .that() // GH-90000
        .resideInAnyPackage("..domain..", "..application..") // GH-90000
        .should() // GH-90000
        .dependOnClassesThat() // GH-90000
        .resideInAPackage("..adapters..")
        .as("Domain and Application layers must not depend on Adapters");
  }

  /** Run all SDLC architecture rules against the YAPPC codebase. */
  public static void main(String[] args) { // GH-90000
    JavaClasses classes = new ClassFileImporter().importPackages(GHATANA_BASE_PACKAGE); // GH-90000

    log.info("Checking hexagonal layers...");
    hexagonalLayers().check(classes); // GH-90000

    log.info("Checking domain isolation from infrastructure...");
    domainDoesNotDependOnInfrastructure().check(classes); // GH-90000

    log.info("Checking ActiveJ Promise usage (no CompletableFuture)...");
    sdlcMustUseActivejPromise().check(classes); // GH-90000

    log.info("Checking workflow step package placement...");
    workflowStepsInCorrectPackages().check(classes); // GH-90000

    log.info("Checking adapter isolation...");
    stepsUseAdaptersOnly().check(classes); // GH-90000

    log.info("Checking adapter layer boundaries...");
    adaptersDontLeakUpwards().check(classes); // GH-90000

    log.info("All YAPPC SDLC architecture rules passed!");
  }
}
