package com.ghatana.yappc.sdlc.quality;

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
  private static final Logger log = LoggerFactory.getLogger(ArchUnitRules.class);

  private static final String SDLC_BASE_PACKAGE = "com.ghatana.yappc.sdlc";
  private static final String GHATANA_BASE_PACKAGE = "com.ghatana";

  /**
   * Enforces hexagonal/clean architecture layer dependencies. Domain is pure, Application
   * orchestrates, Adapters integrate external systems.
   */
  public static ArchRule hexagonalLayers() {
    return layeredArchitecture()
        .consideringAllDependencies()
        .layer("Domain")
        .definedBy("..domain..")
        .layer("Application")
        .definedBy("..application..")
        .layer("Adapters")
        .definedBy("..adapters..")
        .layer("Infrastructure")
        .definedBy("..infrastructure..")
        .whereLayer("Domain")
        .mayOnlyBeAccessedByLayers("Application", "Adapters")
        .whereLayer("Domain")
        .mayNotAccessAnyLayer()
        .whereLayer("Application")
        .mayOnlyAccessLayers("Domain")
        .whereLayer("Adapters")
        .mayOnlyAccessLayers("Application", "Domain")
        .whereLayer("Infrastructure")
        .mayOnlyAccessLayers("Adapters", "Application", "Domain")
        .as("Hexagonal architecture layers must be respected");
  }

  /** Domain layer must not depend on infrastructure concerns. */
  public static ArchRule domainDoesNotDependOnInfrastructure() {
    return noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..infrastructure..")
        .as("Domain layer must not depend on infrastructure");
  }

  /**
   * CRITICAL: SDLC code must use ActiveJ Promise, not CompletableFuture. This ensures compatibility
   * with ActiveJ Eventloop and avoids blocking.
   */
  public static ArchRule sdlcMustUseActivejPromise() {
    return noClasses()
        .that()
        .resideInAPackage(SDLC_BASE_PACKAGE + "..")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(CompletableFuture.class)
        .as("SDLC modules must use ActiveJ Promise, not CompletableFuture");
  }

  /** All WorkflowStep implementations must reside in correct phase packages. */
  public static ArchRule workflowStepsInCorrectPackages() {
    return classes()
        .that()
        .implement(com.ghatana.yappc.sdlc.WorkflowStep.class)
        .should()
        .resideInAnyPackage(
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
   * (EntityRepository, EventPublisher, etc.)
   */
  public static ArchRule stepsUseAdaptersOnly() {
    return noClasses()
        .that()
        .resideInAPackage(SDLC_BASE_PACKAGE + "..")
        .and()
        .implement(com.ghatana.yappc.sdlc.WorkflowStep.class)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("java.sql..", "java.net.http..", "okhttp3..", "org.apache.http..")
        .as("WorkflowStep implementations must use adapters, not direct I/O");
  }

  /** Adapters must not leak into domain or application layers. */
  public static ArchRule adaptersDontLeakUpwards() {
    return noClasses()
        .that()
        .resideInAnyPackage("..domain..", "..application..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..adapters..")
        .as("Domain and Application layers must not depend on Adapters");
  }

  /** Run all SDLC architecture rules against the YAPPC codebase. */
  public static void main(String[] args) {
    JavaClasses classes = new ClassFileImporter().importPackages(GHATANA_BASE_PACKAGE);

    log.info("Checking hexagonal layers...");
    hexagonalLayers().check(classes);

    log.info("Checking domain isolation from infrastructure...");
    domainDoesNotDependOnInfrastructure().check(classes);

    log.info("Checking ActiveJ Promise usage (no CompletableFuture)...");
    sdlcMustUseActivejPromise().check(classes);

    log.info("Checking workflow step package placement...");
    workflowStepsInCorrectPackages().check(classes);

    log.info("Checking adapter isolation...");
    stepsUseAdaptersOnly().check(classes);

    log.info("Checking adapter layer boundaries...");
    adaptersDontLeakUpwards().check(classes);

    log.info("All YAPPC SDLC architecture rules passed!");
  }
}
