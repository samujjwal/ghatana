import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * Java Application Convention Plugin
 */

plugins {
    `java-library`
    `idea`
    application
    jacoco
    checkstyle
    pmd
    id("com.diffplug.spotless")
}

// Property to control Javadoc generation (disabled by default for speed)
val enableJavadoc = project.findProperty("enableJavadoc")?.toString()?.toBoolean() ?: false

val sharedConfigRoot = generateSequence(rootProject.rootDir) { it.parentFile }
    .firstOrNull { candidate ->
        File(candidate, "config/checkstyle/checkstyle.xml").exists() &&
            File(candidate, "config/pmd/minimal-ruleset.xml").exists()
    }
    ?: rootProject.rootDir

fun sharedConfigFile(path: String): File = File(sharedConfigRoot, path)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    if (enableJavadoc) {
        withJavadocJar()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

configure<JacocoPluginExtension> {
    toolVersion = "0.8.14"
}

configure<CheckstyleExtension> {
    toolVersion = "10.21.4"
    configFile = sharedConfigFile("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

configure<PmdExtension> {
    toolVersion = "7.11.0"
    ruleSetFiles = files(sharedConfigFile("config/pmd/minimal-ruleset.xml"))
    ruleSets = emptyList()
    isIgnoreFailures = false
    isConsoleOutput = true
}

tasks.withType<org.gradle.api.plugins.quality.Pmd>().configureEach {
    val rulesetFile = if (name.contains("Test", ignoreCase = true)) {
        sharedConfigFile("config/pmd/test-ruleset.xml")
    } else {
        sharedConfigFile("config/pmd/minimal-ruleset.xml")
    }
    val sourceDirectory = if (name.contains("Test", ignoreCase = true)) {
        "src/test/java"
    } else {
        "src/main/java"
    }
    ruleSetFiles = files(rulesetFile)
    ruleSets = emptyList()
    source = fileTree(sourceDirectory) {
        exclude("**/generated/**")
        exclude("**/build/generated/**")
        exclude("**/*Grpc.java")
        exclude("**/*Proto.java")
        exclude("**/*_Grpc*.java")
        exclude("**/grpc/**")
        exclude("**/proto/**")
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("*.gradle", "*.gradle.kts", ".gitignore")
        targetExclude("**/node_modules/**", "**/build/**", "**/.gradle/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

application {
    mainClass.set("com.ghatana.${project.name}.Application")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    val lombokVersion = "1.18.36"
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}
