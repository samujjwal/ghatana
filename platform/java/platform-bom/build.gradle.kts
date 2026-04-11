/**
 * Platform Libraries Bill of Materials (BOM)
 *
 * @doc.type build-script
 * @doc.purpose Aggregates common platform libraries for easy dependency management
 * @doc.layer platform
 *
 * This BOM allows plugins to depend on all common platform libraries with a single dependency:
 * implementation(platform("com.ghatana.platform:platform-bom:2026.3.1-SNAPSHOT"))
 *
 * This simplifies plugin development by providing automatic access to platform libraries
 * like core, database, http, observability, etc.
 */
plugins {
    `java-platform`
}

group = "com.ghatana.platform"
version = rootProject.version

description = "Platform Libraries BOM - aggregates common platform modules"

dependencies {
    constraints {
        // Core platform libraries
        api(project(":platform:java:core"))
        api(project(":platform:java:domain"))
        api(project(":platform:java:database"))
        api(project(":platform:java:http"))
        api(project(":platform:java:observability"))
        api(project(":platform:java:testing"))
        api(project(":platform:java:config"))
        api(project(":platform:java:workflow"))
        api(project(":platform:java:audit"))
        api(project(":platform:java:security"))
        api(project(":platform:java:agent-core"))
    }
}
