import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * Unified Java Module Convention Plugin
 *
 * Combines: java-library + testing + quality + lombok conventions.
 */

plugins {
    `java-library`
    `idea`
    jacoco
    checkstyle
    pmd
    id("com.diffplug.spotless")
}

// Java 21 Toolchain
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withJavadocJar()
    withSourcesJar()
}

// Compiler Configuration
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.isIncremental = true
    options.compilerArgs.addAll(
        listOf("-parameters", "-Xlint:unchecked", "-Xlint:deprecation")
    )
}

// Javadoc - disabled by default
tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    isEnabled = false
}

// Testing Configuration
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showCauses = true
        showStackTraces = true
    }
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

// JaCoCo Configuration - use hardcoded version
configure<JacocoPluginExtension> {
    toolVersion = "0.8.14"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Checkstyle Configuration - use hardcoded version
configure<CheckstyleExtension> {
    toolVersion = "10.21.4"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    configProperties = mapOf(
        "suppressionFile" to rootProject.file("config/checkstyle/suppressions.xml").absolutePath
    )
    isIgnoreFailures = false
}

// PMD Configuration - use hardcoded version
configure<PmdExtension> {
    toolVersion = "7.11.0"
    isIgnoreFailures = true
}

// Spotless Configuration
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("**/*.gradle", "**/.gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
    isEnforceCheck = true
}

// JAR Manifest
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}

// Standard Dependencies - using version catalog from root project
dependencies {
    // Lombok - hardcoded version
    val lombokVersion = "1.18.36"
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.mockito:mockito-core:5.16.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")

    // Logging for tests
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.24.3")
}

// Dependency Guard: Block deprecated shared:* modules
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.ghatana" && requested.name.startsWith("shared-")) {
            throw GradleException(
                "Dependency on deprecated module '${requested.name}' is forbidden."
            )
        }
    }
}
