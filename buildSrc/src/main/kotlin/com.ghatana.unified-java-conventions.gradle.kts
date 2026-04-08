/**
 * Unified Java Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Provides consistent Java configuration across all modules
 * @doc.layer build
 *
 * This plugin configures:
 * - Java 21 toolchain
 * - UTF-8 encoding
 * - Compiler arguments for safety
 * - JUnit 5 test platform
 * - Test logging
 * - Lombok annotation processing
 */
plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Xlint:unchecked",
        "-Xlint:deprecation"
    ))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

dependencies {
    val lombokVersion = "1.18.36"
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
}
