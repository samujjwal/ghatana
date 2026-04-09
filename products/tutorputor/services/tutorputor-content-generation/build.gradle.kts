plugins {
    java
    application
    kotlin("jvm") version "1.9.22"
    id("jacoco")
    id("com.ghatana.protobuf-conventions")
}

group = "com.ghatana.tutorputor"
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}


dependencies {
    // Platform AI integration (replaces all stub implementations)
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:contracts"))
    
    // ActiveJ framework
    implementation(libs.activej.boot)
    implementation(libs.activej.promise)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    
    // LLM integration (from ai-agents)
    implementation("dev.langchain4j:langchain4j:0.34.0")
    implementation("dev.langchain4j:langchain4j-open-ai:0.34.0")
    implementation("dev.langchain4j:langchain4j-ollama:0.34.0")
    
    // gRPC (from ai-agents)
    implementation("io.grpc:grpc-netty-shaded:1.60.0")
    implementation("io.grpc:grpc-protobuf:1.60.0")
    implementation("io.grpc:grpc-stub:1.60.0")
    implementation("com.google.protobuf:protobuf-java:3.25.1")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")
    
    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core:1.12.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
    
    // Jackson for JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.ghatana.tutorputor.contentgeneration.ContentGenerationLauncher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    reports {
        html.required.set(true)
        junitXml.required.set(true)
    }
}

// Code coverage with JaCoCo
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.00".toBigDecimal() // 0% coverage requirement (temporarily disabled)
            }
        }
    }
}




// Handle duplicate proto files in resources
tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Handle sourcesJar duplicates from protobuf
tasks.withType<org.gradle.api.tasks.bundling.Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
