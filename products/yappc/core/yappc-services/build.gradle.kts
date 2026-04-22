plugins {
    id("java-module")
}

description = "YAPPC Consolidated Services Module"

dependencies {
    // Platform modules
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:security"))

    // YAPPC domain (both core and libs versions)
    implementation(project(":products:yappc:core:yappc-domain-impl"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // YAPPC agents runtime (for AepEventPublisher)
    implementation(project(":products:yappc:core:agents:runtime"))

    // Data-Cloud SPI (for DataCloudClient)
    // TODO(ADAPTER-SEAM): data-cloud coupling in a domain services module.
    //   Future: introduce DataCloudPort in core; move impl to infrastructure:datacloud
    implementation(project(":products:data-cloud:spi"))
    implementation(project(":products:yappc:infrastructure:datacloud"))

    // YAPPC infrastructure
    implementation(project(":products:yappc:core:yappc-infrastructure"))

    // YAPPC shared utilities
    implementation(project(":products:yappc:core:yappc-shared"))

    // Validation

    // JSON processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Async processing
    implementation(libs.activej.promise)

    // Graph algorithms and caching
    implementation(libs.jgrapht.core)
    implementation(libs.caffeine)

    // Language-specific heavy extractors
    implementation(libs.javaparser.core)
    implementation(libs.jooq)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("native")
    }
}

// Handle duplicate entries in JAR tasks
tasks.withType<org.gradle.jvm.tasks.Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Handle duplicate entries in processResources task
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Source sets for service components
sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "src/orchestration/java",
                "src/validation/java",
                "src/transformation/java"
            )
        }
        resources {
            srcDirs(
                "src/main/resources",
                "src/orchestration/resources",
                "src/validation/resources",
                "src/transformation/resources"
            )
        }
    }
    test {
        java {
            srcDirs(
                "src/test/java",
                "src/orchestration/test",
                "src/validation/test",
                "src/transformation/test"
            )
        }
    }
}

// Service-specific tasks
tasks.register("validateServices") {
    group = "verification"
    description = "Validate service configurations"

    doLast {
        println("Validating service configurations...")
    }
}

tasks.register("serviceHealthCheck") {
    group = "verification"
    description = "Health check for all services"

    doLast {
        println("Running service health checks...")
    }
}

// --- Tree-sitter JNI native build --------------------------------------
tasks.register<Exec>("cmakeConfigureTreeSitter") {
    group = "native"
    description = "Configure tree-sitter JNI build with CMake"

    val nativeDir = file("src/main/native")
    val nativeBuild = layout.buildDirectory.get().asFile.resolve("native")

    inputs.dir(nativeDir)
    outputs.dir(nativeBuild)

    doFirst {
        nativeBuild.mkdirs()
    }

    commandLine("cmake", "-S", nativeDir, "-B", nativeBuild, "-DCMAKE_BUILD_TYPE=Release")
}

tasks.register<Exec>("buildTreeSitterJni") {
    group = "native"
    description = "Build tree-sitter JNI shared library via CMake"
    dependsOn("cmakeConfigureTreeSitter")

    val nativeBuild = layout.buildDirectory.get().asFile.resolve("native")

    inputs.dir(nativeBuild)
    outputs.files(
        file("${nativeBuild}/libtree_sitter_jni${System.mapLibraryName("").substringAfterLast(".")}"),
        file("${nativeBuild}/tree_sitter_jni.dll")
    )

    commandLine("cmake", "--build", nativeBuild, "--parallel")

    doLast {
        println("Tree-sitter JNI native library built in: ${nativeBuild}")
    }
}

tasks.register<Copy>("copyTreeSitterJni") {
    group = "native"
    description = "Copy built tree-sitter JNI library to run directory"
    dependsOn("buildTreeSitterJni")

    val nativeBuild = layout.buildDirectory.get().asFile.resolve("native")
    val libName = System.mapLibraryName("tree_sitter_jni")
    from(file("${nativeBuild}/${libName}"))
    into(layout.buildDirectory.get().asFile.resolve("libs"))
}
