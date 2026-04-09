/**
 * Protocol Buffers Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Provides standardized Protobuf and gRPC configuration for modules
 *              that need protocol buffer compilation and gRPC code generation.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * Usage:
 *   plugins {
 *       id("com.ghatana.java-conventions")
 *       id("com.ghatana.protobuf-conventions")
 *   }
 */

plugins {
    alias(libs.plugins.protobuf)
}

// Protobuf configuration
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
    }
    
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
            task.generateDescriptorSet = true
            task.descriptorSetOptions.path = layout.buildDirectory.file("descriptors/${task.sourceSet.name}.desc").get().asFile.path
            task.descriptorSetOptions.includeImports = true
            task.descriptorSetOptions.includeSourceInfo = true
        }
    }
}

// Add generated sources to main source set
sourceSets.main {
    java {
        srcDir(layout.buildDirectory.dir("generated/sources/proto/main/java"))
        srcDir(layout.buildDirectory.dir("generated/sources/proto/main/grpc"))
    }
}

// Wire protobuf generation into Java compilation
tasks.named("compileJava") {
    dependsOn("generateProto")
}

// Wire protobuf generation into sources JAR
tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn("generateProto")
}
