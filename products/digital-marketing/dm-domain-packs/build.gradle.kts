import com.ghatana.buildlogic.ProductPackValidationExtension
import groovy.json.JsonSlurper
import java.nio.file.Files

plugins {
    id("java-module")
    id("product-pack-validation")
}

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

configure<ProductPackValidationExtension> {
    productName.set("Digital Marketing")
    manifestFile.set(layout.projectDirectory.file("domain-pack.json"))
    requiredManifestFields.set(
        listOf(
            "\"productCode\"",
            "\"rulePrefix\"",
            "\"boundaryPolicyStoreClass\"",
            "\"pluginBindingsClass\"",
            "\"kernelCapabilitiesConsumed\"",
            "\"policyActions\"",
            "\"pluginsConsumed\"",
            "\"bridgesConsumed\"",
            "\"domainPacksProvided\"",
            "\"uiSurfaces\"",
            "\"runtimeServices\"",
            "\"dataSensitivity\"",
            "\"policyResources\"",
            "\"complianceRuleSets\""
        )
    )
    policyPackTestPatterns.set(
        listOf(
            "com.ghatana.digitalmarketing.pack.DigitalMarketingPackContractTest",
            "com.ghatana.digitalmarketing.pack.DigitalMarketingBoundaryPolicyStoreTest",
            "com.ghatana.digitalmarketing.pack.DigitalMarketingKernelBoundaryContractTest",
            "com.ghatana.digitalmarketing.pack.DigitalMarketingBoundaryWorkflowCoverageTest"
        )
    )
    complianceSourceFile.set(
        layout.projectDirectory.file("src/main/java/com/ghatana/digitalmarketing/pack/DigitalMarketingComplianceRulePack.java")
    )
    complianceRulePrefix.set("DM-")
}

tasks.register("validateDmosDomainPackBindings") {
    group = "verification"
    description = "Validates DMOS product-specific manifest bindings and prefix constraints."
    val manifestFile = layout.projectDirectory.file("domain-pack.json").asFile

    inputs.file(manifestFile)

    doLast {
        check(manifestFile.exists()) {
            "domain-pack.json not found at ${manifestFile.absolutePath}"
        }

        val root = JsonSlurper().parse(manifestFile) as Map<*, *>
        val productExtensions = root["productExtensions"] as? Map<*, *> ?: emptyMap<Any, Any>()
        fun requiredRootString(key: String): String {
            val value = (root[key] as? String)?.trim().orEmpty()
            check(value.isNotEmpty()) { "domain-pack.json: '$key' is required and must be non-empty" }
            return value
        }
        fun requiredExtensionString(key: String): String {
            val value = (productExtensions[key] as? String)?.trim().orEmpty()
            check(value.isNotEmpty()) { "domain-pack.json: productExtensions.$key is required and must be non-empty" }
            return value
        }

        val productCode = requiredExtensionString("productCode")
        val rulePrefix = requiredRootString("rulePrefix")
        val boundaryPolicyStoreClass = requiredExtensionString("boundaryPolicyStoreClass")
        val pluginBindingsClass = requiredExtensionString("pluginBindingsClass")
        val kernelCapabilitiesConsumed = (root["kernelCapabilitiesConsumed"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val pluginsConsumed = (root["pluginsConsumed"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val complianceRuleSets = (productExtensions["complianceRuleSets"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim() }
            .orEmpty()

        check(productCode == "DM") { "domain-pack.json: productCode must be 'DM'" }
        check(rulePrefix == "DM-") { "domain-pack.json: rulePrefix must be 'DM-'" }
        check(boundaryPolicyStoreClass == "com.ghatana.digitalmarketing.pack.DigitalMarketingBoundaryPolicyStore") {
            "domain-pack.json: boundaryPolicyStoreClass must reference DigitalMarketingBoundaryPolicyStore"
        }
        check(pluginBindingsClass == "com.ghatana.digitalmarketing.pack.DigitalMarketingPluginBindings") {
            "domain-pack.json: pluginBindingsClass must reference DigitalMarketingPluginBindings"
        }
        check(complianceRuleSets.isNotEmpty()) {
            "domain-pack.json: complianceRuleSets must contain at least one rule set"
        }
        check(kernelCapabilitiesConsumed.isNotEmpty()) {
            "domain-pack.json: kernelCapabilitiesConsumed must contain at least one capability"
        }
        check(pluginsConsumed.isNotEmpty()) {
            "domain-pack.json: pluginsConsumed must contain at least one plugin"
        }
        check(complianceRuleSets.none { it.startsWith("PHR-") || it.startsWith("FIN-") }) {
            "domain-pack.json: complianceRuleSets must not contain PHR-/FIN- prefixes"
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
        "validateDmosDomainPackBindings",
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
            limit { counter = "BRANCH"; value = "COVEREDRATIO"; minimum = "0.58".toBigDecimal() }
            excludes = listOf(
                "com.ghatana.digitalmarketing.pack.DigitalMarketingPluginBindings"
            )
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
