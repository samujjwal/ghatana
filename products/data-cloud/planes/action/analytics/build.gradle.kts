plugins {
    id("java-module")
}

dependencies {
    api(project(":products:data-cloud:planes:action:operator-contracts"))
    api(project(":products:data-cloud:planes:action:engine"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:agent-core"))
    api(project(":platform:contracts"))

    implementation(libs.activej.promise)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    implementation(libs.micrometer.core)

    testImplementation(libs.bundles.testing.core)
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
