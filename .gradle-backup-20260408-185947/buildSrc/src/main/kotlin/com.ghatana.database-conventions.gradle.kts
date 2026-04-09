/**
 * Database Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Provides standardized database configuration for modules
 *              that need database access, migrations, and testing.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * Usage:
 *   plugins {
 *       id("com.ghatana.java-conventions")
 *       id("com.ghatana.database-conventions")
 *   }
 *
 * Configuration:
 *   database {
 *       migrations.enabled.set(true)
 *       testing.enabled.set(true)
 *       pool.size.set(10)
 *   }
 */

interface DatabaseConventionExtension {
    val migrations: MigrationExtension
    val testing: TestingExtension
    val pool: PoolExtension
}

interface MigrationExtension {
    val enabled: Property<Boolean>
    val locations: ListProperty<String>
    val baselineOnMigrate: Property<Boolean>
}

interface TestingExtension {
    val enabled: Property<Boolean>
    val containers: ContainerExtension
}

interface ContainerExtension {
    val enabled: Property<Boolean>
    val image: Property<String>
}

interface PoolExtension {
    val size: Property<Int>
    val timeout: Property<Long>
}

// Create the extension
val extension = project.extensions.create<DatabaseConventionExtension>("database")

// Configure defaults
extension.migrations.enabled.convention(true)
extension.migrations.locations.convention(listOf("classpath:db/migration"))
extension.migrations.baselineOnMigrate.convention(false)

extension.testing.enabled.convention(true)
extension.testing.containers.enabled.convention(true)
extension.testing.containers.image.convention("postgres:15-alpine")

extension.pool.size.convention(10)
extension.pool.timeout.set(30000L)

// Apply Flyway plugin if migrations are enabled
if (extension.migrations.enabled.get()) {
    apply(plugin = "org.flywaydb.flyway")
    
    configure<org.flywaydb.gradle.FlywayExtension> {
        url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
        user = "sa"
        password = ""
        locations = extension.migrations.locations.get()
        baselineOnMigrate = extension.migrations.baselineOnMigrate.get()
    }
}

// Database testing configuration
if (extension.testing.enabled.get()) {
    tasks.withType<Test>().configureEach {
        // Add testcontainers for database testing
        if (extension.testing.containers.enabled.get()) {
            systemProperty("database.test.container.image", extension.testing.containers.image.get())
            systemProperty("database.test.enabled", "true")
        }
        
        // Database connection properties for testing
        systemProperty("database.url", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
        systemProperty("database.user", "sa")
        systemProperty("database.password", "")
        systemProperty("database.pool.size", extension.pool.size.get().toString())
        systemProperty("database.pool.timeout", extension.pool.timeout.get().toString())
    }
    
    // Add Testcontainers dependency if testing is enabled
    dependencies.add("testImplementation", libs.testcontainers.core)
    dependencies.add("testImplementation", libs.testcontainers.postgresql)
}

// Add core database dependencies
dependencies.add("implementation", libs.hikaricp)
dependencies.add("implementation", libs.postgresql)

// Flyway dependencies
if (extension.migrations.enabled.get()) {
    dependencies.add("implementation", libs.flyway.core)
    dependencies.add("implementation", libs.flyway.database.postgresql)
}
