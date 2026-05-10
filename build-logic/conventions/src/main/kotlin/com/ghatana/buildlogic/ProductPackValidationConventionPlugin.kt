package com.ghatana.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

abstract class ProductPackValidationExtension @Inject constructor(objects: ObjectFactory) {
    val productName: Property<String> = objects.property(String::class.java)
    val manifestFile: RegularFileProperty = objects.fileProperty()
    val requiredManifestFields: ListProperty<String> = objects.listProperty(String::class.java)
    val policyPackTestPatterns: ListProperty<String> = objects.listProperty(String::class.java)
    val complianceClassFileName: Property<String> = objects.property(String::class.java)
    val complianceSourceFile: RegularFileProperty = objects.fileProperty()
    val complianceRulePrefix: Property<String> = objects.property(String::class.java)
}

abstract class ValidateComplianceRulePackTask : DefaultTask() {
    @get:Optional
    @get:InputFile
    abstract val complianceSourceFile: RegularFileProperty

    @get:InputDirectory
    abstract val javaMainSourceDir: DirectoryProperty

    @get:Input
    abstract val complianceRulePrefix: Property<String>

    @TaskAction
    fun validate() {
        val prefix = complianceRulePrefix.get()
        if (complianceSourceFile.isPresent) {
            val sourceFile = complianceSourceFile.get().asFile
            require(sourceFile.exists()) {
                "Compliance rule pack source is missing: ${sourceFile.absolutePath}"
            }
            validateComplianceSourceFile(sourceFile, prefix)
            return
        }

        val javaMainSrc = javaMainSourceDir.get().asFile
        require(javaMainSrc.exists()) {
            "No complianceSourceFile configured and src/main/java does not exist in ${javaMainSrc.absolutePath}"
        }

        val complianceFiles = javaMainSrc.walk()
            .filter { it.isFile && it.extension == "java" && it.readText().contains("ComplianceRule") }
            .toList()
        require(complianceFiles.isNotEmpty()) {
            "No Java source files containing ComplianceRule declarations found under ${javaMainSrc.absolutePath}. " +
                "Configure 'complianceSourceFile' explicitly or add a ComplianceRule factory class."
        }

        for (file in complianceFiles) {
            validateComplianceSourceFile(file, prefix)
        }
    }
}

class ProductPackValidationConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "productPackValidation",
            ProductPackValidationExtension::class.java
        )

        extension.requiredManifestFields.convention(
            listOf(
                "pack:",
                "id:",
                "version:",
                "domain:",
                "kernelCapabilitiesConsumed:",
                "policyActions:",
                "pluginsConsumed:",
                "bridgesConsumed:",
                "domainPacksProvided:",
                "uiSurfaces:",
                "runtimeServices:",
                "dataSensitivity:",
                "policyResources:"
            )
        )
        extension.policyPackTestPatterns.convention(emptyList())
        extension.complianceRulePrefix.convention("")

        project.afterEvaluate {
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
            val testSourceSet = sourceSets.getByName("test")

            project.tasks.register("validateDomainPackManifest") {
                group = "verification"
                description = "Validates required product domain-pack manifest fields."
                val manifestFile = extension.manifestFile.get().asFile
                inputs.file(manifestFile)
                doLast {
                    require(manifestFile.exists()) {
                        "Domain-pack manifest is missing: ${manifestFile.absolutePath}"
                    }
                    val content = manifestFile.readText()
                    val missing = extension.requiredManifestFields.get().filterNot(content::contains)
                    if (missing.isNotEmpty()) {
                        throw GradleException(
                            "${extension.productName.get()} manifest is missing required fields: $missing"
                        )
                    }
                }
            }

            project.tasks.register("validatePolicyPack", Test::class.java) {
                group = "verification"
                description = "Executes shared product policy-pack contract assertions."
                useJUnitPlatform()
                testClassesDirs = testSourceSet.output.classesDirs
                classpath = testSourceSet.runtimeClasspath
                systemProperty("ghatana.projectDir", project.projectDir.absolutePath)
                extension.policyPackTestPatterns.get().forEach { pattern ->
                    filter.includeTestsMatching(pattern)
                }
            }

            val buildDirProvider = project.layout.buildDirectory.dir("classes/java/main")
            project.tasks.register("validateComplianceRulePack", ValidateComplianceRulePackTask::class.java) {
                group = "verification"
                description = "Validates product compliance rule pack wiring."
                javaMainSourceDir.set(project.layout.projectDirectory.dir("src/main/java"))
                complianceRulePrefix.set(extension.complianceRulePrefix)
                if (extension.complianceSourceFile.isPresent) {
                    complianceSourceFile.set(extension.complianceSourceFile)
                }
            }

            project.tasks.register("productConformanceCheck") {
                group = "verification"
                description = "Runs all product pack validation checks."
                dependsOn(
                    "validateDomainPackManifest",
                    "validatePolicyPack",
                    "validateComplianceRulePack"
                )
            }

            project.tasks.named("check").configure {
                dependsOn("productConformanceCheck")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Source-level compliance rule validation
// ---------------------------------------------------------------------------

private val KNOWN_SEVERITIES = setOf("CRITICAL", "HIGH", "MEDIUM", "LOW")

private val PLACEHOLDER_EXPRESSIONS = setOf(
    "TODO", "FIXME", "PLACEHOLDER", "STUB", "TBD", "NOT_IMPLEMENTED", "REPLACE_ME"
)

private val COMPLIANCE_RULE_ID_REGEX = Regex(
    """new\s+(?:[A-Za-z_]\w*\.)*ComplianceRule\(\s*"([^"]+)""""
)

internal fun extractComplianceRuleIds(text: String): List<String> {
    return COMPLIANCE_RULE_ID_REGEX
        .findAll(text)
        .map { it.groupValues[1] }
        .toList()
}

/**
 * Validates a single Java source file containing ComplianceRule declarations.
 *
 * Checks performed:
 *  - At least one ComplianceRule ID is declared
 *  - All IDs start with the given prefix (if configured)
 *  - No duplicate IDs
 *  - Severity values are from the canonical set (CRITICAL/HIGH/MEDIUM/LOW)
 *  - Expression strings are non-empty and not placeholder text
 */
private fun validateComplianceSourceFile(sourceFile: File, prefix: String) {
    val text = sourceFile.readText()

    val ruleIds = extractComplianceRuleIds(text)

    require(ruleIds.isNotEmpty()) {
        "No ComplianceRule IDs found in ${sourceFile.name}"
    }

    if (prefix.isNotBlank()) {
        val badIds = ruleIds.filterNot { it.startsWith(prefix) }
        require(badIds.isEmpty()) {
            "ComplianceRule IDs must start with '$prefix' in ${sourceFile.name}, offending: $badIds"
        }
    }

    require(ruleIds.distinct().size == ruleIds.size) {
        val duplicates = ruleIds.groupBy { it }.filter { it.value.size > 1 }.keys
        "Duplicate ComplianceRule IDs in ${sourceFile.name}: $duplicates"
    }

    // Validate severity values
    val severityMatches = Regex("""Severity\.([A-Z_]+)""").findAll(text).map { it.groupValues[1] }.toList()
    val unknownSeverities = severityMatches.filterNot { KNOWN_SEVERITIES.contains(it) }
    require(unknownSeverities.isEmpty()) {
        "Unknown severity values in ${sourceFile.name}: $unknownSeverities. Allowed: $KNOWN_SEVERITIES"
    }

    // Validate that expression strings are non-empty and not placeholder text
    val expressionMatches = Regex("""\.expression\(\s*"([^"]*)"""").findAll(text).map { it.groupValues[1] }.toList()
    for (expr in expressionMatches) {
        val trimmed = expr.trim()
        require(trimmed.isNotEmpty()) {
            "Empty expression string found in ${sourceFile.name}. Compliance rule expressions must not be blank."
        }
        val isPlaceholder = PLACEHOLDER_EXPRESSIONS.any { ph -> trimmed.uppercase().contains(ph) }
        require(!isPlaceholder) {
            "Placeholder expression detected in ${sourceFile.name}: \"$trimmed\". Replace with a real rule expression."
        }
    }
}
