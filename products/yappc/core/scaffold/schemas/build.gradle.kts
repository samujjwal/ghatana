// DEPRECATED — JSON schemas already bundled in :products:yappc:core:scaffold:core resources
// This module is a backward-compatibility stub. Use core:scaffold:core directly.
plugins {
    id("java-library")
}

dependencies {
    api(project(":products:yappc:core:scaffold:core"))
}
