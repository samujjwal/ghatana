plugins {
    id("java-module")
}

description = "Finance product - Core trading and risk management platform"

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:unchecked")
}

dependencies {
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))
    api(project(":platform-kernel:kernel-testing"))
    api(project(":platform-plugins:plugin-ledger"))
    api(project(":platform-plugins:plugin-fraud-detection"))
    api(project(":platform-plugins:plugin-compliance"))
    api(project(":platform-plugins:plugin-consent"))
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
    implementation(project(":products:finance:domains:oms"))
    implementation(project(":products:finance:domains:ems"))
    implementation(project(":products:finance:domains:pms"))
    implementation(project(":products:finance:domains:risk"))
    implementation(project(":products:finance:domains:compliance"))
    implementation(project(":products:finance:domains:rules"))
    implementation(project(":products:finance:domains:corporate-actions"))
    implementation(project(":products:finance:domains:market-data"))
    implementation(project(":products:finance:domains:post-trade"))
    implementation(project(":products:finance:domains:pricing"))
    implementation(project(":products:finance:domains:reconciliation"))
    implementation(project(":products:finance:domains:reference-data"))
    implementation(project(":products:finance:domains:regulatory-reporting"))
    implementation(project(":products:finance:domains:sanctions"))
    implementation(project(":products:finance:domains:surveillance"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.micrometer.core)
    implementation(libs.bundles.database.core)
    testImplementation(project(":platform:java:testing"))
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

// =============================================================================
// Phase 3.3: Pack schema validation gates
// =============================================================================

tasks.register("validateDomainPackManifest") {
    description = "Validates the Finance domain-pack-manifest.yaml for required fields and schema correctness."
    group = "verification"
    val manifestFile = layout.projectDirectory.file("domain-pack-manifest.yaml").asFile
    doLast {
        require(manifestFile.exists()) { "domain-pack-manifest.yaml is missing from product root" }
        val content = manifestFile.readText()
        val requiredFields = listOf("pack:", "id:", "version:", "capabilities:", "domain:")
        val missing = requiredFields.filter { field -> !content.contains(field) }
        if (missing.isNotEmpty()) {
            throw GradleException("Finance domain-pack-manifest.yaml is missing required fields: $missing")
        }
        logger.lifecycle("Finance domain-pack-manifest.yaml validation passed.")
    }
}

tasks.register("validatePolicyPack") {
    description = "Validates that Finance boundary policy store class compiles and its rules are non-empty."
    group = "verification"
    dependsOn(tasks.named("compileJava"))
    
    val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
    
    doLast {
        val classFile = classesDir.walkTopDown()
            .firstOrNull { it.name == "FinanceBoundaryPolicyStore.class" }
        requireNotNull(classFile) {
            "Finance policy pack class not found: com.ghatana.finance.kernel.policy.FinanceBoundaryPolicyStore"
        }
        logger.lifecycle("Finance policy pack validation passed: ${classFile.name}")
    }
}

tasks.register("validateComplianceRulePack") {
    description = "Validates that Finance compliance rule pack compiles."
    group = "verification"
    dependsOn(tasks.named("compileJava"))
    
    val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
    
    doLast {
        require(classesDir.exists()) { "Classes directory does not exist: $classesDir" }
        val classFile = classesDir.walkTopDown()
            .firstOrNull { it.name == "FinanceComplianceRulePack.class" }
        requireNotNull(classFile) {
            "Finance compliance rule pack class not found: com.ghatana.finance.kernel.policy.FinanceComplianceRulePack"
        }
        logger.lifecycle("Finance compliance rule pack validation passed: ${classFile.name}")
    }
}

tasks.named("check") {
    dependsOn(
        "validateDomainPackManifest",
        "validatePolicyPack",
        "validateComplianceRulePack"
    )
}
