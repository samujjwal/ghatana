plugins {
    id("java-library")
    id("maven-publish")
}

group = "com.ghatana.testing"
version = "2026.3.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

// Repository configuration is centralized in settings.gradle.kts

dependencies {
    // Common test dependencies
    testImplementation(libs.junit.bom)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.junit.jupiter)
    
    // Logging
    testRuntimeOnly(libs.logback.classic)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ghatana/testing")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}

// Enable Javadoc linting
tasks.named<Javadoc>("javadoc") {
    options.addStringOption("Xdoclint:all,-missing", "-quiet")
}

// Configure code quality plugins
if (project.hasProperty("enableCheckstyle")) {
    apply(plugin = "checkstyle")
    
    configure<CheckstyleExtension> {
        toolVersion = "10.3.3"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        configProperties = mapOf(
            "checkstyle.cache.file" to "${buildDir}/checkstyle.cache"
        )
        isIgnoreFailures = false
        isShowViolations = true
    }
}

// Configure test coverage
if (project.hasProperty("enableJacoco")) {
    apply(plugin = "jacoco")
    
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.8"
    }
    
    tasks.named<JacocoReport>("jacocoTestReport") {
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }
    
    tasks.named<Test>("test") {
        finalizedBy("jacocoTestReport")
    }
}
