plugins {
    id("java")
    id("org.owasp.dependencycheck") version "12.1.6"
    id("com.github.spotbugs") version "6.4.2" apply false
    // NOTE: spotless plugin is provided via buildSrc and applied selectively via conventions;
    // do NOT re-declare it here to avoid plugin marker resolution conflicts.
    // Individual modules apply it by referencing the convention plugin.
}

group = "com.ghatana.products.yappc"
version = "2026.3.1-SNAPSHOT"

description = "YAPPC — AI-Native Product Development Platform"

// Add dependencies for JSON and YAML processing
buildscript {
    dependencies {
        classpath("org.json:json:20231013")
        classpath("org.yaml:snakeyaml:2.0")
    }
}

// ============================================================================
// Shared Configuration for All Subprojects
// ============================================================================
subprojects {
    if (!file("$projectDir/src/main/java").exists() &&
        !file("$projectDir/src/main/kotlin").exists() &&
        !file("$projectDir/src/test/java").exists()) {
        return@subprojects
    }

    apply(plugin = "java-library")

    group = "com.ghatana.products.yappc"
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }

    val spotbugsProjects = setOf(
        ":products:yappc:backend:api",
        ":products:yappc:services:ai",
        ":products:yappc:services:platform",  // replaces the merged domain + infrastructure
        ":products:yappc:services:lifecycle",
        ":products:yappc:services:scaffold"
    )

    if (path in spotbugsProjects) {
        apply(plugin = "com.github.spotbugs")

        configure<com.github.spotbugs.snom.SpotBugsExtension> {
            effort.set(com.github.spotbugs.snom.Effort.MAX)
            reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
            excludeFilter.set(rootProject.file("config/spotbugs/spotbugs-exclude.xml"))
        }

        tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
            reports {
                create("html") {
                    required.set(true)
                }
                create("xml") {
                    required.set(false)
                }
            }
        }

        tasks.matching { it.name == "check" }.configureEach {
            dependsOn("spotbugsMain")
        }
    }

    // ========================================================================
    // Dependency Governance Constraints
    // ========================================================================
    configurations.all {
        // Ban direct LangChain4J — use platform:java:ai-integration instead
        exclude(group = "dev.langchain4j", module = "langchain4j")
        exclude(group = "dev.langchain4j", module = "langchain4j-open-ai")
        exclude(group = "dev.langchain4j", module = "langchain4j-anthropic")

        // Ban Spring Reactor — use ActiveJ Promise
        exclude(group = "io.projectreactor", module = "reactor-core")

        // Ban RxJava — use ActiveJ Promise
        exclude(group = "io.reactivex.rxjava3", module = "rxjava")
    }
}

configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
    suppressionFile = rootProject.file("../../config/owasp-suppressions.xml").absolutePath
    formats = listOf("HTML", "JSON")
    failBuildOnCVSS = 9.0F
}

// ============================================================================
// Config & Catalog Validation Tasks
// ============================================================================
// Validation tasks are maintained in a dedicated script for readability.
// Tasks registered: validateAgentCatalog, validateEventSchemas, validatePipelines,
//                   validateLifecycleConfig, validateWorkflowConfig, validatePolicyConfig
apply(from = "gradle/yappc-validations.gradle.kts")
