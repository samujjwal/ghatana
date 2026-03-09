plugins {
    `java-library`
}

group = rootProject.group
version = rootProject.version

java {
    // Use the repository-level Java toolchain via conventions if available
    toolchain {
        // Match the repository-wide Java source/target (Java 21) to avoid mixed-source
        // release issues when the root build config enforces Java 21. Gradle will
        // provision the appropriate toolchain if available on the machine.
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
    }
}

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

    // Lombok is used heavily in the product domain sources. The repository-level
    // build helper normally auto-adds Lombok to projects that contain src/main/java
    // files, but this libs module compiles sources from a different directory
    // (products/yappc/domain), so add Lombok explicitly here.
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
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
