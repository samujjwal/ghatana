plugins {
    id("java-library")
}

group = "com.ghatana.products"

dependencies {
    // Platform Dependencies - reuse canonical platform modules
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:contracts"))

    // ActiveJ framework
    implementation(libs.activej.promise)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.config)

    // Security / JWT / OAuth
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.nimbus.oauth2.sdk)
    implementation(libs.bouncycastle.provider)

    // Jakarta APIs
    implementation(libs.javax.inject)
    implementation(libs.javax.mail)

    // Caching
    implementation(libs.caffeine)

    // Metrics
    implementation(libs.micrometer.core)

    // gRPC interceptors
    implementation(libs.grpc.stub)
    implementation(libs.grpc.core)

    // JPA persistence
    implementation(libs.jakarta.persistence.api)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.datatype.jdk8)

    // Redis client
    implementation(libs.jedis)

    // Logging
    implementation(libs.slf4j.api)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
