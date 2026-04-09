/**
 * Platform Contracts Module
 *
 * @doc.type build-script
 * @doc.purpose Shared contracts and Protobuf definitions for the platform
 * @doc.layer platform
 * @doc.pattern Contract
 */
plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.protobuf-conventions")
    id("com.ghatana.testing-conventions")
}

group = "com.ghatana.contracts"
version = "1.0-SNAPSHOT"
description = "Platform Contracts - Shared Protobuf definitions and schemas"

dependencies {
    // Protocol Buffers and gRPC
    api(libs.bundles.grpc.core)
    
    // JSON processing for schema generation
    implementation(libs.bundles.jackson.json)
    implementation(libs.bundles.jackson.yaml)
    
    // Schema generation libraries
    implementation("com.sun.codemodel:codemodel:2.6") 
    implementation("org.jsonschema2pojo:jsonschema2pojo-core:1.2.2")
    implementation("com.github.spullara.mustache.java:compiler:0.9.14")
    implementation("org.jsoup:jsoup:1.15.3")
    
    // Common utilities
    implementation(libs.bundles.common.utils)
    
    // Testing
    testImplementation(libs.bundles.testing.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Custom source set for schema generators
sourceSets {
    create("generators") {
        java {
            srcDirs("src/main/java")
            include("com/ghatana/contracts/schema/**/*.java")
        }
        compileClasspath += configurations.getByName("compileClasspath")
        runtimeClasspath += configurations.getByName("runtimeClasspath")
    }
}

// Schema generation tasks
val generateJsonSchemas = tasks.register<JavaExec>("generateJsonSchemas") {
    dependsOn("compileGeneratorsJava")
    dependsOn("generateProto")
    
    classpath = sourceSets["generators"].runtimeClasspath
    mainClass.set("com.ghatana.contracts.schema.ProtoToJsonSchemaGenerator")
    
    val descriptorFile = layout.buildDirectory.file("descriptors/main.desc")
    val outDir = layout.buildDirectory.dir("generated/schemas")
    
    inputs.file(descriptorFile)
    outputs.dir(outDir)

    args = listOf(
        "--descriptorSet=" + descriptorFile.get().asFile.absolutePath,
        "--outDir=" + outDir.get().asFile.absolutePath,
        "--bundle=bundle.schema.json",
        "--includeSourceInfo=true"
    )
    
    doFirst {
        outDir.get().asFile.mkdirs()
    }
}

val generatePojos = tasks.register<JavaExec>("generatePojos") {
    dependsOn(generateJsonSchemas)
    
    classpath = sourceSets["generators"].runtimeClasspath
    mainClass.set("com.ghatana.contracts.schema.JsonSchemaBundleToPojoGenerator")
    
    val bundleFile = layout.buildDirectory.file("generated/schemas/bundle.schema.json")
    val outDir = layout.buildDirectory.dir("generated/sources/pojo")
    
    inputs.file(bundleFile)
    outputs.dir(outDir)
    
    args = listOf(
        bundleFile.get().asFile.absolutePath,
        outDir.get().asFile.absolutePath,
        "--root=com",
        "--class-suffix=Pojo",
        "--include-prefix=ghatana"
    )
    
    doFirst {
        outDir.get().asFile.deleteRecursively()
        outDir.get().asFile.mkdirs()
    }
}

// Wire generation into build lifecycle (disabled temporarily for green build)
// tasks.named("compileJava") {
//     dependsOn(generatePojos)
// }

// tasks.matching { it.name == "sourcesJar" }.configureEach {
//     dependsOn(generatePojos)
// }

// Fix JaCoCo task dependencies
tasks.named("jacocoTestReport") {
    dependsOn("compileJava", "generateProto")
}
