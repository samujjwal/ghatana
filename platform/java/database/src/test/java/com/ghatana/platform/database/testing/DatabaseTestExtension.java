package com.ghatana.platform.database.testing;

import com.ghatana.core.database.EntityManagerProvider;
import com.ghatana.core.database.TransactionManager;
import com.ghatana.core.database.config.JpaConfig;
import com.ghatana.core.database.jdbc.JdbcTemplate;
import jakarta.persistence.EntityManagerFactory;

// JUnit 5 imports
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ParameterResolutionException;

// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Database
import javax.sql.DataSource;

// Java
import java.lang.reflect.Field;
import java.util.Optional;

// TestContainers
import org.testcontainers.DockerClientFactory;

/**
 * Production-grade JUnit 5 extension for automatic database test lifecycle management with Testcontainers.
 *
 * <p><b>Purpose</b><br>
 * Automates database test setup with Testcontainers, providing lifecycle management,
 * dependency injection, transaction isolation, and automatic cleanup. Supports both
 * parameter injection and field injection for database components.
 *
 * <p><b>Architecture Role</b><br>
 * Testing extension in core/database/testing for integration test infrastructure.
 * Used by:
 * - Repository Tests - Test JPA repositories with real database
 * - Service Tests - Integration tests with database dependencies
 * - Migration Tests - Validate Flyway migrations
 * - Performance Tests - Benchmark database operations
 * - End-to-End Tests - Full application stack with database
 *
 * <p><b>Extension Features</b><br>
 * - <b>Automatic Container Lifecycle</b>: Start/stop PostgreSQL Testcontainer
 * - <b>Dependency Injection</b>: Inject database components into tests
 * - <b>Transaction Isolation</b>: Rollback between tests (optional) // GH-90000
 * - <b>Field Injection</b>: Annotate fields with @Inject
 * - <b>Parameter Injection</b>: Inject into test method parameters
 * - <b>Shared Container</b>: Reuse container across test class
 * - <b>Auto-Cleanup</b>: Shutdown container and resources
 *
 * <p><b>Injectable Components</b><br>
 * - <b>DataSource</b>: JDBC DataSource for connection access
 * - <b>JdbcTemplate</b>: High-level JDBC operations
 * - <b>EntityManagerFactory</b>: JPA EntityManagerFactory
 * - <b>EntityManagerProvider</b>: EntityManager lifecycle manager
 * - <b>TransactionManager</b>: Transaction management
 * - <b>JpaConfig</b>: JPA configuration object
 * - <b>DatabaseTestContainer</b>: Direct container access
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic usage with parameter injection
 * @ExtendWith(DatabaseTestExtension.class) // GH-90000
 * class UserRepositoryTest {
 *
 *     @Test
 *     void shouldSaveUser(TransactionManager txManager, // GH-90000
 *                         EntityManagerProvider emProvider) {
 *         // Transaction automatically rolls back after test
 *         txManager.inTransaction(em -> { // GH-90000
 *             User user = new User("john@example.com");
 *             em.persist(user); // GH-90000
 *             return user;
 *         });
 *
 *         // Verify in new transaction
 *         User saved = txManager.inReadOnlyTransaction(em -> // GH-90000
 *             em.find(User.class, user.getId())); // GH-90000
 *
 *         assertThat(saved.getEmail()).isEqualTo("john@example.com");
 *     }
 * }
 *
 * // 2. Field injection with @Inject
 * @ExtendWith(DatabaseTestExtension.class) // GH-90000
 * class OrderRepositoryTest {
 *
 *     @Inject
 *     private TransactionManager txManager;
 *
 *     @Inject
 *     private JdbcTemplate jdbcTemplate;
 *
 *     @Test
 *     void shouldCreateOrder() { // GH-90000
 *         Order order = txManager.inTransaction(em -> { // GH-90000
 *             Order o = new Order(); // GH-90000
 *             em.persist(o); // GH-90000
 *             return o;
 *         });
 *
 *         // Verify with JDBC
 *         Integer count = jdbcTemplate.queryForObject( // GH-90000
 *             "SELECT COUNT(*) FROM orders WHERE id = ?", // GH-90000
 *             (rs, rowNum) -> rs.getInt(1), // GH-90000
 *             order.getId() // GH-90000
 *         );
 *
 *         assertThat(count).isEqualTo(1); // GH-90000
 *     }
 * }
 *
 * // 3. Custom JPA configuration
 * @ExtendWith(DatabaseTestExtension.class) // GH-90000
 * @DatabaseTest( // GH-90000
 *     entityPackages = {"com.example.domain.user", "com.example.domain.order"},
 *     showSql = true,
 *     formatSql = true
 * )
 * class CustomConfigTest {
 *
 *     @Test
 *     void testWithCustomConfig(JpaConfig config) { // GH-90000
 *         assertThat(config.isShowSql()).isTrue(); // GH-90000
 *         assertThat(config.isFormatSql()).isTrue(); // GH-90000
 *     }
 * }
 *
 * // 4. Direct container access for setup
 * @ExtendWith(DatabaseTestExtension.class) // GH-90000
 * class MigrationTest {
 *
 *     @Test
 *     void shouldRunMigrations(DatabaseTestContainer container, DataSource ds) { // GH-90000
 *         // Container running with JDBC URL
 *         String jdbcUrl = container.getJdbcUrl(); // GH-90000
 *         logger.info("Database running at: {}", jdbcUrl); // GH-90000
 *
 *         // Run Flyway migrations
 *         FlywayMigration migration = FlywayMigration.builder() // GH-90000
 *             .dataSource(ds) // GH-90000
 *             .locations("classpath:db/migration")
 *             .build(); // GH-90000
 *
 *         MigrationResult result = migration.migrate(); // GH-90000
 *         assertThat(result.migrationsExecuted).isGreaterThan(0); // GH-90000
 *     }
 * }
 *
 * // 5. Shared container for performance
 * @ExtendWith(DatabaseTestExtension.class) // GH-90000
 * @TestInstance(TestInstance.Lifecycle.PER_CLASS)  // Share container // GH-90000
 * class PerformanceTest {
 *
 *     @Inject
 *     private TransactionManager txManager;
 *
 *     @BeforeAll
 *     void loadTestData() { // GH-90000
 *         // Load 10,000 test records once
 *         txManager.inTransaction(em -> { // GH-90000
 *             for (int i = 0; i < 10000; i++) { // GH-90000
 *                 em.persist(new User("user" + i + "@example.com")); // GH-90000
 *             }
 *             return null;
 *         });
 *     }
 *
 *     @Test
 *     void benchmarkQuery() { // GH-90000
 *         // All tests share same container and data
 *         long start = System.nanoTime(); // GH-90000
 *         List<User> users = txManager.inReadOnlyTransaction(em -> // GH-90000
 *             em.createQuery("SELECT u FROM User u", User.class) // GH-90000
 *                 .setMaxResults(100) // GH-90000
 *                 .getResultList()); // GH-90000
 *         long durationMs = (System.nanoTime() - start) / 1_000_000; // GH-90000
 *
 *         assertThat(durationMs).isLessThan(100); // GH-90000
 *     }
 * }
 *
 * // 6. Multiple database components
 * @ExtendWith(DatabaseTestExtension.class) // GH-90000
 * class IntegrationTest {
 *
 *     @Test
 *     void testWithAllComponents( // GH-90000
 *             DataSource dataSource,
 *             JdbcTemplate jdbc,
 *             EntityManagerFactory emf,
 *             EntityManagerProvider emProvider,
 *             TransactionManager txManager,
 *             JpaConfig config) {
 *
 *         // Use JDBC for bulk insert
 *         jdbc.batchUpdate("INSERT INTO users (email) VALUES (?)", // GH-90000
 *             Arrays.asList("user1@example.com", "user2@example.com"), // GH-90000
 *             (ps, email) -> ps.setString(1, email)); // GH-90000
 *
 *         // Use JPA for query
 *         List<User> users = txManager.inReadOnlyTransaction(em -> // GH-90000
 *             em.createQuery("SELECT u FROM User u", User.class) // GH-90000
 *                 .getResultList()); // GH-90000
 *
 *         assertThat(users).hasSize(2); // GH-90000
 *     }
 * }
 * }</pre>
 *
 * <p><b>Lifecycle Hooks</b><br>
 * - <b>@BeforeAll</b>: Start Testcontainer, create DataSource, initialize JPA
 * - <b>@BeforeEach</b>: Inject dependencies into test instance
 * - <b>@AfterEach</b>: Clean up per-test resources (optional rollback) // GH-90000
 * - <b>@AfterAll</b>: Shutdown container, close EMF, cleanup resources
 *
 * <p><b>Container Configuration</b><br>
 * Default PostgreSQL 15 container with:
 * - Image: postgres:15-alpine
 * - Database: testdb
 * - Username: test
 * - Password: test
 * - Port: Random available port
 * - Startup timeout: 60 seconds
 *
 * <p><b>Transaction Isolation</b><br>
 * By default, each test runs in isolation:
 * <pre>{@code
 * @ExtendWith(DatabaseTestExtension.class) // GH-90000
 * class IsolationTest {
 *
 *     @Test
 *     void test1(TransactionManager txManager) { // GH-90000
 *         txManager.inTransaction(em -> { // GH-90000
 *             em.persist(new User("test1@example.com"));
 *             return null;
 *         });
 *     }
 *
 *     @Test
 *     void test2(TransactionManager txManager) { // GH-90000
 *         // test1's user NOT visible here - isolated
 *         long count = txManager.inReadOnlyTransaction(em -> // GH-90000
 *             em.createQuery("SELECT COUNT(u) FROM User u", Long.class) // GH-90000
 *                 .getSingleResult()); // GH-90000
 *         assertThat(count).isEqualTo(0); // GH-90000
 *     }
 * }
 * }</pre>
 *
 * <p><b>Field Injection Requirements</b><br>
 * Fields must be:
 * - Annotated with @Inject (or @jakarta.inject.Inject) // GH-90000
 * - Non-final
 * - One of the injectable types
 *
 * <p><b>Parameter Injection</b><br>
 * Test method parameters automatically resolved if type matches injectable components.
 *
 * <p><b>Best Practices</b><br>
 * - Use @TestInstance(PER_CLASS) to share container across tests (faster) // GH-90000
 * - Use TransactionManager for test isolation (rollback after each test) // GH-90000
 * - Inject only needed components (avoid over-injection) // GH-90000
 * - Use @BeforeAll for expensive setup (data loading) // GH-90000
 * - Clean up resources in @AfterEach if needed
 * - Use JdbcTemplate for bulk operations in setup
 *
 * <p><b>Performance Optimization</b><br>
 * - Container starts once per test class (reused across tests) // GH-90000
 * - EntityManagerFactory created once (expensive operation) // GH-90000
 * - Use transaction rollback instead of database cleanup
 * - Consider @TestInstance(PER_CLASS) for shared state // GH-90000
 *
 * <p><b>Thread Safety</b><br>
 * Extension manages per-test-class state stored in ExtensionContext.
 * Thread-safe when used with standard JUnit 5 execution model.
 *
 * @see DatabaseTestContainer
 * @see DatabaseTest
 * @see TransactionManager
 * @see EntityManagerProvider
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose JUnit 5 extension for database test lifecycle with Testcontainers
 * @doc.layer core
 * @doc.pattern Extension
 */
