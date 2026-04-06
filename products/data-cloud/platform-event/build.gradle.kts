plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud Platform Event Module"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":products:data-cloud:spi"))
    api(project(":products:data-cloud:platform-entity"))
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform-kernel:kernel-plugin"))

    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.jakarta.persistence.api)

    implementation(libs.hibernate.core)
    implementation(libs.kafka.clients)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()
}

tasks.javadoc {
    dependsOn(tasks.compileJava)
    classpath += sourceSets.main.get().compileClasspath
    (options as StandardJavadocDocletOptions).apply {
        links("https://docs.oracle.com/en/java/javase/21/docs/api/")
        isFailOnError = false  // Continue on javadoc errors (Lombok symbol issues)
    }
}