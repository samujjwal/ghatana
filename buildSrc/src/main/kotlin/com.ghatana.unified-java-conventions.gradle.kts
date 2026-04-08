/**
 * @deprecated — superseded by com.ghatana.java-conventions (merged 2026-04-08).
 *
 * This plugin was an exact functional duplicate of com.ghatana.java-conventions.
 * All configuration (Java 21 toolchain, sourceCompatibility, compiler args, JUnit
 * Platform, Lombok wiring, test logging) has been merged into the canonical plugin.
 *
 * Migration — replace in your module's build.gradle.kts:
 *
 *   // Before
 *   id("com.ghatana.unified-java-conventions")
 *
 *   // After
 *   id("com.ghatana.java-conventions")
 *
 * This file is kept as a no-op forward-delegate to prevent build failures on any
 * module that has not yet been migrated.  It will be deleted in a future cleanup.
 */

// Forward all configuration to the canonical plugin.
apply(plugin = "com.ghatana.java-conventions")
