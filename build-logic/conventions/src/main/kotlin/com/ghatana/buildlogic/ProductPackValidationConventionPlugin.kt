package com.ghatana.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
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
                "pluginsConsumed:",
                "bridgesConsumed:",
                "domainPacksProvided:",
                "uiSurfaces:",
                "runtimeServices:",
                "dataSensitivity:"
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
            project.tasks.register("validateComplianceRulePack") {
                group = "verification"
                description = "Validates product compliance rule pack wiring."
                if (extension.complianceSourceFile.isPresent) {
                    inputs.file(extension.complianceSourceFile)
                }
                if (extension.complianceClassFileName.isPresent) {
                    dependsOn(project.tasks.named("compileJava"))
                }
                doLast {
                    if (extension.complianceSourceFile.isPresent) {
                        val sourceFile = extension.complianceSourceFile.get().asFile
                        require(sourceFile.exists()) {
                            "Compliance rule pack source is missing: ${sourceFile.absolutePath}"
                        }
                        val text = sourceFile.readText()
                        val ruleIds = Regex("new\\s+ComplianceRule\\(\\s*\"([^\"]+)\"")
                            .findAll(text)
                            .map { it.groupValues[1] }
                            .toList()
                        require(ruleIds.isNotEmpty()) {
                            "No ComplianceRule IDs found in ${sourceFile.name}"
                        }
                        val prefix = extension.complianceRulePrefix.get()
                        if (prefix.isNotBlank()) {
                            require(ruleIds.all { it.startsWith(prefix) }) {
                                "All ComplianceRule IDs in ${sourceFile.name} must start with " + prefix
                            }
                        }
                        require(ruleIds.distinct().size == ruleIds.size) {
                            "Duplicate ComplianceRule IDs found in ${sourceFile.name}"
                        }
                    } else {
                        val classFileName = extension.complianceClassFileName.get()
                        val classesDir = buildDirProvider.get().asFile.toPath()
                        require(Files.exists(classesDir)) {
                            "Compiled classes directory does not exist: " + classesDir
                        }
                        val found = Files.walk(classesDir).use { paths ->
                            paths.anyMatch { path -> path.fileName.toString() == classFileName }
                        }
                        require(found) {
                            "Expected compliance rule pack class file was not found: $classFileName"
                        }
                    }
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
