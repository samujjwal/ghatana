package com.ghatana.platform.http.server.testing;

import io.activej.http.AsyncServlet;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.lang.reflect.Field;
import java.time.Duration;

/**
 * Production-grade JUnit 5 extension for HTTP server testing with automatic HttpServerTestRunner lifecycle and dependency injection.
 *
 * <p><b>Purpose</b><br>
 * Simplifies HTTP server testing by automatically managing HttpServerTestRunner lifecycle,
 * injecting runner into test methods/fields, and providing clean test isolation. Eliminates
 * boilerplate setup/teardown code in HTTP integration tests.
 *
 * <p><b>Architecture Role</b><br>
 * JUnit 5 extension in core/http/testing for automated HTTP test setup.
 * Used by:
 * - HTTP Integration Tests - Test servlets with automatic setup
 * - API Tests - Validate endpoints with minimal boilerplate
 * - Service Tests - Test HTTP services in isolation
 * - Regression Tests - Consistent test environment
 *
 * <p><b>Extension Features</b><br>
 * - <b>Automatic Lifecycle</b>: Start runner before each test, close after
 * - <b>Method Injection</b>: Inject HttpServerTestRunner as test method parameter
 * - <b>Field Injection</b>: Auto-inject runner into test instance fields
 * - <b>Servlet Discovery</b>: Find AsyncServlet field in test class
 * - <b>Timeout Configuration</b>: Custom timeout per extension instance
 * - <b>Isolation</b>: Fresh runner for each test method
 * - <b>JUnit 5 Integration</b>: BeforeEachCallback, AfterEachCallback, ParameterResolver
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Method parameter injection (recommended) // GH-90000
 * @ExtendWith(HttpServerTestExtension.class) // GH-90000
 * class UserServiceHttpTest {
 *
 *     private final AsyncServlet servlet = createUserServlet(); // GH-90000
 *
 *     @Test
 *     void shouldReturnUserById(HttpServerTestRunner runner) { // GH-90000
 *         HttpResponse response = runner.get("/api/users/123");
 *
 *         assertEquals(200, response.getCode()); // GH-90000
 *         assertEquals("application/json", response.getHeader("Content-Type"));
 *
 *         User user = parseJson(response, User.class); // GH-90000
 *         assertEquals("123", user.getId()); // GH-90000
 *     }
 *
 *     @Test
 *     void shouldCreateUser(HttpServerTestRunner runner) { // GH-90000
 *         String userJson = "{\"name\":\"John Doe\",\"email\":\"john@example.com\"}";
 *
 *         HttpResponse response = runner.post("/api/users", userJson); // GH-90000
 *
 *         assertEquals(201, response.getCode()); // GH-90000
 *         assertNotNull(response.getHeader("Location"));
 *     }
 * }
 *
 * // 2. Field injection (alternative) // GH-90000
 * @ExtendWith(HttpServerTestExtension.class) // GH-90000
 * class UserServiceHttpTest {
 *
 *     private final AsyncServlet servlet = createUserServlet(); // GH-90000
 *     private HttpServerTestRunner runner;  // Auto-injected by extension
 *
 *     @Test
 *     void shouldReturnUsers() { // GH-90000
 *         HttpResponse response = runner.get("/api/users");
 *
 *         assertEquals(200, response.getCode()); // GH-90000
 *
 *         User[] users = parseJson(response, User[].class); // GH-90000
 *         assertTrue(users.length > 0); // GH-90000
 *     }
 * }
 *
 * // 3. Custom timeout (30 seconds) // GH-90000
 * @ExtendWith(HttpServerTestExtension.class) // GH-90000
 * class SlowOperationTest {
 *
 *     @RegisterExtension
 *     static HttpServerTestExtension extension =
 *         new HttpServerTestExtension(Duration.ofSeconds(30)); // GH-90000
 *
 *     private final AsyncServlet servlet = createSlowServlet(); // GH-90000
 *
 *     @Test
 *     void shouldHandleSlowOperation(HttpServerTestRunner runner) { // GH-90000
 *         HttpResponse response = runner.get("/api/slow-operation");
 *         assertEquals(200, response.getCode()); // GH-90000
 *     }
 * }
 *
 * // 4. Multiple test methods with isolation
 * @ExtendWith(HttpServerTestExtension.class) // GH-90000
 * class ProductServiceHttpTest {
 *
 *     private final AsyncServlet servlet = createProductServlet(); // GH-90000
 *
 *     @Test
 *     void shouldListProducts(HttpServerTestRunner runner) { // GH-90000
 *         HttpResponse response = runner.get("/api/products");
 *         assertEquals(200, response.getCode()); // GH-90000
 *     }
 *
 *     @Test
 *     void shouldGetProductById(HttpServerTestRunner runner) { // GH-90000
 *         HttpResponse response = runner.get("/api/products/123");
 *         assertEquals(200, response.getCode()); // GH-90000
 *     }
 *
 *     @Test
 *     void shouldReturn404ForMissingProduct(HttpServerTestRunner runner) { // GH-90000
 *         HttpResponse response = runner.get("/api/products/999");
 *         assertEquals(404, response.getCode()); // GH-90000
 *     }
 * }
 *
 * // 5. Integration with other extensions
 * @ExtendWith({HttpServerTestExtension.class, DatabaseTestExtension.class}) // GH-90000
 * class FullStackTest {
 *
 *     private final AsyncServlet servlet = createServletWithDatabase(); // GH-90000
 *
 *     @Test
 *     void shouldPersistUserToDatabaseViaHttp(HttpServerTestRunner runner) { // GH-90000
 *         // Create user via HTTP
 *         HttpResponse response = runner.post("/api/users", // GH-90000
 *             "{\"name\":\"Test User\"}");
 *         assertEquals(201, response.getCode()); // GH-90000
 *
 *         // Verify in database
 *         User user = userRepository.findByName("Test User");
 *         assertNotNull(user); // GH-90000
 *     }
 * }
 *
 * // 6. Shared servlet setup with @BeforeEach
 * @ExtendWith(HttpServerTestExtension.class) // GH-90000
 * class OrderServiceHttpTest {
 *
 *     private AsyncServlet servlet;
 *
 *     @BeforeEach
 *     void setUp() { // GH-90000
 *         OrderService orderService = new OrderService(); // GH-90000
 *         servlet = createServlet(orderService); // GH-90000
 *     }
 *
 *     @Test
 *     void shouldCreateOrder(HttpServerTestRunner runner) { // GH-90000
 *         HttpResponse response = runner.post("/api/orders", // GH-90000
 *             "{\"items\":[{\"id\":1}]}");
 *         assertEquals(201, response.getCode()); // GH-90000
 *     }
 * }
 * }</pre>
 *
 * <p><b>Extension Lifecycle</b><br>
 * <pre>
 * @BeforeEach (extension): // GH-90000
 * 1. Find AsyncServlet field in test instance
 * 2. Create HttpServerTestRunner with servlet
 * 3. Start runner (listen on random port) // GH-90000
 * 4. Inject runner into test instance fields
 * 5. Store runner in ExtensionContext
 *
 * Test Method Execution:
 * 6. Resolve HttpServerTestRunner parameter (if requested) // GH-90000
 * 7. Execute test method
 *
 * @AfterEach (extension): // GH-90000
 * 8. Retrieve runner from ExtensionContext
 * 9. Close runner (stop server, close eventloop) // GH-90000
 * 10. Clean up ExtensionContext
 * </pre>
 *
 * <p><b>Servlet Discovery</b><br>
 * Extension looks for AsyncServlet field in test class:
 * <pre>{@code
 * private final AsyncServlet servlet = ...;  // Found and used
 * private AsyncServlet servlet;              // Found and used
 * AsyncServlet servlet = ...;                // Found and used (package-private) // GH-90000
 * }</pre>
 *
 * <p><b>Parameter Injection</b><br>
 * Extension resolves HttpServerTestRunner parameters:
 * <pre>{@code
 * @Test
 * void testMethod(HttpServerTestRunner runner) { // GH-90000
 *     // runner injected automatically
 * }
 * }</pre>
 *
 * <p><b>Field Injection</b><br>
 * Extension injects runner into matching fields:
 * <pre>{@code
 * private HttpServerTestRunner runner;  // Injected automatically
 * }</pre>
 *
 * <p><b>Default Timeout</b><br>
 * <pre>
 * Default: 10 seconds
 * Custom: new HttpServerTestExtension(Duration.ofSeconds(30)) // GH-90000
 * </pre>
 *
 * <p><b>Test Isolation</b><br>
 * <pre>
 * - Fresh runner created for each test method
 * - Server started on new random port per test
 * - No state shared between tests
 * - Automatic cleanup after each test
 * </pre>
 *
 * <p><b>Common Patterns</b><br>
 * <pre>{@code
 * // Pattern 1: Stateless servlet (field initializer) // GH-90000
 * private final AsyncServlet servlet = createServlet(); // GH-90000
 *
 * // Pattern 2: Stateful servlet (@BeforeEach setup) // GH-90000
 * private AsyncServlet servlet;
 *
 * @BeforeEach
 * void setUp() { // GH-90000
 *     servlet = createServlet(); // GH-90000
 * }
 *
 * // Pattern 3: Custom timeout (@RegisterExtension) // GH-90000
 * @RegisterExtension
 * static HttpServerTestExtension extension =
 *     new HttpServerTestExtension(Duration.ofMinutes(1)); // GH-90000
 *
 * // Pattern 4: Multiple servlets (parameterize tests) // GH-90000
 * @ParameterizedTest
 * @MethodSource("servlets")
 * void testMultipleServlets(AsyncServlet servlet, HttpServerTestRunner runner) { // GH-90000
 *     // Test different servlet implementations
 * }
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * - Prefer method parameter injection (more explicit) // GH-90000
 * - Use field injection for shared runner access across methods
 * - Define servlet as final field when stateless
 * - Use @BeforeEach for servlet setup when stateful
 * - Set custom timeout for slow operations
 * - Keep tests isolated (no shared state) // GH-90000
 * - Verify response status, headers, and body
 * - Use HttpTestUtils for response parsing
 *
 * <p><b>Limitations</b><br>
 * - Only one AsyncServlet field supported (first found) // GH-90000
 * - Servlet field must be accessible (not private in some cases) // GH-90000
 * - Runner field must be named exactly "runner"
 * - No support for multiple runners per test
 *
 * <p><b>Thread Safety</b><br>
 * Extension is NOT thread-safe (JUnit 5 runs tests sequentially by default). // GH-90000
 * For parallel execution, ensure each test has isolated servlet instance.
 *
 * @see HttpServerTestRunner
 * @see HttpTestUtils
 * @see MockHttpClient
 * @see org.junit.jupiter.api.extension.ExtendWith
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose JUnit 5 extension for HTTP server testing with lifecycle management
 * @doc.layer core
 * @doc.pattern Extension
 */
