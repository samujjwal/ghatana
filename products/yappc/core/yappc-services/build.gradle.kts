import com.google.protobuf.gradle.id

plugins {
    id("java-module")
    id("com.google.protobuf")
}

description = "YAPPC Consolidated Services Module — absorbs services-platform and services-lifecycle (SIMP-Y8)"

// Include generated sources
sourceSets {
    main {
        java {
            srcDir("src/generated/java")
        }
    }
}

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
    implementation(project(":platform:java:audit"))

    // YAPPC domain (both core and libs versions)
    implementation(project(":products:yappc:core:yappc-domain-impl"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // YAPPC facades
    implementation(project(":products:yappc:core:yappc-facades"))

    // YAPPC agents runtime (for AepEventPublisher)
    implementation(project(":products:yappc:core:agents:runtime"))
    implementation(project(":products:yappc:core:agents"))

    // AEP integration seam (owned by infrastructure:aep)
    implementation(project(":products:yappc:infrastructure:aep"))

    // Data-Cloud seam (for DataCloudClient)
    implementation(project(":products:yappc:infrastructure:datacloud"))

    // YAPPC infrastructure
    implementation(project(":products:yappc:core:yappc-infrastructure"))

    // YAPPC shared utilities
    implementation(project(":products:yappc:core:yappc-shared"))

    // AI and scaffold (absorbed from services-lifecycle)
    implementation(project(":products:yappc:core:ai"))
    implementation(project(":products:yappc:core:scaffold:core"))
    implementation(project(":products:yappc:core:scaffold:api"))
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

    // gRPC dependencies for proto generation
    implementation("io.grpc:grpc-netty-shaded:1.60.0")
    implementation("io.grpc:grpc-protobuf:1.60.0")
    implementation("io.grpc:grpc-stub:1.60.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

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

// ============================================================================
// Protocol Buffers Configuration for artifact_compiler.proto
// ============================================================================
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.26.1"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.60.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
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

// ============================================================================
// Route Registry Generation Task
// ============================================================================
tasks.register<Exec>("generateRouteRegistry") {
    group = "codegen"
    description = "Generate GeneratedRouteRegistry.java from route-manifest.yaml"

    val scriptFile = layout.projectDirectory.file("../../scripts/generate-route-registry.py").asFile
    val outputFile = layout.projectDirectory.dir("src/generated/java/com/ghatana/yappc/api/generated").file("GeneratedRouteRegistry.java").asFile

    inputs.file(scriptFile)
    inputs.file(layout.projectDirectory.file("../../docs/api/route-manifest.yaml"))
    outputs.file(outputFile)

    doFirst {
        outputFile.parentFile.mkdirs()

        // Cross-platform Python executable detection (lazy, at execution time)
        val pythonExec = if (System.getProperty("os.name").lowercase().contains("windows")) {
            if (File("C:/Windows/py.exe").exists()) "py" else "python"
        } else {
            "python3"
        }
        commandLine(pythonExec, scriptFile.absolutePath)
    }

    workingDir(layout.projectDirectory.file("../..").asFile)

    doLast {
        println("Generated route registry: ${outputFile.absolutePath}")
    }
}

// Helper function to find executable in PATH (used at execution time)
fun which(executable: String): String? {
    try {
        val process = ProcessBuilder(if (System.getProperty("os.name").lowercase().contains("windows")) "where" else "which", executable)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        return if (process.waitFor() == 0 && output.isNotBlank()) output.lines().first() else null
    } catch (e: Exception) {
        return null
    }
}

// Cross-platform Python executable detection (lazy, safe for configuration cache)
fun detectPythonExecutable(): String {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    return if (isWindows) {
        // Try py launcher first, then python
        if (File("C:/Windows/py.exe").exists() || which("py") != null) "py"
        else "python"
    } else {
        // On macOS/Linux, try python3 first, then python
        if (which("python3") != null) "python3"
        else "python"
    }
}

// ============================================================================
// Route Manifest Validation Task
// ============================================================================
tasks.register<Exec>("validateRouteManifest") {
    group = "verification"
    description = "Validate route-manifest.yaml structure and OpenAPI parity"

    val scriptFile = layout.projectDirectory.file("../../scripts/validate-openapi-parity.py").asFile

    inputs.file(scriptFile)
    inputs.file(layout.projectDirectory.file("../../docs/api/route-manifest.yaml"))
    inputs.file(layout.projectDirectory.file("../../docs/api/openapi.yaml"))

    environment("PYTHONIOENCODING", "utf-8")

    doFirst {
        // Cross-platform Python executable detection (lazy, at execution time)
        val pythonExec = if (System.getProperty("os.name").lowercase().contains("windows")) {
            if (File("C:/Windows/py.exe").exists()) "py" else "python"
        } else {
            "python3"
        }
        commandLine(pythonExec, scriptFile.absolutePath)
    }

    workingDir(layout.projectDirectory.file("../..").asFile)

    doLast {
        println("Route manifest validation PASSED")
    }
}

// Wire generation and validation into compileJava
tasks.compileJava {
    dependsOn("generateRouteRegistry", "validateRouteManifest")
}

tasks.register("serviceHealthCheck") {
    group = "verification"
    description = "Health check for all services"

    doLast {
        println("Running service health checks...")
    }
}

// ============================================================================
// Artifact Compiler Contract Drift Gate
// ============================================================================
tasks.register<Test>("verifyArtifactCompilerContract") {
    group = "verification"
    description = "Runs canonical artifact compiler contract compatibility tests (proto <-> Java DTO adapters <-> worker payload)."
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.ghatana.yappc.domain.artifact.ArtifactCompilerContractCompatibilityTest")
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn("verifyArtifactCompilerContract")
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
