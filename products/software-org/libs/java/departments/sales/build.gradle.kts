plugins {
    java

}

dependencies {
    implementation(project(":products:software-org:engine:modules:domain-model"))
    implementation(project(":platform:java:observability"))
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
}
