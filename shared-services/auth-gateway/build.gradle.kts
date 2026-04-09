plugins {
    application
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
}

group = "com.ghatana.services"

application {
    mainClass.set("com.ghatana.services.auth.AuthGatewayLauncher")
}

dependencies {
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:database"))
    implementation(libs.bundles.activej.http)
    implementation(libs.bundles.security.core)
    implementation("org.mindrot:jbcrypt:0.4")
    implementation(libs.caffeine)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.guava)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.testing.containers)
}