public class HttpServerTestExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(HttpServerTestExtension.class); // GH-90000

    private static final String RUNNER_KEY = "httpRunner";

    private final Duration timeout;

    /**
     * Creates an extension with default 10 second timeout.
     */
    public HttpServerTestExtension() { // GH-90000
        this(Duration.ofSeconds(10)); // GH-90000
    }

    /**
     * Creates an extension with custom timeout.
     *
     * @param timeout The timeout for HTTP operations
     */
    public HttpServerTestExtension(Duration timeout) { // GH-90000
        this.timeout = timeout;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception { // GH-90000
        Object testInstance = context.getRequiredTestInstance(); // GH-90000

        // Find servlet field in test class
        AsyncServlet servlet = findServlet(testInstance); // GH-90000

        if (servlet != null) { // GH-90000
            // Create and start runner
            HttpServerTestRunner runner = HttpServerTestRunner.create(servlet, timeout); // GH-90000
            runner.start(); // GH-90000

            // Store for cleanup and parameter resolution
            context.getStore(NAMESPACE).put(RUNNER_KEY, runner); // GH-90000

            // Inject into test instance fields
            injectRunner(testInstance, runner); // GH-90000
        }
    }

    @Override
    public void afterEach(ExtensionContext context) { // GH-90000
        HttpServerTestRunner runner = context.getStore(NAMESPACE) // GH-90000
            .remove(RUNNER_KEY, HttpServerTestRunner.class); // GH-90000

        if (runner != null) { // GH-90000
            runner.close(); // GH-90000
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) { // GH-90000
        return parameterContext.getParameter().getType().equals(HttpServerTestRunner.class); // GH-90000
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) { // GH-90000
        return extensionContext.getStore(NAMESPACE).get(RUNNER_KEY, HttpServerTestRunner.class); // GH-90000
    }

    private AsyncServlet findServlet(Object testInstance) throws IllegalAccessException { // GH-90000
        Class<?> testClass = testInstance.getClass(); // GH-90000

        // Prefer an explicitly-named field 'servlet' when present so tests can declare multiple AsyncServlet fields
        try {
            Field servletField = testClass.getDeclaredField("servlet");
            if (AsyncServlet.class.isAssignableFrom(servletField.getType()) && !servletField.isSynthetic()) { // GH-90000
                servletField.setAccessible(true); // GH-90000
                return (AsyncServlet) servletField.get(testInstance); // GH-90000
            }
        } catch (NoSuchFieldException ignored) { // GH-90000
            // fall back to scanning fields
        }

        while (testClass != Object.class) { // GH-90000
            for (Field field : testClass.getDeclaredFields()) { // GH-90000
                if (AsyncServlet.class.isAssignableFrom(field.getType()) && !field.isSynthetic()) { // GH-90000
                    field.setAccessible(true); // GH-90000
                    return (AsyncServlet) field.get(testInstance); // GH-90000
                }
            }
            testClass = testClass.getSuperclass(); // GH-90000
        }

        return null;
    }

    private void injectRunner(Object testInstance, HttpServerTestRunner runner) throws IllegalAccessException { // GH-90000
        Class<?> testClass = testInstance.getClass(); // GH-90000

        while (testClass != Object.class) { // GH-90000
            for (Field field : testClass.getDeclaredFields()) { // GH-90000
                if (field.getType() == HttpServerTestRunner.class && !field.isSynthetic()) { // GH-90000
                    field.setAccessible(true); // GH-90000
                    field.set(testInstance, runner); // GH-90000
                }
            }
            testClass = testClass.getSuperclass(); // GH-90000
        }
    }

    /**
     * Gets the runner from the extension context.
     *
     * @param context The extension context
     * @return The HTTP server test runner
     */
    public static HttpServerTestRunner getRunner(ExtensionContext context) { // GH-90000
        return context.getStore(NAMESPACE).get(RUNNER_KEY, HttpServerTestRunner.class); // GH-90000
    }
}
