/**
 * Platform Kernel Bill of Materials (BOM)
 *
 * @doc.type build-script
 * @doc.purpose Aggregates all platform-kernel modules for easy dependency management
 * @doc.layer platform
 *
 * This BOM allows plugins to depend on all kernel modules with a single dependency:
 * implementation(platform("com.ghatana.kernel:kernel-bom:1.0.0"))
 *
 * This simplifies plugin development by providing automatic access to all kernel libraries.
 */
plugins {
    `java-platform`
}

group = "com.ghatana.kernel"
version = rootProject.version

description = "Platform Kernel BOM - aggregates all kernel modules"

dependencies {
    constraints {
        // Kernel core modules
        api(project(":platform-kernel:kernel-core"))
        api(project(":platform-kernel:kernel-plugin"))
        api(project(":platform-kernel:kernel-persistence"))
        api(project(":platform-kernel:kernel-testing"))
    }
}
