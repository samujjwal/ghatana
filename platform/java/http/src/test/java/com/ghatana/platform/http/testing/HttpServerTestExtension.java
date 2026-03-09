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
 * // 1. Method parameter injection (recommended)
 * @ExtendWith(HttpServerTestExtension.class)
 * class UserServiceHttpTest {
 *     
 *     private final AsyncServlet servlet = createUserServlet();
 *     
 *     @Test
 *     void shouldReturnUserById(HttpServerTestRunner runner) {
 *         HttpResponse response = runner.get("/api/users/123");
 *         
 *         assertEquals(200, response.getCode());
 *         assertEquals("application/json", response.getHeader("Content-Type"));
 *         
 *         User user = parseJson(response, User.class);
 *         assertEquals("123", user.getId());
 *     }
 *     
 *     @Test
 *     void shouldCreateUser(HttpServerTestRunner runner) {
 *         String userJson = "{\"name\":\"John Doe\",\"email\":\"john@example.com\"}";
 *         
 *         HttpResponse response = runner.post("/api/users", userJson);
 *         
 *         assertEquals(201, response.getCode());
 *         assertNotNull(response.getHeader("Location"));
 *     }
 * }
 *
 * // 2. Field injection (alternative)
 * @ExtendWith(HttpServerTestExtension.class)
 * class UserServiceHttpTest {
 *     
 *     private final AsyncServlet servlet = createUserServlet();
 *     private HttpServerTestRunner runner;  // Auto-injected by extension
 *     
 *     @Test
 *     void shouldReturnUsers() {
 *         HttpResponse response = runner.get("/api/users");
 *         
 *         assertEquals(200, response.getCode());
 *         
 *         User[] users = parseJson(response, User[].class);
 *         assertTrue(users.length > 0);
 *     }
 * }
 *
 * // 3. Custom timeout (30 seconds)
 * @ExtendWith(HttpServerTestExtension.class)
 * class SlowOperationTest {
 *     
 *     @RegisterExtension
 *     static HttpServerTestExtension extension = 
 *         new HttpServerTestExtension(Duration.ofSeconds(30));
 *     
 *     private final AsyncServlet servlet = createSlowServlet();
 *     
 *     @Test
 *     void shouldHandleSlowOperation(HttpServerTestRunner runner) {
 *         HttpResponse response = runner.get("/api/slow-operation");
 *         assertEquals(200, response.getCode());
 *     }
 * }
 *
 * // 4. Multiple test methods with isolation
 * @ExtendWith(HttpServerTestExtension.class)
 * class ProductServiceHttpTest {
 *     
 *     private final AsyncServlet servlet = createProductServlet();
 *     
 *     @Test
 *     void shouldListProducts(HttpServerTestRunner runner) {
 *         HttpResponse response = runner.get("/api/products");
 *         assertEquals(200, response.getCode());
 *     }
 *     
 *     @Test
 *     void shouldGetProductById(HttpServerTestRunner runner) {
 *         HttpResponse response = runner.get("/api/products/123");
 *         assertEquals(200, response.getCode());
 *     }
 *     
 *     @Test
 *     void shouldReturn404ForMissingProduct(HttpServerTestRunner runner) {
 *         HttpResponse response = runner.get("/api/products/999");
 *         assertEquals(404, response.getCode());
 *     }
 * }
 *
 * // 5. Integration with other extensions
 * @ExtendWith({HttpServerTestExtension.class, DatabaseTestExtension.class})
 * class FullStackTest {
 *     
 *     private final AsyncServlet servlet = createServletWithDatabase();
 *     
 *     @Test
 *     void shouldPersistUserToDatabaseViaHttp(HttpServerTestRunner runner) {
 *         // Create user via HTTP
 *         HttpResponse response = runner.post("/api/users", 
 *             "{\"name\":\"Test User\"}");
 *         assertEquals(201, response.getCode());
 *         
 *         // Verify in database
 *         User user = userRepository.findByName("Test User");
 *         assertNotNull(user);
 *     }
 * }
 *
 * // 6. Shared servlet setup with @BeforeEach
 * @ExtendWith(HttpServerTestExtension.class)
 * class OrderServiceHttpTest {
 *     
 *     private AsyncServlet servlet;
 *     
 *     @BeforeEach
 *     void setUp() {
 *         OrderService orderService = new OrderService();
 *         servlet = createServlet(orderService);
 *     }
 *     
 *     @Test
 *     void shouldCreateOrder(HttpServerTestRunner runner) {
 *         HttpResponse response = runner.post("/api/orders", 
 *             "{\"items\":[{\"id\":1}]}");
 *         assertEquals(201, response.getCode());
 *     }
 * }
 * }</pre>
 *
 * <p><b>Extension Lifecycle</b><br>
 * <pre>
 * @BeforeEach (extension):
 * 1. Find AsyncServlet field in test instance
 * 2. Create HttpServerTestRunner with servlet
 * 3. Start runner (listen on random port)
 * 4. Inject runner into test instance fields
 * 5. Store runner in ExtensionContext
 *
 * Test Method Execution:
 * 6. Resolve HttpServerTestRunner parameter (if requested)
 * 7. Execute test method
 *
 * @AfterEach (extension):
 * 8. Retrieve runner from ExtensionContext
 * 9. Close runner (stop server, close eventloop)
 * 10. Clean up ExtensionContext
 * </pre>
 *
 * <p><b>Servlet Discovery</b><br>
 * Extension looks for AsyncServlet field in test class:
 * <pre>{@code
 * private final AsyncServlet servlet = ...;  // Found and used
 * private AsyncServlet servlet;              // Found and used
 * AsyncServlet servlet = ...;                // Found and used (package-private)
 * }</pre>
 *
 * <p><b>Parameter Injection</b><br>
 * Extension resolves HttpServerTestRunner parameters:
 * <pre>{@code
 * @Test
 * void testMethod(HttpServerTestRunner runner) {
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
 * Custom: new HttpServerTestExtension(Duration.ofSeconds(30))
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
 * // Pattern 1: Stateless servlet (field initializer)
 * private final AsyncServlet servlet = createServlet();
 *
 * // Pattern 2: Stateful servlet (@BeforeEach setup)
 * private AsyncServlet servlet;
 * 
 * @BeforeEach
 * void setUp() {
 *     servlet = createServlet();
 * }
 *
 * // Pattern 3: Custom timeout (@RegisterExtension)
 * @RegisterExtension
 * static HttpServerTestExtension extension = 
 *     new HttpServerTestExtension(Duration.ofMinutes(1));
 *
 * // Pattern 4: Multiple servlets (parameterize tests)
 * @ParameterizedTest
 * @MethodSource("servlets")
 * void testMultipleServlets(AsyncServlet servlet, HttpServerTestRunner runner) {
 *     // Test different servlet implementations
 * }
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * - Prefer method parameter injection (more explicit)
 * - Use field injection for shared runner access across methods
 * - Define servlet as final field when stateless
 * - Use @BeforeEach for servlet setup when stateful
 * - Set custom timeout for slow operations
 * - Keep tests isolated (no shared state)
 * - Verify response status, headers, and body
 * - Use HttpTestUtils for response parsing
 *
 * <p><b>Limitations</b><br>
 * - Only one AsyncServlet field supported (first found)
 * - Servlet field must be accessible (not private in some cases)
 * - Runner field must be named exactly "runner"
 * - No support for multiple runners per test
 *
 * <p><b>Thread Safety</b><br>
 * Extension is NOT thread-safe (JUnit 5 runs tests sequentially by default).
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
        ExtensionContext.Namespace.create(HttpServerTestExtension.class);
    
    private static final String RUNNER_KEY = "httpRunner";
    
    private final Duration timeout;
    
    /**
     * Creates an extension with default 10 second timeout.
     */
    public HttpServerTestExtension() {
        this(Duration.ofSeconds(10));
    }
    
    /**
     * Creates an extension with custom timeout.
     * 
     * @param timeout The timeout for HTTP operations
     */
    public HttpServerTestExtension(Duration timeout) {
        this.timeout = timeout;
    }
    
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Object testInstance = context.getRequiredTestInstance();
        
        // Find servlet field in test class
        AsyncServlet servlet = findServlet(testInstance);
        
        if (servlet != null) {
            // Create and start runner
            HttpServerTestRunner runner = HttpServerTestRunner.create(servlet, timeout);
            runner.start();
            
            // Store for cleanup and parameter resolution
            context.getStore(NAMESPACE).put(RUNNER_KEY, runner);
            
            // Inject into test instance fields
            injectRunner(testInstance, runner);
        }
    }
    
    @Override
    public void afterEach(ExtensionContext context) {
        HttpServerTestRunner runner = context.getStore(NAMESPACE)
            .remove(RUNNER_KEY, HttpServerTestRunner.class);
        
        if (runner != null) {
            runner.close();
        }
    }
    
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(HttpServerTestRunner.class);
    }
    
    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE).get(RUNNER_KEY, HttpServerTestRunner.class);
    }
    
    private AsyncServlet findServlet(Object testInstance) throws IllegalAccessException {
        Class<?> testClass = testInstance.getClass();
        
        // Prefer an explicitly-named field 'servlet' when present so tests can declare multiple AsyncServlet fields
        try {
            Field servletField = testClass.getDeclaredField("servlet");
            if (AsyncServlet.class.isAssignableFrom(servletField.getType()) && !servletField.isSynthetic()) {
                servletField.setAccessible(true);
                return (AsyncServlet) servletField.get(testInstance);
            }
        } catch (NoSuchFieldException ignored) {
            // fall back to scanning fields
        }

        while (testClass != Object.class) {
            for (Field field : testClass.getDeclaredFields()) {
                if (AsyncServlet.class.isAssignableFrom(field.getType()) && !field.isSynthetic()) {
                    field.setAccessible(true);
                    return (AsyncServlet) field.get(testInstance);
                }
            }
            testClass = testClass.getSuperclass();
        }
        
        return null;
    }
    
    private void injectRunner(Object testInstance, HttpServerTestRunner runner) throws IllegalAccessException {
        Class<?> testClass = testInstance.getClass();
        
        while (testClass != Object.class) {
            for (Field field : testClass.getDeclaredFields()) {
                if (field.getType() == HttpServerTestRunner.class && !field.isSynthetic()) {
                    field.setAccessible(true);
                    field.set(testInstance, runner);
                }
            }
            testClass = testClass.getSuperclass();
        }
    }
    
    /**
     * Gets the runner from the extension context.
     * 
     * @param context The extension context
     * @return The HTTP server test runner
     */
    public static HttpServerTestRunner getRunner(ExtensionContext context) {
        return context.getStore(NAMESPACE).get(RUNNER_KEY, HttpServerTestRunner.class);
    }
}
