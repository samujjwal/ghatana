plugins {
    id("java-module")
}

group = rootProject.group
version = rootProject.version


dependencies {
    // api(project(":libs:types")) - path needs verification
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))

    // JPA and Hibernate APIs used by entities
    api(libs.jakarta.persistence.api)
    api(libs.hibernate.core)

    // Hypersistence / hibernate-types for JSON/array mapping
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")

    // Expose DTO packages for domain consumers so domain services can reference
    // stable request/response types. These DTOs are added to this libs module
    // as minimal placeholders during migration; they are internal to the
    // product domain and can be replaced with richer canonical types later.
    // No additional dependency needed; DTOs are provided as source files.

    // ActiveJ Promise is required for async repository contracts.
    api(libs.activej.promise)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // finalizedBy(jacocoTestReport) is handled by java-module when coverage is enabled
}


tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value   = "COVEREDRATIO"
                minimum = "0.15".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value   = "COVEREDRATIO"
                minimum = "0.15".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            exclude(
                "**/package-info.class",
                "**/*Config.class",
                "**/*Module.class",
                "**/*Launcher.class",
                "**/*Bootstrapper.class",
                "**/generated/**"
            )
        }
    )
}

// The libs module should compile only its local, shared stable domain contracts
// and DTO placeholders. Do NOT pull in product source directories here; doing
// so can create duplicate-class problems when the same types remain in product
// modules. Keep product modules compiling against these shared types via an
// explicit project dependency instead of mixing source roots.
sourceSets {
    named("main") {
        java {
            setSrcDirs(listOf(file("${projectDir}/src/main/java")))
        }
        resources {
            setSrcDirs(listOf(file("${projectDir}/src/main/resources")))
        }
    }
}

description = "Shared YAPPC domain models (stable contracts and DTOs)"
