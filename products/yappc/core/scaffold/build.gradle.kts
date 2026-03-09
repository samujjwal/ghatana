// yappc-scaffold root build file
//
// This project is a multi-module subproject inside the larger `polyfix` build.
// Module-level build files live in each subdirectory (cli/, core/, adapters/, packs/, schemas/)
// and define their own plugins and dependencies. Keeping this root file minimal prevents
// duplicated dependency declarations and respects the repository management configured in
// the parent `polyfix/settings.gradle` (repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS).

// If you need to apply common configuration across the scaffold modules, prefer a separate
// Gradle convention plugin or add a small `subprojects {}` block here but avoid declaring
// repositories at the project level.

// Example (commented):
// subprojects {
//     // common compile options, tasks, or conventions
// }
