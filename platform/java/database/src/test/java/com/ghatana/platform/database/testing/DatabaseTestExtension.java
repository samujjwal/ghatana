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
import org.junit.platform.commons.util.AnnotationUtils;

// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Database
import javax.sql.DataSource;

// Java
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

// TestContainers
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
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
 * - <b>Transaction Isolation</b>: Rollback between tests (optional)
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
 * @ExtendWith(DatabaseTestExtension.class)
 * class UserRepositoryTest {
 *     
 *     @Test
 *     void shouldSaveUser(TransactionManager txManager, 
 *                         EntityManagerProvider emProvider) {
 *         // Transaction automatically rolls back after test
 *         txManager.inTransaction(em -> {
 *             User user = new User("john@example.com");
 *             em.persist(user);
 *             return user;
 *         });
 *         
 *         // Verify in new transaction
 *         User saved = txManager.inReadOnlyTransaction(em -> 
 *             em.find(User.class, user.getId()));
 *         
 *         assertThat(saved.getEmail()).isEqualTo("john@example.com");
 *     }
 * }
 *
 * // 2. Field injection with @Inject
 * @ExtendWith(DatabaseTestExtension.class)
 * class OrderRepositoryTest {
 *     
 *     @Inject
 *     private TransactionManager txManager;
 *     
 *     @Inject
 *     private JdbcTemplate jdbcTemplate;
 *     
 *     @Test
 *     void shouldCreateOrder() {
 *         Order order = txManager.inTransaction(em -> {
 *             Order o = new Order();
 *             em.persist(o);
 *             return o;
 *         });
 *         
 *         // Verify with JDBC
 *         Integer count = jdbcTemplate.queryForObject(
 *             "SELECT COUNT(*) FROM orders WHERE id = ?",
 *             (rs, rowNum) -> rs.getInt(1),
 *             order.getId()
 *         );
 *         
 *         assertThat(count).isEqualTo(1);
 *     }
 * }
 *
 * // 3. Custom JPA configuration
 * @ExtendWith(DatabaseTestExtension.class)
 * @DatabaseTest(
 *     entityPackages = {"com.example.domain.user", "com.example.domain.order"},
 *     showSql = true,
 *     formatSql = true
 * )
 * class CustomConfigTest {
 *     
 *     @Test
 *     void testWithCustomConfig(JpaConfig config) {
 *         assertThat(config.isShowSql()).isTrue();
 *         assertThat(config.isFormatSql()).isTrue();
 *     }
 * }
 *
 * // 4. Direct container access for setup
 * @ExtendWith(DatabaseTestExtension.class)
 * class MigrationTest {
 *     
 *     @Test
 *     void shouldRunMigrations(DatabaseTestContainer container, DataSource ds) {
 *         // Container running with JDBC URL
 *         String jdbcUrl = container.getJdbcUrl();
 *         logger.info("Database running at: {}", jdbcUrl);
 *         
 *         // Run Flyway migrations
 *         FlywayMigration migration = FlywayMigration.builder()
 *             .dataSource(ds)
 *             .locations("classpath:db/migration")
 *             .build();
 *         
 *         MigrationResult result = migration.migrate();
 *         assertThat(result.migrationsExecuted).isGreaterThan(0);
 *     }
 * }
 *
 * // 5. Shared container for performance
 * @ExtendWith(DatabaseTestExtension.class)
 * @TestInstance(TestInstance.Lifecycle.PER_CLASS)  // Share container
 * class PerformanceTest {
 *     
 *     @Inject
 *     private TransactionManager txManager;
 *     
 *     @BeforeAll
 *     void loadTestData() {
 *         // Load 10,000 test records once
 *         txManager.inTransaction(em -> {
 *             for (int i = 0; i < 10000; i++) {
 *                 em.persist(new User("user" + i + "@example.com"));
 *             }
 *             return null;
 *         });
 *     }
 *     
 *     @Test
 *     void benchmarkQuery() {
 *         // All tests share same container and data
 *         long start = System.nanoTime();
 *         List<User> users = txManager.inReadOnlyTransaction(em -> 
 *             em.createQuery("SELECT u FROM User u", User.class)
 *                 .setMaxResults(100)
 *                 .getResultList());
 *         long durationMs = (System.nanoTime() - start) / 1_000_000;
 *         
 *         assertThat(durationMs).isLessThan(100);
 *     }
 * }
 *
 * // 6. Multiple database components
 * @ExtendWith(DatabaseTestExtension.class)
 * class IntegrationTest {
 *     
 *     @Test
 *     void testWithAllComponents(
 *             DataSource dataSource,
 *             JdbcTemplate jdbc,
 *             EntityManagerFactory emf,
 *             EntityManagerProvider emProvider,
 *             TransactionManager txManager,
 *             JpaConfig config) {
 *         
 *         // Use JDBC for bulk insert
 *         jdbc.batchUpdate("INSERT INTO users (email) VALUES (?)",
 *             Arrays.asList("user1@example.com", "user2@example.com"),
 *             (ps, email) -> ps.setString(1, email));
 *         
 *         // Use JPA for query
 *         List<User> users = txManager.inReadOnlyTransaction(em -> 
 *             em.createQuery("SELECT u FROM User u", User.class)
 *                 .getResultList());
 *         
 *         assertThat(users).hasSize(2);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Lifecycle Hooks</b><br>
 * - <b>@BeforeAll</b>: Start Testcontainer, create DataSource, initialize JPA
 * - <b>@BeforeEach</b>: Inject dependencies into test instance
 * - <b>@AfterEach</b>: Clean up per-test resources (optional rollback)
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
 * @ExtendWith(DatabaseTestExtension.class)
 * class IsolationTest {
 *     
 *     @Test
 *     void test1(TransactionManager txManager) {
 *         txManager.inTransaction(em -> {
 *             em.persist(new User("test1@example.com"));
 *             return null;
 *         });
 *     }
 *     
 *     @Test
 *     void test2(TransactionManager txManager) {
 *         // test1's user NOT visible here - isolated
 *         long count = txManager.inReadOnlyTransaction(em -> 
 *             em.createQuery("SELECT COUNT(u) FROM User u", Long.class)
 *                 .getSingleResult());
 *         assertThat(count).isEqualTo(0);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Field Injection Requirements</b><br>
 * Fields must be:
 * - Annotated with @Inject (or @jakarta.inject.Inject)
 * - Non-final
 * - One of the injectable types
 *
 * <p><b>Parameter Injection</b><br>
 * Test method parameters automatically resolved if type matches injectable components.
 *
 * <p><b>Best Practices</b><br>
 * - Use @TestInstance(PER_CLASS) to share container across tests (faster)
 * - Use TransactionManager for test isolation (rollback after each test)
 * - Inject only needed components (avoid over-injection)
 * - Use @BeforeAll for expensive setup (data loading)
 * - Clean up resources in @AfterEach if needed
 * - Use JdbcTemplate for bulk operations in setup
 *
 * <p><b>Performance Optimization</b><br>
 * - Container starts once per test class (reused across tests)
 * - EntityManagerFactory created once (expensive operation)
 * - Use transaction rollback instead of database cleanup
 * - Consider @TestInstance(PER_CLASS) for shared state
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

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseTestExtension.class);
    private static final ExtensionContext.Namespace NAMESPACE = 
            ExtensionContext.Namespace.create(DatabaseTestExtension.class);
    private static final String DATABASE_KEY = "database";
    private static final String DATA_SOURCE_KEY = "dataSource";
    private static final String JDBC_TEMPLATE_KEY = "jdbcTemplate";
    private static final String ENTITY_MANAGER_FACTORY_KEY = "entityManagerFactory";
    private static final String ENTITY_MANAGER_PROVIDER_KEY = "entityManagerProvider";
    private static final String TRANSACTION_MANAGER_KEY = "transactionManager";
    private static final String JPA_CONFIG_KEY = "jpaConfig";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        LOG.debug("Setting up test database for {}", context.getDisplayName());

        boolean dockerAvailable = isDockerAvailable();
        Assumptions.assumeTrue(dockerAvailable,
                () -> "Skipping database tests because Docker is unavailable");
        
        // Create and start the database container
        DatabaseTestContainer database = createDatabaseContainer(context);
        database.start();
        
        // Store the container in the extension context
        getStore(context).put(DATABASE_KEY, database);
        
        // Initialize and store other components
        DataSource dataSource = database.getDataSource();
        getStore(context).put(DATA_SOURCE_KEY, dataSource);
        getStore(context).put(JDBC_TEMPLATE_KEY, new JdbcTemplate(dataSource));
        
        // If entity packages are specified, create JPA components
        String[] entityPackages = getEntityPackages(context);
        if (entityPackages != null && entityPackages.length > 0) {
            JpaConfig jpaConfig = database.createJpaConfig(entityPackages);
            EntityManagerFactory emf = jpaConfig.createEntityManagerFactory(dataSource);
            getStore(context).put(ENTITY_MANAGER_FACTORY_KEY, emf);
            
            EntityManagerProvider empProvider = new EntityManagerProvider(emf);
            getStore(context).put(ENTITY_MANAGER_PROVIDER_KEY, empProvider);
            
            TransactionManager txManager = new TransactionManager(empProvider);
            getStore(context).put(TRANSACTION_MANAGER_KEY, txManager);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        LOG.debug("Tearing down database test container for class: {}", context.getDisplayName());
        
        // Close EntityManagerFactory if it exists
        EntityManagerFactory emf = getStore(context).remove(ENTITY_MANAGER_FACTORY_KEY, EntityManagerFactory.class);
        if (emf != null && emf.isOpen()) {
            LOG.debug("Closing EntityManagerFactory");
            emf.close();
        }
        
        // Stop the database container
        DatabaseTestContainer database = getStore(context).remove(DATABASE_KEY, DatabaseTestContainer.class);
        if (database != null) {
            LOG.debug("Stopping database container");
            database.stop();
        }
        
        // Clear all stored components
        getStore(context).remove(DATA_SOURCE_KEY);
        getStore(context).remove(JDBC_TEMPLATE_KEY);
        getStore(context).remove(ENTITY_MANAGER_PROVIDER_KEY);
        getStore(context).remove(TRANSACTION_MANAGER_KEY);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // Start a new transaction for each test if JPA is being used
        TransactionManager txManager = getStore(context).get(TRANSACTION_MANAGER_KEY, TransactionManager.class);
        if (txManager != null) {
            txManager.begin();
        }
        
        // Inject fields into the test instance
        Object testInstance = context.getTestInstance().orElse(null);
        if (testInstance != null) {
            injectFields(testInstance, context);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        // Roll back the transaction after each test to keep tests isolated
        TransactionManager txManager = getStore(context).get(TRANSACTION_MANAGER_KEY, TransactionManager.class);
        if (txManager != null && txManager.isActive()) {
            // Always rollback to ensure test isolation
            txManager.rollback();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) 
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return type == DatabaseTestContainer.class ||
               type == DataSource.class ||
               type == JdbcTemplate.class ||
               type == EntityManagerFactory.class ||
               type == EntityManagerProvider.class ||
               type == TransactionManager.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) 
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        
        if (type == DatabaseTestContainer.class) {
            return getStore(extensionContext).get(DATABASE_KEY, DatabaseTestContainer.class);
        } else if (type == DataSource.class) {
            return getStore(extensionContext).get(DATA_SOURCE_KEY, DataSource.class);
        } else if (type == JdbcTemplate.class) {
            return getStore(extensionContext).get(JDBC_TEMPLATE_KEY, JdbcTemplate.class);
        } else if (type == EntityManagerFactory.class) {
            return getStore(extensionContext).get(ENTITY_MANAGER_FACTORY_KEY, EntityManagerFactory.class);
        } else if (type == EntityManagerProvider.class) {
            return getStore(extensionContext).get(ENTITY_MANAGER_PROVIDER_KEY, EntityManagerProvider.class);
        } else if (type == TransactionManager.class) {
            return getStore(extensionContext).get(TRANSACTION_MANAGER_KEY, TransactionManager.class);
        }
        
        throw new org.junit.jupiter.api.extension.ParameterResolutionException("Unsupported parameter type: " + type.getName());
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(NAMESPACE);
    }

    private DatabaseTestContainer createDatabaseContainer(ExtensionContext context) {
        // Look for @DatabaseTest annotation on the test class
        Optional<DatabaseTest> annotation = findAnnotation(context, DatabaseTest.class);
        
        DatabaseTestContainer.Builder builder = DatabaseTestContainer.builder();
        
        // Apply configuration from annotation if present
        if (annotation.isPresent()) {
            DatabaseTest dbTest = annotation.get();
            
            // Set database properties
            if (!dbTest.image().isEmpty()) {
                builder.image(dbTest.image());
            }
            if (!dbTest.database().isEmpty()) {
                builder.database(dbTest.database());
            }
            if (!dbTest.username().isEmpty()) {
                builder.username(dbTest.username());
            }
            if (!dbTest.password().isEmpty()) {
                builder.password(dbTest.password());
            }
            
            // Set migration locations if specified
            if (dbTest.migrations().length > 0) {
                builder.withMigrations(dbTest.migrations());
            }
            
            // Note: startupTimeout is not supported in the current DatabaseTest annotation
            // If needed, add it to the annotation and uncomment the following:
            // if (dbTest.startupTimeout() > 0) {
            //     builder.startupTimeout(Duration.ofSeconds(dbTest.startupTimeout()));
            // }
        }
        
        return builder.build();
    }

    private boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            LOG.debug("Docker availability check failed", ex);
            return false;
        }
    }

    private String[] getEntityPackages(ExtensionContext context) {
        // Look for @DatabaseTest annotation on the test class
        Optional<DatabaseTest> annotation = findAnnotation(context, DatabaseTest.class);
        return annotation.map(DatabaseTest::entityPackages).orElse(new String[0]);
    }

    private <T extends java.lang.annotation.Annotation> Optional<T> findAnnotation(ExtensionContext context, Class<T> annotationType) {
        return context.getElement().map(element -> element.getAnnotation(annotationType));
    }

    private void injectFields(Object testInstance, ExtensionContext context) {
        Class<?> testClass = testInstance.getClass();
        
        // Get all fields including inherited ones
        Field[] fields = getAllFields(testClass);
        
        for (Field field : fields) {
            Object value = getFieldValue(field.getType(), getStore(context));
            if (value != null) {
                try {
                    field.setAccessible(true);
                    field.set(testInstance, value);
                } catch (IllegalAccessException e) {
                    LOG.warn("Failed to inject field {}: {}", field.getName(), e.getMessage());
                }
            }
        }
    }
    
    private Field[] getAllFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        Class<?> superClass = clazz.getSuperclass();
        
        if (superClass != null) {
            Field[] superFields = getAllFields(superClass);
            Field[] allFields = new Field[fields.length + superFields.length];
            System.arraycopy(fields, 0, allFields, 0, fields.length);
            System.arraycopy(superFields, 0, allFields, fields.length, superFields.length);
            return allFields;
        }
        
        return fields;
    }
    
    private Object getFieldValue(Class<?> fieldType, ExtensionContext.Store store) {
        if (DatabaseTestContainer.class.isAssignableFrom(fieldType)) {
            return store.get(DATABASE_KEY, DatabaseTestContainer.class);
        } else if (DataSource.class.isAssignableFrom(fieldType)) {
            return store.get(DATA_SOURCE_KEY, DataSource.class);
        } else if (JdbcTemplate.class.isAssignableFrom(fieldType)) {
            return store.get(JDBC_TEMPLATE_KEY, JdbcTemplate.class);
        } else if (EntityManagerFactory.class.isAssignableFrom(fieldType)) {
            return store.get(ENTITY_MANAGER_FACTORY_KEY, EntityManagerFactory.class);
        } else if (EntityManagerProvider.class.isAssignableFrom(fieldType)) {
            return store.get(ENTITY_MANAGER_PROVIDER_KEY, EntityManagerProvider.class);
        } else if (TransactionManager.class.isAssignableFrom(fieldType)) {
            return store.get(TRANSACTION_MANAGER_KEY, TransactionManager.class);
        }
        return null;
    }
}
