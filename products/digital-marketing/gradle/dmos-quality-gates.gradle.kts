import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ValidatePatternsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input
    abstract val forbiddenPatterns: ListProperty<String>

    @get:Input
    abstract val violationPrefix: Property<String>

    @get:Input
    abstract val failurePrefix: Property<String>

    @TaskAction
    fun validate() {
        val compiled = forbiddenPatterns.get().map { Regex(it, RegexOption.IGNORE_CASE) }
        val violations = mutableListOf<String>()

        sourceFiles.files
            .filter { it.isFile }
            .forEach { file ->
                val text = file.readText()
                compiled.forEach { pattern ->
                    if (pattern.containsMatchIn(text)) {
                        violations.add("${file.path}: ${violationPrefix.get()} ${pattern.pattern}")
                    }
                }
            }

        check(violations.isEmpty()) {
            "${failurePrefix.get()}:\n" + violations.joinToString("\n")
        }
    }
}

val sourceExtensions = listOf("java", "kt", "ts", "tsx")

tasks.register<ValidatePatternsTask>("validateNoProductionStubs") {
    group = "verification"
    description = "Fails if production source contains stub/mock placeholders or unsafe unsupported stubs."

    sourceFiles.from(
        sourceExtensions.map { ext ->
            project.layout.projectDirectory.asFileTree.matching {
                include("src/main/**/*.$ext")
            }
        }
    )

    forbiddenPatterns.set(
        listOf(
            "\\bTODO\\b",
            "\\bFIXME\\b",
            "\\bHACK\\b",
            "\\bSTUB\\b",
            "\\bMOCK\\b",
            "\\bDUMMY\\b",
            "UnsupportedOperationException\\s*\\("
        )
    )
    violationPrefix.set("forbidden production marker")
    failurePrefix.set("Production quality gate failed")
}

tasks.register<ValidatePatternsTask>("validateNoTestTheatre") {
    group = "verification"
    description = "Fails if tests contain theatre assertions or disabled tests."

    sourceFiles.from(
        sourceExtensions.map { ext ->
            project.layout.projectDirectory.asFileTree.matching {
                include("src/test/**/*.$ext")
            }
        }
    )

    forbiddenPatterns.set(
        listOf(
            "assertTrue\\s*\\(\\s*true\\s*\\)",
            "assertThat\\s*\\(\\s*true\\s*\\)\\s*\\.\\s*isTrue\\s*\\(",
            "expect\\s*\\(\\s*true\\s*\\)\\s*\\.\\s*toBe\\s*\\(\\s*true\\s*\\)",
            "\\bit\\s*\\.\\s*skip\\s*\\(",
            "@Disabled\\b"
        )
    )
    violationPrefix.set("forbidden test theatre pattern")
    failurePrefix.set("Test authenticity gate failed")
}

tasks.register<ValidatePatternsTask>("validateNoMockingFrameworkUsage") {
    group = "verification"
    description = "Fails if DMOS tests use mock frameworks instead of behavior-oriented in-memory doubles."

    sourceFiles.from(
        sourceExtensions.map { ext ->
            project.layout.projectDirectory.asFileTree.matching {
                include("src/test/**/*.$ext")
            }
        }
    )

    forbiddenPatterns.set(
        listOf(
            "import\\s+org\\.mockito",
            "@Mock\\b",
            "Mockito\\.",
            "mock\\s*\\("
        )
    )
    violationPrefix.set("forbidden mocking framework pattern")
    failurePrefix.set("Test realism gate failed")
}

tasks.register<ValidatePatternsTask>("validateNoSecurityAntiPatterns") {
    group = "verification"
    description = "Fails if production source contains DMOS security anti-patterns (hardcoded secrets, insecure defaults)."

    sourceFiles.from(
        sourceExtensions.map { ext ->
            project.layout.projectDirectory.asFileTree.matching {
                include("src/main/**/*.$ext")
            }
        }
    )

    forbiddenPatterns.set(
        listOf(
            // Hardcoded secret or key patterns
            "password\\s*=\\s*\"[^\"]{4,}\"",
            "secret\\s*=\\s*\"[^\"]{4,}\"",
            "apiKey\\s*=\\s*\"[^\"]{4,}\"",
            // Disabling SSL/TLS verification
            "setHostnameVerifier\\s*\\(\\s*\\(\\s*.*?\\)\\s*->\\s*true",
            "ALLOW_ALL_HOSTNAME_VERIFIER",
            "TrustAllX509TrustManager",
            "trustAll",
            // Using MD5 or SHA1 for security-sensitive operations
            "MessageDigest\\.getInstance\\s*\\(\\s*\"MD5\"",
            "MessageDigest\\.getInstance\\s*\\(\\s*\"SHA-1\""
        )
    )
    violationPrefix.set("forbidden security anti-pattern")
    failurePrefix.set("Security review gate failed")
}

tasks.named("check") {
    dependsOn(
        "validateNoProductionStubs",
        "validateNoTestTheatre",
        "validateNoMockingFrameworkUsage",
        "validateNoSecurityAntiPatterns"
    )
}
