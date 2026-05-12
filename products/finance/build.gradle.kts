import com.ghatana.buildlogic.ProductPackValidationExtension
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("java-module")
    id("product-pack-validation")
}

description = "Finance product - Core trading and risk management platform"

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:unchecked")
}

dependencies {
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))
    api(project(":platform-plugins:plugin-ledger"))
    api(project(":platform-plugins:plugin-fraud-detection"))
    api(project(":platform-plugins:plugin-compliance"))
    api(project(":platform-plugins:plugin-risk-management"))
    api(project(":platform-plugins:plugin-audit-trail"))
    api(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:java:agent-core"))
    implementation(libs.bundles.ai.integration)
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.servicegraph)
    implementation(libs.activej.launcher)
    runtimeOnly(project(":products:finance:domains:oms"))
    runtimeOnly(project(":products:finance:domains:ems"))
    runtimeOnly(project(":products:finance:domains:pms"))
    runtimeOnly(project(":products:finance:domains:risk"))
    runtimeOnly(project(":products:finance:domains:rules"))
    runtimeOnly(project(":products:finance:domains:corporate-actions"))
    runtimeOnly(project(":products:finance:domains:market-data"))
    runtimeOnly(project(":products:finance:domains:post-trade"))
    runtimeOnly(project(":products:finance:domains:pricing"))
    runtimeOnly(project(":products:finance:domains:reconciliation"))
    runtimeOnly(project(":products:finance:domains:reference-data"))
    runtimeOnly(project(":products:finance:domains:regulatory-reporting"))
    runtimeOnly(project(":products:finance:domains:sanctions"))
    runtimeOnly(project(":products:finance:domains:surveillance"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.micrometer.core)
    implementation(libs.bundles.database.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(libs.bundles.testing.containers)
}

tasks.register<JavaExec>("validateContracts") {
    group = "verification"
    description = "Validates Finance contracts for deployment"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.ghatana.finance.contracts.ContractValidationRunner")
}

tasks.register<Test>("checkApiContractConformance") {
    group = "verification"
    description = "Validates that implemented routes match the OpenAPI specification (CI gate for contract drift)"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.ghatana.products.finance.http.FinanceApiContractConformanceTest")
    }
}

configure<ProductPackValidationExtension> {
    productName.set("Finance")
    manifestFile.set(layout.projectDirectory.file("domain-pack-manifest.yaml"))
    requiredManifestFields.set(
        listOf(
            "id:", "version:", "capabilities:", "domain:",
            "kernelCapabilitiesConsumed:", "policyActions:", "pluginsConsumed:", "bridgesConsumed:",
            "domainPacksProvided:", "uiSurfaces:", "runtimeServices:", "dataSensitivity:",
            "policyResources:"
        )
    )
    policyPackTestPatterns.set(
        listOf(
            "com.ghatana.finance.kernel.FinancePackContractTest",
            "com.ghatana.finance.kernel.FinanceKernelBoundaryContractTest"
        )
    )
    complianceSourceFile.set(layout.projectDirectory.file(
        "src/main/java/com/ghatana/finance/kernel/policy/FinanceComplianceRulePack.java"
    ))
    complianceRulePrefix.set("FIN-")
}
