// BACKWARD-COMPAT STUB — source has moved to :services:platform
// This module is retained for backwards compatibility. Consumers should migrate to:
//   implementation(project(":products:yappc:services:platform"))
plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc.services"
version = rootProject.version.toString()
base.archivesName.set("yappc-services-infrastructure")

description = "[DEPRECATED] Use :services:platform instead. Backward-compat stub exposing the platform module."

dependencies {
    // Expose all platform classes transitively to any consumer still depending on :services:infrastructure
    api(project(":products:yappc:services:platform"))
}

