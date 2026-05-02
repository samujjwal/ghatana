plugins {
    id("java-module")
}

import groovy.json.JsonSlurper
import java.nio.file.Files

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

group = "com.ghatana.digitalmarketing"
description = "DMOS Domain Packs — boundary policy rules, compliance rule packs, plugin startup bindings"

dependencies {
    api(project(":products:digital-marketing:dm-core-contracts"))
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))
    api(project(":platform-plugins:plugin-compliance"))
    api(project(":platform-plugins:plugin-consent"))
    api(project(":platform-plugins:plugin-human-approval"))
    api(project(":platform-plugins:plugin-risk-management"))
    api(project(":platform-plugins:plugin-audit-trail"))

    compileOnly(libs.spotbugs.annotations)

    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.register("validateDomainPackManifest") {
    group = "verification"
    description = "Validates DMOS domain-pack manifest fields and DM prefix constraints."
    val manifestFile = layout.projectDirectory.file("domain-pack.json").asFile

    inputs.file(manifestFile)

    doLast {
        check(manifestFile.exists()) {
            "domain-pack.json not found at ${manifestFile.absolutePath}"
        }

        val root = JsonSlurper().parse(manifestFile) as Map<*, *>
        fun requiredString(key: String): String {
            val value = (root[key] as? String)?.trim().orEmpty()
            check(value.isNotEmpty()) { "domain-pack.json: '$key' is required and must be non-empty" }
            return value
        }

        val productCode = requiredString("productCode")
        val rulePrefix = requiredString("rulePrefix")
        val boundaryPolicyStoreClass = requiredString("boundaryPolicyStoreClass")
        val pluginBindingsClass = requiredString("pluginBindingsClass")
        val complianceRuleSets = (root["complianceRuleSets"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim() }
            .orEmpty()

        check(productCode == "DM") {
            "domain-pack.json: productCode must be 'DM'"
        }
        check(rulePrefix == "DM-") {
            "domain-pack.json: rulePrefix must be 'DM-'"
        }
        check(boundaryPolicyStoreClass == "com.ghatana.digitalmarketing.pack.DigitalMarketingBoundaryPolicyStore") {
            "domain-pack.json: boundaryPolicyStoreClass must reference DigitalMarketingBoundaryPolicyStore"
        }
        check(pluginBindingsClass == "com.ghatana.digitalmarketing.pack.DigitalMarketingPluginBindings") {
            "domain-pack.json: pluginBindingsClass must reference DigitalMarketingPluginBindings"
        }
        check(complianceRuleSets.isNotEmpty()) {
            "domain-pack.json: complianceRuleSets must contain at least one rule set"
        }
        check(complianceRuleSets.none { it.startsWith("PHR-") || it.startsWith("FIN-") }) {
            "domain-pack.json: complianceRuleSets must not contain PHR-/FIN- prefixes"
        }
    }
}

tasks.register("validatePolicyPack") {
    group = "verification"
    description = "Validates DMOS boundary policy default-deny and DM-BP rule prefix conventions."
    val boundaryPolicyStoreSource = layout.projectDirectory
        .file("src/main/java/com/ghatana/digitalmarketing/pack/DigitalMarketingBoundaryPolicyStore.java")
        .asFile

    inputs.file(boundaryPolicyStoreSource)

    doLast {
        val text = boundaryPolicyStoreSource.readText()

        check("DM-BP-999" in text) {
            "DigitalMarketingBoundaryPolicyStore must include DM-BP-999 default-deny rule"
        }
        check(".resourcePattern(\"**\")" in text && ".effect(Effect.DENY)" in text) {
            "DigitalMarketingBoundaryPolicyStore must include default-deny rule over ** with Effect.DENY"
        }

        val ruleIds = Regex("\\\"(DM-BP-[0-9]{3})\\\"")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
        check(ruleIds.isNotEmpty()) {
            "No DM-BP-* rule IDs found in DigitalMarketingBoundaryPolicyStore"
        }
        check(ruleIds.distinct().size == ruleIds.size) {
            "Duplicate DM-BP-* rule IDs found in DigitalMarketingBoundaryPolicyStore"
        }
    }
}

tasks.register("validateComplianceRulePack") {
    group = "verification"
    description = "Validates DMOS compliance rule ID uniqueness and DM- prefix conformance."
    val complianceRulePackSource = layout.projectDirectory
        .file("src/main/java/com/ghatana/digitalmarketing/pack/DigitalMarketingComplianceRulePack.java")
        .asFile

    inputs.file(complianceRulePackSource)

    doLast {
        val text = complianceRulePackSource.readText()
        val ruleIds = Regex("new\\s+ComplianceRule\\(\\s*\"([^\"]+)\"")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

        check(ruleIds.isNotEmpty()) {
            "No ComplianceRule IDs found in DigitalMarketingComplianceRulePack"
        }
        check(ruleIds.all { it.startsWith("DM-") }) {
            "All ComplianceRule IDs must start with DM-"
        }
        check(ruleIds.distinct().size == ruleIds.size) {
            "Duplicate ComplianceRule IDs found in DigitalMarketingComplianceRulePack"
        }
    }
}

tasks.register("validateReferenceConsumerHygiene") {
    group = "verification"
    description = "Fails when DMOS packs contain forbidden PHR/FIN reference consumer identifiers."
    val sourceRoot = layout.projectDirectory.dir("src/main/java").asFile.toPath()

    inputs.dir(sourceRoot.toFile())

    doLast {
        val forbiddenPatterns = listOf("PHR-", "FIN-", "PhrBoundaryPolicyStore", "FinanceBoundaryPolicyStore")

        val violations = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".java") }
                .forEach { path ->
                    val text = Files.readString(path)
                    forbiddenPatterns
                        .filter { pattern -> text.contains(pattern) }
                        .forEach { pattern ->
                            violations.add("$path contains forbidden token '$pattern'")
                        }
                }
        }

        check(violations.isEmpty()) {
            "Reference consumer hygiene violations:\n${violations.joinToString("\n")}"
        }
    }
}

tasks.named("check") {
    dependsOn(
        "validateDomainPackManifest",
        "validatePolicyPack",
        "validateComplianceRulePack",
        "validateReferenceConsumerHygiene"
    )
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.95".toBigDecimal() }
            limit { counter = "BRANCH"; value = "COVEREDRATIO"; minimum = "0.90".toBigDecimal() }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
