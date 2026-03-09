package com.ghatana.platform.testing;

import com.ghatana.platform.testing.utils.TestLoggingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;

/**
 * Base test class providing standardized test lifecycle and logging infrastructure.
 *
 * <h2>Purpose</h2>
 * Provides a shared foundation for unit and integration tests with:
 * <ul>
 *   <li>Automatic test lifecycle logging (start/end/duration)</li>
 *   <li>Structured test step logging</li>
 *   <li>Per-class test instance lifecycle</li>
 *   <li>Extension integration (for resource management)</li>
 *   <li>Pre/post-test hooks (beforeTest/afterTest)</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 *
 * <h3>1. Test Lifecycle Logging</h3>
 * Automatically logs when tests start/end with execution duration:
 * <pre>
 * [STARTING] My test name
 * [DEBUG] Test execution time: 123 ms
 * [ENDING] My test name
 * </pre>
 *
 * <h3>2. Per-Class Lifecycle</h3>
 * Uses {@code @TestInstance(Lifecycle.PER_CLASS)} so:
 * <ul>
 *   <li>Instance created once per test class</li>
 *   <li>Can use non-static @BeforeAll/@AfterAll</li>
 *   <li>Enables shared test fixtures</li>
 *   <li>More efficient resource setup</li>
 * </ul>
 *
 * <h3>3. Lifecycle Aware Extension</h3>
 * Integrates with {@code LifecycleAwareExtension} for:
 * <ul>
 *   <li>Resource lifecycle callbacks</li>
 *   <li>Test context propagation</li>
 *   <li>Automatic cleanup on failure</li>
 *   <li>Thread-local context management</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * {@code
 * // 1. Basic usage - simple test class
 * public class UserServiceTest extends BaseTest {
 *     private UserService userService;
 *
 *     @Override
 *     protected void beforeTest() {
 *         userService = new UserService();
 *     }
 *
 *     @Test
 *     @DisplayName("should create user with valid name")
 *     void shouldCreateUserWithValidName() {
 *         logStep("Creating user", "name=Alice");
 *         User user = userService.createUser("Alice");
 *         
 *         logStep("Verifying user");
 *         assertThat(user.getName()).isEqualTo("Alice");
 *     }
 * }
 *
 * // 2. With test fixtures and cleanup
 * public class DatabaseTest extends BaseTest {
 *     private Database db;
 *     private Session session;
 *
 *     @Override
 *     protected void beforeTest() {
 *         db = new Database();
 *         session = db.connect();
 *         logStep("Database connected");
 *     }
 *
 *     @Override
 *     protected void afterTest() {
 *         if (session != null) {
 *             session.close();
 *             logStep("Database disconnected");
 *         }
 *     }
 *
 *     @Test
 *     void shouldQueryData() {
 *         logStep("Querying users from database");
 *         List<User> users = db.query("SELECT * FROM users");
 *         assertThat(users).isNotEmpty();
 *     }
 * }
 *
 * // 3. Multi-step test with detailed logging
 * @Test
 * void shouldProcessOrderWithMultipleSteps() {
 *     logStep("Step 1: Create order");
 *     Order order = orderService.createOrder(...);
 *
 *     logStep("Step 2: Apply discount", "discount=10%");
 *     order = orderService.applyDiscount(order, 0.10);
 *
 *     logStep("Step 3: Validate total");
 *     assertThat(order.getTotal()).isEqualTo(expectedTotal);
 *
 *     logStep("Step 4: Submit order");
 *     orderService.submit(order);
 *
 *     logStep("Step 5: Verify confirmation");
 *     // Assertions...
 * }
 * }
 *
 * <h2>Test Logging Output</h2>
 * <pre>
 * [INFO] [STARTING] shouldProcessOrderWithMultipleSteps
 * [DEBUG] STEP: Step 1: Create order
 * [DEBUG] STEP: Step 2: Apply discount [discount=10%]
 * [DEBUG] STEP: Step 3: Validate total
 * [DEBUG] STEP: Step 4: Submit order
 * [DEBUG] STEP: Step 5: Verify confirmation
 * [DEBUG] Test execution time: 47 ms
 * [INFO] [ENDING] shouldProcessOrderWithMultipleSteps
 * </pre>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li><b>Always call super.beforeTest() if overriding</b> (or document why not)</li>
 *   <li><b>Use logStep() for complex multi-step tests</b> (aids debugging)</li>
 *   <li><b>Always implement afterTest() for resource cleanup</b> (even if empty)</li>
 *   <li><b>Don't suppress exceptions in afterTest()</b> (tests should fail if cleanup fails)</li>
 *   <li><b>Use @DisplayName on test methods</b> (logged as testName)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Not thread-safe. Each test class instance is isolated (per-class lifecycle).
 * For concurrent tests, use @Execution(ExecutionMode.CONCURRENT) cautiously
 * and provide additional synchronization if needed.
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>Per-class instantiation</b>: O(1) per test class, not per test method</li>
 *   <li><b>Logging overhead</b>: <1ms for typical test (negligible)</li>
 *   <li><b>Memory</b>: Single instance per test class (efficient)</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Layer:</b> Testing infrastructure</li>
 *   <li><b>Module:</b> core/testing/test-utils</li>
 *   <li><b>Usage:</b> Base class for ALL unit/integration tests</li>
 *   <li><b>Pattern:</b> Test template method, lifecycle management</li>
 * </ul>
 *
 * <h2>Integration with Other Test Utilities</h2>
 * <ul>
 *   <li>Works with {@link TestClock} for deterministic time handling</li>
 *   <li>Works with {@link LocalTestHttpServer} for HTTP testing</li>
 *   <li>Works with {@link EventloopTestBase} for ActiveJ Promise testing</li>
 *   <li>Supports {@link LifecycleAwareExtension} for resource management</li>
 * </ul>
 *
 * @see TestClock Deterministic time for testing
 * @see TestLoggingUtils Structured test logging
 * @see LifecycleAwareExtension JUnit 5 extension for lifecycle management
 * @see org.junit.jupiter.api.extension.ExtendWith JUnit 5 extension mechanism
 * @doc.type class
 * @doc.layer testing
 * @doc.purpose base test class with lifecycle and logging infrastructure
 * @doc.pattern test-template test-lifecycle-management test-base-class
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(LifecycleAwareExtension.class)
public abstract class BaseTest implements TestLifecycleCallback {
    
    protected final Logger log = TestLoggingUtils.getLogger(getClass());
    private long testStartTime;
    private String testName;
    
    /**
     * Performs automatic setup before each test method.
     *
     * <p>Framework method (called by JUnit 5) that:
     * 1. Captures test name and start time
     * 2. Logs test starting
     * 3. Calls {@link #beforeTest()} for subclass setup
     *
     * <p><b>Note:</b> This is called by JUnit 5 automatically.
     * Override {@link #beforeTest()} instead, not this method.
     *
     * @param testInfo JUnit 5 test information (injected by framework)
     */
    @BeforeEach
    protected void setUpTest(TestInfo testInfo) {
        this.testName = testInfo.getDisplayName();
        testStartTime = System.currentTimeMillis();
        TestLoggingUtils.logTestStart(log, testName);
        beforeTest();
    }
    
    /**
     * Performs automatic cleanup after each test method.
     *
     * <p>Framework method (called by JUnit 5) that:
     * 1. Calls {@link #afterTest()} for subclass cleanup
     * 2. Calculates test duration
     * 3. Logs test ending with duration
     * 4. Ensures logging happens even if afterTest() throws
     *
     * <p><b>Note:</b> This is called by JUnit 5 automatically.
     * Override {@link #afterTest()} instead, not this method.
     */
    @AfterEach
    protected void tearDownTest() {
        try {
            afterTest();
        } finally {
            long duration = System.currentTimeMillis() - testStartTime;
            log.debug("Test execution time: {} ms", duration);
            TestLoggingUtils.logTestEnd(log, testName, true);
        }
    }
    
    /**
     * Hook for subclasses to perform setup before each test.
     *
     * <p>Called after JUnit 5 invokes @BeforeEach methods but before the test runs.
     * Use this to initialize test fixtures, mock objects, or other test setup.
     *
     * <p><b>Default Implementation:</b> Does nothing (no-op).
     * Subclasses override this when setup is needed.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * public class MyTest extends BaseTest {
     *     private MyService service;
     *
     *     @Override
     *     protected void beforeTest() {
     *         service = new MyService();
     *     }
     * }
     * }</pre>
     *
     * @see #afterTest() Cleanup hook called after test
     */
    protected void beforeTest() {
        // Default implementation does nothing
    }
    
    /**
     * Hook for subclasses to perform cleanup after each test.
     *
     * <p>Called after the test finishes but before @AfterEach methods run.
     * Use this to close resources, reset state, or perform cleanup.
     * Guaranteed to run even if the test throws an exception.
     *
     * <p><b>Default Implementation:</b> Does nothing (no-op).
     * Subclasses override this when cleanup is needed.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * public class DatabaseTest extends BaseTest {
     *     private Database db;
     *
     *     @Override
     *     protected void beforeTest() {
     *         db = new Database();
     *     }
     *
     *     @Override
     *     protected void afterTest() {
     *         if (db != null) db.close();  // Always cleaned up
     *     }
     * }
     * }</pre>
     *
     * <p><b>Important:</b> If afterTest() throws an exception, the test will fail
     * with that exception rather than the original test failure (if any).
     * Wrap cleanup in try-finally if you need to mask cleanup failures.
     *
     * @see #beforeTest() Setup hook called before test
     */
    protected void afterTest() {
        // Default implementation does nothing
    }
    
    /**
     * Logs a test step for debugging multi-step tests.
     *
     * <p>Useful for complex tests with multiple stages. Each step is logged
     * at DEBUG level with optional details for tracing test execution flow.
     *
     * <p><b>Output Format:</b>
     * <pre>
     * [DEBUG] STEP: Setup database
     * [DEBUG] STEP: Create user [name=Alice, age=30]
     * [DEBUG] STEP: Verify result
     * </pre>
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * @Test
     * void shouldProcessOrder() {
     *     logStep("Create order");
     *     Order order = orderService.create(...);
     *
     *     logStep("Apply discount", "amount=50", "code=SUMMER2025");
     *     orderService.applyDiscount(order, code);
     *
     *     logStep("Verify total");
     *     assertThat(order.getTotal()).isEqualTo(450);
     * }
     * }</pre>
     *
     * <p><b>Best Practices:</b>
     * <ul>
     *   <li>Use for tests with 3+ logical steps</li>
     *   <li>Include relevant parameters in details</li>
     *   <li>Keep step descriptions short and clear</li>
     *   <li>Don't use for trivial single-assertion tests</li>
     * </ul>
     *
     * @param step description of the step being executed (never null)
     * @param details optional additional context (variable args for flexibility)
     */
    protected void logStep(String step, Object... details) {
        TestLoggingUtils.logTestStep(log, step, details);
    }
}
