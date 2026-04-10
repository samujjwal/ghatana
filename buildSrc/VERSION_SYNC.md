# buildSrc Version Synchronization

## Background

Gradle's buildSrc has an isolated classloader and CANNOT access:
- The main project's version catalog (gradle/libs.versions.toml)
- The main project's gradle.properties
- Project properties via `val x: String by project`

This is a known Gradle limitation.

## Solution

1. **buildSrc classpath dependencies**: Use `buildSrc/gradle.properties` as the single source of truth.
   This file is kept in sync with the main version catalog.

2. **Convention plugin tool versions**: Hardcoded in convention plugins themselves.
   These versions must be kept in sync with `gradle/libs.versions.toml` manually.
   Convention plugins cannot read from buildSrc/gradle.properties due to the isolated classloader.

## Sync Rules

1. When updating a version in `gradle/libs.versions.toml`:
   - If it's a buildSrc classpath dependency (spotless, saxon, etc.), update `buildSrc/gradle.properties`
   - If it's a convention plugin tool version (checkstyle, pmd, jacoco, lombok), update the convention plugin file directly
   - Do both in the same commit

2. Property naming convention for buildSrc/gradle.properties:
   - buildSrc/gradle.properties: camelCase (e.g., `spotlessVersion`)
   - gradle/libs.versions.toml: kebab-case (e.g., `spotless`)

3. The CI "Verify buildSrc version sync" step enforces buildSrc classpath dependency sync only.

## Current Mappings

### buildSrc Classpath Dependencies (validated by CI)

| buildSrc/gradle.properties | gradle/libs.versions.toml |
|---------------------------|--------------------------|
| spotlessVersion           | spotless                 |
| spotbugsPluginVersion     | spotbugs-plugin          |
| saxonHeVersion            | saxon-he                 |
| httpclient5Version        | httpclient5              |
| httpcore5Version          | httpcore5                |

### Convention Plugin Tool Versions (manual sync required)

| Convention Plugin | Version | gradle/libs.versions.toml |
|------------------|---------|--------------------------|
| lombok-conventions | 1.18.36 | lombok |
| quality-conventions (Checkstyle) | 10.21.4 | checkstyle |
| quality-conventions (PMD) | 7.11.0 | pmd |
| quality-conventions (JaCoCo) | 0.8.14 | jacoco |
| testing-conventions (JaCoCo) | 0.8.14 | jacoco |

## Adding New buildSrc Dependencies

1. Add version to `buildSrc/gradle.properties` (if classpath dependency)
2. Add corresponding version to `gradle/libs.versions.toml`
3. Add dependency to `buildSrc/build.gradle.kts`
4. Update this documentation

## Updating Convention Plugin Tool Versions

1. Update version in convention plugin file (hardcoded)
2. Update version in `gradle/libs.versions.toml`
3. Update this documentation
4. Do both in the same commit
