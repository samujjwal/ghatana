package com.ghatana.yappc.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("YAPPC Shared Architecture Boundary Tests")
class YappcSharedBoundaryTest {

    @Test
    @DisplayName("YS-3: yappc-shared must not depend on agents/scaffold/refactorer packages")
    void sharedModule_mustNotDependOnForbiddenDomainClusters() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.ghatana.yappc");

        noClasses()
                .that().resideInAnyPackage("com.ghatana.yappc.client..", "com.ghatana.yappc.plugin..", "com.ghatana.yappc.agent.spi..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.ghatana.yappc.agents..",
                        "com.ghatana.yappc.scaffold..",
                        "com.ghatana.yappc.refactorer..")
                .check(imported);
    }
}


