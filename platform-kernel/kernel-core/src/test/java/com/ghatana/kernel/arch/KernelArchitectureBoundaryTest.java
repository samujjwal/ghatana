package com.ghatana.kernel.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.base.DescribedPredicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit dependency-direction conformance tests for platform-kernel.
 *
 * <p>These tests enforce the forbidden dependency directions documented in
 * {@code docs/MONOREPO_ARCHITECTURE.md}:</p>
 * <ul>
 *   <li>ARCH-001: {@code platform-kernel} must not import from any {@code products.*} package</li>
 *   <li>ARCH-002: {@code platform-kernel} must not import from {@code platform-plugins.*} packages</li>
 *   <li>ARCH-003: kernel classes must not directly use {@code CompletableFuture} (use ActiveJ Promise)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit dependency-direction enforcement tests for platform-kernel boundary
 * @doc.layer platform
 * @doc.pattern ArchitecturalTest
 */
@DisplayName("platform-kernel architectural boundary rules")
class KernelArchitectureBoundaryTest {

    private static JavaClasses kernelClasses;

    @BeforeAll
    static void importClasses() {
        kernelClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.kernel");
    }

    // ── ARCH-001: kernel must not depend on products ──────────────────────────

    @Test
    @DisplayName("ARCH-001: platform-kernel must not import from products.*")
    void kernelMustNotDependOnProducts() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.kernel..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.phr..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.finance..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.yappc..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.aep..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.datacloud..")
                .as("ARCH-001: platform-kernel must not depend on any product package");

        rule.check(kernelClasses);
    }

    // ── ARCH-002: kernel must not depend on plugin implementations ────────────

    @Test
    @DisplayName("ARCH-002: platform-kernel must not import from plugin implementation packages")
    void kernelMustNotDependOnPluginImplementations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.kernel..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.ghatana.plugin..impl..")
                .as("ARCH-002: kernel must not depend on plugin implementations — " +
                    "depend on plugin interfaces (SPI) only");

        rule.check(kernelClasses);
    }

    // ── ARCH-003: kernel must use ActiveJ Promise, not CompletableFuture ─────

    @Test
    @DisplayName("ARCH-003: kernel async code must use ActiveJ Promise, not CompletableFuture")
    void kernelMustUseActivejPromise() {
        ArchRule rule = noClasses()
                .that(notInAdapterPackages())
                .and()
                    .doNotHaveSimpleName("PhrPatientDataService") // legacy bridge — tracked in TODO
                .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.concurrent.CompletableFuture")
                .as("ARCH-003: kernel classes must use ActiveJ Promise for async, not CompletableFuture");

        rule.check(kernelClasses);
    }

    private static DescribedPredicate<JavaClass> notInAdapterPackages() {
        return new DescribedPredicate<>("not in adapter packages") {
            @Override
            public boolean test(JavaClass item) {
                return !item.getPackageName().contains(".adapter.") && 
                       !item.getSimpleName().startsWith("AepKernelAdapter") &&
                       !item.getSimpleName().startsWith("DataCloudKernelAdapter");
            }
        };
    }
}
