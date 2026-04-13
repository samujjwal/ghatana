plugins {
    id("java-module")
}

description = "YAPPC Consolidated API Module"

dependencies {
    // Platform modules
    implementation(project(":platform:java:http"))

    // YAPPC modules
    implementation(project(":products:yappc:core:yappc-services"))
    implementation(project(":products:yappc:core:yappc-domain-impl"))
    implementation(project(":products:yappc:core:yappc-shared"))

    // REST API
    implementation(libs.activej.http)
    implementation(libs.activej.boot)

    // GraphQL
    implementation(libs.graphql.java)

    // Security - use Nimbus JWT (canonical)
    implementation(libs.nimbus.jose.jwt)

    // Validation

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}

// Handle duplicate entries in JAR tasks
tasks.withType<org.gradle.jvm.tasks.Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Source sets for API components
sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "src/rest/java",
                "src/graphql/java"
            )
        }
        resources {
            srcDirs(
                "src/main/resources",
                "src/rest/resources",
                "src/graphql/resources"
            )
        }
    }
    test {
        java {
            srcDirs(
                "src/test/java",
                "src/rest/test",
                "src/graphql/test"
            )
        }
    }
}

// API-specific tasks
tasks.register("generateApiDocs") {
    group = "documentation"
    description = "Generate API documentation"

    doLast {
        println("Generating API documentation...")
    }
}

tasks.register("validateApiSpecs") {
    group = "verification"
    description = "Validate API specifications"

    doLast {
        println("Validating API specifications...")
    }
}

// Make API docs generation part of build
tasks.named("build") {
    dependsOn("generateApiDocs")
}
