plugins {
    java

}

group = "com.ghatana.softwareorg"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":products:software-org:engine:modules:domain-model"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}
