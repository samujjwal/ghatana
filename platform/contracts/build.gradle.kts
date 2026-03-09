plugins {
    id("java-library")
    alias(libs.plugins.protobuf)
}

group = "com.ghatana.contracts"
version = "1.0-SNAPSHOT"

dependencies {
    api(libs.protobuf.java)
    api(libs.protobuf.java.util)
    
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty)
    implementation(libs.grpc.core)
    implementation(libs.google.common.protos)
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    
    // For Schema Generation
    implementation(libs.jackson.databind)
    implementation("com.sun.codemodel:codemodel:2.6") 
    implementation("org.jsonschema2pojo:jsonschema2pojo-core:1.2.2")
    implementation("com.github.spullara.mustache.java:compiler:0.9.14")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("commons-io:commons-io:2.11.0")

    // Guava
    implementation(libs.guava)

    // Testing
    testImplementation(libs.jackson.dataformat.yaml)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
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

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/sources/pojo"))
        }
    }
    test {
        resources {
            srcDir("openapi")
        }
    }
    create("generators") {
        java {
            srcDirs("src/main/java")
            include("com/ghatana/contracts/schema/**/*.java")
        }
        compileClasspath += configurations.getByName("compileClasspath")
        runtimeClasspath += configurations.getByName("runtimeClasspath")
    }
}

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
        delete(outDir)
        outDir.get().asFile.mkdirs()
    }
}

tasks.named("compileJava") {
    dependsOn(generatePojos)
}

tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn(generatePojos)
}
