/**
 * Gradle TestKit tests for YAPPC validation tasks.
 *
 * @doc.type module
 * @doc.purpose Gradle TestKit integration tests validating the yappc-validations.gradle.kts tasks
 * @doc.layer product
 * @doc.pattern Test
 */
plugins {
    id("java")
}

group = "com.ghatana.products.yappc"
version = rootProject.version


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.test {
    useJUnitPlatform()

    // Pass the absolute path to the validations script so tests can reference it
    val validationScriptPath = System.getProperty("validationScriptPathOverride")
        ?: file("${rootProject.projectDir}/gradle/yappc-validations.gradle.kts").absolutePath
    systemProperty(
        "validationScriptPath",
        validationScriptPath
    )
}
