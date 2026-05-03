plugins {
    id("java-module")
}

description = "YAPPC Consolidated Services Module — absorbs services-platform and services-lifecycle (SIMP-Y8)"

dependencies {
    // Platform modules
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:runtime"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:agent-core"))

    // YAPPC domain (both core and libs versions)
    implementation(project(":products:yappc:core:yappc-domain-impl"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // YAPPC agents runtime (for AepEventPublisher)
    implementation(project(":products:yappc:core:agents:runtime"))
    implementation(project(":products:yappc:core:agents"))

    // AEP integration (absorbed from services-lifecycle)
    implementation(project(":products:aep:aep-operator-contracts"))
    implementation(project(":products:aep:orchestrator"))
    implementation(project(":products:aep:aep-engine"))
    implementation(project(":products:aep:aep-agent-runtime"))

    // Data-Cloud SPI (for DataCloudClient)
    // DataCloud SPI included directly; DataCloudPort decoupling tracked in architecture backlog
    implementation(project(":products:data-cloud:spi"))
    implementation(project(":products:yappc:infrastructure:datacloud"))

    // YAPPC infrastructure
    implementation(project(":products:yappc:core:yappc-infrastructure"))

    // YAPPC shared utilities
    implementation(project(":products:yappc:core:yappc-shared"))

    // AI and scaffold (absorbed from services-lifecycle)
    implementation(project(":products:yappc:core:ai"))
    implementation(project(":products:yappc:core:scaffold:core"))
    implementation(project(":platform-kernel:kernel-plugin"))

    // ActiveJ for async + HTTP
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.http)
    implementation(libs.activej.boot)
    implementation(libs.activej.launcher)

    // JSON + YAML Processing
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)

    // Protobuf for JSON formatting (absorbed from services-lifecycle)
    implementation(libs.protobuf.java)
    implementation("com.google.protobuf:protobuf-java-util:4.34.1")

    // JSON Schema validation for configuration governance
    implementation(libs.networknt.validator)

    // Database (absorbed from services-platform)
    implementation("com.zaxxer:HikariCP:5.1.0")
    runtimeOnly(libs.postgresql)

    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")

    // Observability — Prometheus metrics scrape endpoint
    implementation(libs.micrometer.registry.prometheus)

    // LLM (absorbed from services-lifecycle)
    implementation("dev.langchain4j:langchain4j:0.25.0")

    // Graph algorithms and caching
    implementation(libs.jgrapht.core)
    implementation(libs.caffeine)

    // Language-specific heavy extractors
    implementation(libs.javaparser.core)
    implementation(libs.jooq)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator.annprocess)
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