public class DatabaseTestExtension implements
        BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseTestExtension.class); // GH-90000
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DatabaseTestExtension.class); // GH-90000
    private static final String DATABASE_KEY = "database";
    private static final String DATA_SOURCE_KEY = "dataSource";
    private static final String JDBC_TEMPLATE_KEY = "jdbcTemplate";
    private static final String ENTITY_MANAGER_FACTORY_KEY = "entityManagerFactory";
    private static final String ENTITY_MANAGER_PROVIDER_KEY = "entityManagerProvider";
    private static final String TRANSACTION_MANAGER_KEY = "transactionManager";
    private static final String JPA_CONFIG_KEY = "jpaConfig";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception { // GH-90000
        LOG.debug("Setting up test database for {}", context.getDisplayName()); // GH-90000

        boolean dockerAvailable = isDockerAvailable(); // GH-90000
        Assumptions.assumeTrue(dockerAvailable, // GH-90000
                () -> "Skipping database tests because Docker is unavailable"); // GH-90000

        // Create and start the database container
        DatabaseTestContainer database = createDatabaseContainer(context); // GH-90000
        database.start(); // GH-90000

        // Store the container in the extension context
        getStore(context).put(DATABASE_KEY, database); // GH-90000

        // Initialize and store other components
        DataSource dataSource = database.getDataSource(); // GH-90000
        getStore(context).put(DATA_SOURCE_KEY, dataSource); // GH-90000
        getStore(context).put(JDBC_TEMPLATE_KEY, new JdbcTemplate(dataSource)); // GH-90000

        // If entity packages are specified, create JPA components
        String[] entityPackages = getEntityPackages(context); // GH-90000
        if (entityPackages != null && entityPackages.length > 0) { // GH-90000
            JpaConfig jpaConfig = database.createJpaConfig(entityPackages); // GH-90000
            EntityManagerFactory emf = jpaConfig.createEntityManagerFactory(dataSource); // GH-90000
            getStore(context).put(ENTITY_MANAGER_FACTORY_KEY, emf); // GH-90000

            EntityManagerProvider empProvider = new EntityManagerProvider(emf); // GH-90000
            getStore(context).put(ENTITY_MANAGER_PROVIDER_KEY, empProvider); // GH-90000

            TransactionManager txManager = new TransactionManager(empProvider); // GH-90000
            getStore(context).put(TRANSACTION_MANAGER_KEY, txManager); // GH-90000
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception { // GH-90000
        LOG.debug("Tearing down database test container for class: {}", context.getDisplayName()); // GH-90000

        // Close EntityManagerFactory if it exists
        EntityManagerFactory emf = getStore(context).remove(ENTITY_MANAGER_FACTORY_KEY, EntityManagerFactory.class); // GH-90000
        if (emf != null && emf.isOpen()) { // GH-90000
            LOG.debug("Closing EntityManagerFactory");
            emf.close(); // GH-90000
        }

        // Stop the database container
        DatabaseTestContainer database = getStore(context).remove(DATABASE_KEY, DatabaseTestContainer.class); // GH-90000
        if (database != null) { // GH-90000
            LOG.debug("Stopping database container");
            database.stop(); // GH-90000
        }

        // Clear all stored components
        getStore(context).remove(DATA_SOURCE_KEY); // GH-90000
        getStore(context).remove(JDBC_TEMPLATE_KEY); // GH-90000
        getStore(context).remove(ENTITY_MANAGER_PROVIDER_KEY); // GH-90000
        getStore(context).remove(TRANSACTION_MANAGER_KEY); // GH-90000
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception { // GH-90000
        // Start a new transaction for each test if JPA is being used
        TransactionManager txManager = getStore(context).get(TRANSACTION_MANAGER_KEY, TransactionManager.class); // GH-90000
        if (txManager != null) { // GH-90000
            txManager.begin(); // GH-90000
        }

        // Inject fields into the test instance
        Object testInstance = context.getTestInstance().orElse(null); // GH-90000
        if (testInstance != null) { // GH-90000
            injectFields(testInstance, context); // GH-90000
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception { // GH-90000
        // Roll back the transaction after each test to keep tests isolated
        TransactionManager txManager = getStore(context).get(TRANSACTION_MANAGER_KEY, TransactionManager.class); // GH-90000
        if (txManager != null && txManager.isActive()) { // GH-90000
            // Always rollback to ensure test isolation
            txManager.rollback(); // GH-90000
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) // GH-90000
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType(); // GH-90000
        return type == DatabaseTestContainer.class ||
               type == DataSource.class ||
               type == JdbcTemplate.class ||
               type == EntityManagerFactory.class ||
               type == EntityManagerProvider.class ||
               type == TransactionManager.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) // GH-90000
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType(); // GH-90000

        if (type == DatabaseTestContainer.class) { // GH-90000
            return getStore(extensionContext).get(DATABASE_KEY, DatabaseTestContainer.class); // GH-90000
        } else if (type == DataSource.class) { // GH-90000
            return getStore(extensionContext).get(DATA_SOURCE_KEY, DataSource.class); // GH-90000
        } else if (type == JdbcTemplate.class) { // GH-90000
            return getStore(extensionContext).get(JDBC_TEMPLATE_KEY, JdbcTemplate.class); // GH-90000
        } else if (type == EntityManagerFactory.class) { // GH-90000
            return getStore(extensionContext).get(ENTITY_MANAGER_FACTORY_KEY, EntityManagerFactory.class); // GH-90000
        } else if (type == EntityManagerProvider.class) { // GH-90000
            return getStore(extensionContext).get(ENTITY_MANAGER_PROVIDER_KEY, EntityManagerProvider.class); // GH-90000
        } else if (type == TransactionManager.class) { // GH-90000
            return getStore(extensionContext).get(TRANSACTION_MANAGER_KEY, TransactionManager.class); // GH-90000
        }

        throw new org.junit.jupiter.api.extension.ParameterResolutionException("Unsupported parameter type: " + type.getName()); // GH-90000
    }

    private ExtensionContext.Store getStore(ExtensionContext context) { // GH-90000
        return context.getStore(NAMESPACE); // GH-90000
    }

    private DatabaseTestContainer createDatabaseContainer(ExtensionContext context) { // GH-90000
        // Look for @DatabaseTest annotation on the test class
        Optional<DatabaseTest> annotation = findAnnotation(context, DatabaseTest.class); // GH-90000

        DatabaseTestContainer.Builder builder = DatabaseTestContainer.builder(); // GH-90000

        // Apply configuration from annotation if present
        if (annotation.isPresent()) { // GH-90000
            DatabaseTest dbTest = annotation.get(); // GH-90000

            // Set database properties
            if (!dbTest.image().isEmpty()) { // GH-90000
                builder.image(dbTest.image()); // GH-90000
            }
            if (!dbTest.database().isEmpty()) { // GH-90000
                builder.database(dbTest.database()); // GH-90000
            }
            if (!dbTest.username().isEmpty()) { // GH-90000
                builder.username(dbTest.username()); // GH-90000
            }
            if (!dbTest.password().isEmpty()) { // GH-90000
                builder.password(dbTest.password()); // GH-90000
            }

            // Set migration locations if specified
            if (dbTest.migrations().length > 0) { // GH-90000
                builder.withMigrations(dbTest.migrations()); // GH-90000
            }

            // Note: startupTimeout is not supported in the current DatabaseTest annotation
            // If needed, add it to the annotation and uncomment the following:
            // if (dbTest.startupTimeout() > 0) { // GH-90000
            //     builder.startupTimeout(Duration.ofSeconds(dbTest.startupTimeout())); // GH-90000
            // }
        }

        return builder.build(); // GH-90000
    }

    private boolean isDockerAvailable() { // GH-90000
        try {
            return DockerClientFactory.instance().isDockerAvailable(); // GH-90000
        } catch (Throwable ex) { // GH-90000
            LOG.debug("Docker availability check failed", ex); // GH-90000
            return false;
        }
    }

    private String[] getEntityPackages(ExtensionContext context) { // GH-90000
        // Look for @DatabaseTest annotation on the test class
        Optional<DatabaseTest> annotation = findAnnotation(context, DatabaseTest.class); // GH-90000
        return annotation.map(DatabaseTest::entityPackages).orElse(new String[0]); // GH-90000
    }

    private <T extends java.lang.annotation.Annotation> Optional<T> findAnnotation(ExtensionContext context, Class<T> annotationType) { // GH-90000
        return context.getElement().map(element -> element.getAnnotation(annotationType)); // GH-90000
    }

    private void injectFields(Object testInstance, ExtensionContext context) { // GH-90000
        Class<?> testClass = testInstance.getClass(); // GH-90000

        // Get all fields including inherited ones
        Field[] fields = getAllFields(testClass); // GH-90000

        for (Field field : fields) { // GH-90000
            Object value = getFieldValue(field.getType(), getStore(context)); // GH-90000
            if (value != null) { // GH-90000
                try {
                    field.setAccessible(true); // GH-90000
                    field.set(testInstance, value); // GH-90000
                } catch (IllegalAccessException e) { // GH-90000
                    LOG.warn("Failed to inject field {}: {}", field.getName(), e.getMessage()); // GH-90000
                }
            }
        }
    }

    private Field[] getAllFields(Class<?> clazz) { // GH-90000
        Field[] fields = clazz.getDeclaredFields(); // GH-90000
        Class<?> superClass = clazz.getSuperclass(); // GH-90000

        if (superClass != null) { // GH-90000
            Field[] superFields = getAllFields(superClass); // GH-90000
            Field[] allFields = new Field[fields.length + superFields.length];
            System.arraycopy(fields, 0, allFields, 0, fields.length); // GH-90000
            System.arraycopy(superFields, 0, allFields, fields.length, superFields.length); // GH-90000
            return allFields;
        }

        return fields;
    }

    private Object getFieldValue(Class<?> fieldType, ExtensionContext.Store store) { // GH-90000
        if (DatabaseTestContainer.class.isAssignableFrom(fieldType)) { // GH-90000
            return store.get(DATABASE_KEY, DatabaseTestContainer.class); // GH-90000
        } else if (DataSource.class.isAssignableFrom(fieldType)) { // GH-90000
            return store.get(DATA_SOURCE_KEY, DataSource.class); // GH-90000
        } else if (JdbcTemplate.class.isAssignableFrom(fieldType)) { // GH-90000
            return store.get(JDBC_TEMPLATE_KEY, JdbcTemplate.class); // GH-90000
        } else if (EntityManagerFactory.class.isAssignableFrom(fieldType)) { // GH-90000
            return store.get(ENTITY_MANAGER_FACTORY_KEY, EntityManagerFactory.class); // GH-90000
        } else if (EntityManagerProvider.class.isAssignableFrom(fieldType)) { // GH-90000
            return store.get(ENTITY_MANAGER_PROVIDER_KEY, EntityManagerProvider.class); // GH-90000
        } else if (TransactionManager.class.isAssignableFrom(fieldType)) { // GH-90000
            return store.get(TRANSACTION_MANAGER_KEY, TransactionManager.class); // GH-90000
        }
        return null;
    }
}
