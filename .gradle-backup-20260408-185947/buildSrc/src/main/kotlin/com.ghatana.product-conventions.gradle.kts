/**
 * Product Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Provides standardized configuration for product-level builds,
 *              including validation, governance, and product-specific conventions.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * Usage:
 *   plugins {
 *       id("com.ghatana.product-conventions")
 *   }
 *
 * Configuration:
 *   product {
 *       name.set("yappc")
 *       type.set("ai-platform")
 *       validation.enabled.set(true)
 *       governance.enabled.set(true)
 *   }
 */

interface ProductConventionExtension {
    val name: Property<String>
    val type: Property<String>
    val validation: ValidationExtension
    val governance: GovernanceExtension
}

interface ValidationExtension {
    val enabled: Property<Boolean>
    val moduleSize: ModuleSizeExtension
    val structural: StructuralExtension
}

interface ModuleSizeExtension {
    val enabled: Property<Boolean>
    val maxJavaFiles: Property<Int>
}

interface StructuralExtension {
    val enabled: Property<Boolean>
    val rules: ListProperty<String>
}

interface GovernanceExtension {
    val enabled: Property<Boolean>
    val dependencyExclusions: ListProperty<String>
    val architecturalRules: ListProperty<String>
}

// Create the extension
val extension = project.extensions.create<ProductConventionExtension>("product")

// Configure defaults
extension.name.convention(project.name)
extension.type.convention("unknown")

extension.validation.enabled.convention(true)
extension.validation.moduleSize.enabled.convention(true)
extension.validation.moduleSize.maxJavaFiles.convention(150)
extension.validation.structural.enabled.convention(true)
extension.validation.structural.rules.convention(listOf())

extension.governance.enabled.convention(true)
extension.governance.dependencyExclusions.convention(listOf(
    "dev.langchain4j:langchain4j",
    "dev.langchain4j:langchain4j-open-ai",
    "io.projectreactor:reactor-core",
    "io.reactivex.rxjava3:rxjava"
))
extension.governance.architecturalRules.convention(listOf())

// Apply product-level configuration
subprojects {
    if (!hasJavaSource()) return@subprojects
    
    // Dependency governance
    if (extension.governance.enabled.get()) {
        configurations.all {
            extension.governance.dependencyExclusions.get().forEach { exclusion ->
                val parts = exclusion.split(":")
                if (parts.size == 2) {
                    exclude(group = parts[0], module = parts[1])
                }
            }
        }
    }
}

// Module size validation
if (extension.validation.enabled.get() && extension.validation.moduleSize.enabled.get()) {
    tasks.register("checkModuleSize") {
        group = "verification"
        description = "Validates module size limits"
        
        doLast {
            val violations = mutableListOf<String>()
            val maxFiles = extension.validation.moduleSize.maxJavaFiles.get()
            
            subprojects.forEach { subproject ->
                val srcDir = subproject.file("src/main/java")
                if (srcDir.exists()) {
                    val fileCount = srcDir.walkTopDown()
                        .filter { it.isFile && it.extension == "java" }
                        .count()
                    
                    if (fileCount > maxFiles) {
                        violations.add("${subproject.path}: $fileCount files (max: $maxFiles)")
                    }
                }
            }
            
            if (violations.isNotEmpty()) {
                throw GradleException(
                    "Modules exceed size limit:\n" +
                    violations.joinToString("\n") { "  - $it" } + "\n" +
                    "Consider splitting large modules."
                )
            }
            
            logger.lifecycle("All modules within size limits")
        }
    }
    
    tasks.named("check") {
        dependsOn("checkModuleSize")
    }
}

// Structural validation
if (extension.validation.enabled.get() && extension.validation.structural.enabled.get()) {
    tasks.register("checkStructuralRules") {
        group = "verification"
        description = "Validates structural governance rules"
        
        doLast {
            val violations = mutableListOf<String>()
            
            extension.validation.structural.rules.get().forEach { rule ->
                // Add rule validation logic here
                // This would be customized per product
            }
            
            if (violations.isNotEmpty()) {
                throw GradleException(
                    "Structural rule violations:\n" +
                    violations.joinToString("\n") { "  - $it" }
                )
            }
            
            logger.lifecycle("Structural validation passed")
        }
    }
    
    tasks.named("check") {
        dependsOn("checkStructuralRules")
    }
}

// Helper function to check if project has Java sources
fun hasJavaSource(): Boolean {
    return file("$projectDir/src/main/java").exists() ||
           file("$projectDir/src/main/kotlin").exists() ||
           file("$projectDir/src/test/java").exists() ||
           file("$projectDir/src/test/kotlin").exists()
}
