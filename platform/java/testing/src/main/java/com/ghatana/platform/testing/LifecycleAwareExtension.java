package com.ghatana.platform.testing;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

/**
 * JUnit 5 extension providing lifecycle callback delegation to test instances.
 *
 * <h2>Purpose</h2>
 * Bridges JUnit 5 lifecycle events to custom test lifecycle handlers, enabling:
 * <ul>
 *   <li>Per-class and per-method lifecycle callbacks</li>
 *   <li>Automatic test instance lifecycle management</li>
 *   <li>Centralized resource setup/teardown coordination</li>
 *   <li>Error handling and exception wrapping</li>
 *   <li>State tracking across test execution</li>
 * </ul>
 *
 * <h2>JUnit 5 Lifecycle Integration</h2>
 * <pre>
 * Test Class Lifecycle:
 * =====================
 * [beforeAll]          ← Extension.beforeAll() → TestLifecycleCallback.beforeAll()
 *   ↓
 * [Test 1 starts]      ← Extension.beforeEach() → TestLifecycleCallback.beforeEach()
 * [Test 1 runs]
 * [Test 1 ends]        ← Extension.afterEach() → TestLifecycleCallback.afterEach()
 *   ↓
 * [Test 2 starts]      ← Extension.beforeEach()
 * [Test 2 runs]
 * [Test 2 ends]        ← Extension.afterEach()
 *   ↓
 * [afterAll]           ← Extension.afterAll() → TestLifecycleCallback.afterAll()
 * </pre>
 *
 * <h2>Key Features</h2>
 *
 * <h3>1. Automatic Test Instance Detection</h3>
 * Detects if test class implements {@code TestLifecycleCallback} and provides callbacks.
 *
 * <h3>2. Unified Exception Handling</h3>
 * Wraps all callback exceptions as RuntimeException for consistent error handling.
 *
 * <h3>3. Call Counting</h3>
 * Ensures {@code beforeAll()} and {@code afterAll()} called exactly once per test class.
 *
 * <h3>4. Context Isolation</h3>
 * Each test class has isolated ExtensionContext with separate storage.
 *
 * <h2>Usage Examples</h2>
 * {@code
 * // 1. Basic usage with BaseTest
 * @ExtendWith(LifecycleAwareExtension.class)
 * public class UserServiceTest extends BaseTest {
 *     private UserService service;
 *
 *     @Override
 *     protected void beforeTest() {
 *         service = new UserService();
 *     }
 *
 *     @Test
 *     void shouldCreateUser() {
 *         User user = service.createUser("Alice");
 *         assertThat(user).isNotNull();
 *     }
 * }
 *
 * // 2. Explicit lifecycle callback implementation
 * @ExtendWith(LifecycleAwareExtension.class)
 * public class DatabaseTest implements TestLifecycleCallback {
 *     private Database db;
 *
 *     @Override
 *     public void beforeAll(ExtensionContext context) {
 *         // Runs once before all tests in this class
 *         db = new Database();
 *         db.initialize();
 *     }
 *
 *     @Override
 *     public void beforeEach(ExtensionContext context) {
 *         // Runs before each test
 *         db.beginTransaction();
 *     }
 *
 *     @Override
 *     public void afterEach(ExtensionContext context) {
 *         // Runs after each test
 *         db.rollback();  // Ensures test isolation
 *     }
 *
 *     @Override
 *     public void afterAll(ExtensionContext context) {
 *         // Runs once after all tests
 *         db.close();
 *     }
 *
 *     @Test
 *     void shouldQueryData() {
 *         List<User> users = db.query("SELECT * FROM users");
 *         assertThat(users).isNotEmpty();
 *     }
 * }
 *
 * // 3. Mixed with other extensions
 * @ExtendWith(LifecycleAwareExtension.class)  // Custom lifecycle
 * @ExtendWith(MockitoExtension.class)         // Mockito injection
 * public class ServiceTest extends BaseTest {
 *     @Mock
 *     private Repository repo;
 *
 *     private Service service;
 *
 *     @Override
 *     protected void beforeTest() {
 *         service = new Service(repo);  // Mockito injectable
 *     }
 * }
 * }
 *
 * <h2>Lifecycle Event Sequence</h2>
 *
 * <h3>For Per-Class Tests (@TestInstance(Lifecycle.PER_CLASS))</h3>
 * <pre>
 * Test Class Instance Created
 * ↓
 * Extension.postProcessTestInstance() [captures test instance]
 * ↓
 * Extension.beforeAll() [calls TestLifecycleCallback.beforeAll()]
 * ↓
 * For each test method:
 *   Extension.beforeEach() → TestLifecycleCallback.beforeEach()
 *   Test method runs
 *   Extension.afterEach() → TestLifecycleCallback.afterEach()
 * ↓
 * Extension.afterAll() [calls TestLifecycleCallback.afterAll()]
 * ↓
 * Test Class Instance Destroyed
 * </pre>
 *
 * <h3>For Per-Method Tests (default)</h3>
 * <pre>
 * For each test method:
 *   New Test Instance Created
 *   Extension.beforeAll() [first method only]
 *   Extension.beforeEach()
 *   Test method runs
 *   Extension.afterEach()
 *   Extension.afterAll() [last method only]
 *   Test Instance Destroyed
 * </pre>
 *
 * <h2>Error Handling Strategy</h2>
 * <ul>
 *   <li>All lifecycle callback exceptions wrapped as RuntimeException</li>
 *   <li>Original exception preserved as cause (stack trace available)</li>
 *   <li>Extension execution stops at first exception</li>
 *   <li>Test fails with wrapped exception</li>
 * </ul>
 * <pre>{@code
 * // Example: beforeEach throws exception
 * try {
 *     callback.beforeEach(context);
 * } catch (Exception e) {
 *     // Wrapped as RuntimeException
 *     throw new RuntimeException("Error in beforeEach callback", e);
 * }
 * }</pre>
 *
 * <h2>State Storage in ExtensionContext</h2>
 * <p>Extension uses ExtensionContext.getStore() to track state:
 * <ul>
 *   <li><b>beforeAllCounter:</b> Ensures beforeAll() called once</li>
 *   <li><b>afterAllCounter:</b> Ensures afterAll() called once</li>
 *   <li><b>testInstance:</b> Stores captured test instance</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Not thread-safe for concurrent test execution. Use with @Execution(SAME_THREAD)
 * or provide synchronization for shared state access.
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>beforeAll:</b> O(1) - single callback, called once per class</li>
 *   <li><b>beforeEach:</b> O(1) - single callback per method</li>
 *   <li><b>afterEach:</b> O(1) - single callback per method</li>
 *   <li><b>afterAll:</b> O(1) - single callback, called once per class</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Layer:</b> JUnit 5 extension infrastructure</li>
 *   <li><b>Module:</b> core/testing/test-utils</li>
 *   <li><b>Pattern:</b> Extension SPI, lifecycle pattern, template method</li>
 * </ul>
 *
 * <h2>Integration with BaseTest</h2>
 * BaseTest is annotated with @ExtendWith(LifecycleAwareExtension.class) and
 * implements TestLifecycleCallback. Subclasses can override beforeTest() and
 * afterTest() hooks to customize lifecycle behavior.
 *
 * <h2>Comparison: Extension vs TestWatcher</h2>
 * <table>
 *   <tr>
 *     <th>Aspect</th>
 *     <th>LifecycleAwareExtension</th>
 *     <th>TestWatcher</th>
 *   </tr>
 *   <tr>
 *     <td>Class-level setup/teardown</td>
 *     <td>✅ beforeAll/afterAll</td>
 *     <td>❌ Not available</td>
 *   </tr>
 *   <tr>
 *     <td>Method-level setup/teardown</td>
 *     <td>✅ beforeEach/afterEach</td>
 *     <td>✅ starting/finished</td>
 *   </tr>
 *   <tr>
 *     <td>Custom callback interface</td>
 *     <td>✅ TestLifecycleCallback</td>
 *     <td>❌ Statement-based only</td>
 *   </tr>
 *   <tr>
 *     <td>Test result handling</td>
 *     <td>❌ No test result info</td>
 *     <td>✅ Result context available</td>
 *   </tr>
 * </table>
 *
 * @see TestLifecycleCallback Callback interface implementation
 * @see BaseTest Extension usage example
 * @see org.junit.jupiter.api.extension.Extension JUnit 5 extension API
 * @doc.type class
 * @doc.layer testing
 * @doc.purpose JUnit 5 extension providing lifecycle callbacks to test instances
 * @doc.pattern junit5-extension lifecycle-management callback-delegation
 */
public final class LifecycleAwareExtension 
        implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback,
                   TestInstancePostProcessor {

    private static final String TEST_INSTANCE_KEY = "testInstance";
    private static final String BEFORE_ALL_COUNTER = "beforeAllCounter";
    private static final String AFTER_ALL_COUNTER = "afterAllCounter";
    
    private TestLifecycleCallback testInstance;

    @Override
    public void beforeAll(ExtensionContext context) {
        // Initialize counters if not present
        getOrCreateCounter(context, BEFORE_ALL_COUNTER);
        
        // Only call beforeAll once per test class
        if (getCounterValue(context, BEFORE_ALL_COUNTER) == 0) {
            getCallback(context).ifPresent(callback -> {
                try {
                    callback.beforeAll(context);
                } catch (Exception e) {
                    throw new RuntimeException("Error in beforeAll callback", e);
                }
            });
        }
        incrementCounter(context, BEFORE_ALL_COUNTER);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Initialize counters if not present
        getOrCreateCounter(context, AFTER_ALL_COUNTER);
        
        // Only call afterAll once per test class
        if (getCounterValue(context, AFTER_ALL_COUNTER) == 0) {
            getCallback(context).ifPresent(callback -> {
                try {
                    callback.afterAll(context);
                } catch (Exception e) {
                    throw new RuntimeException("Error in afterAll callback", e);
                }
            });
        }
        incrementCounter(context, AFTER_ALL_COUNTER);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        getCallback(context).ifPresent(callback -> {
            try {
                callback.beforeEach(context);
            } catch (Exception e) {
                throw new RuntimeException("Error in beforeEach callback", e);
            }
        });
    }

    @Override
    public void afterEach(ExtensionContext context) {
        getCallback(context).ifPresent(callback -> {
            try {
                callback.afterEach(context);
            } catch (Exception e) {
                throw new RuntimeException("Error in afterEach callback", e);
            }
        });
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        if (testInstance instanceof TestLifecycleCallback) {
            this.testInstance = (TestLifecycleCallback) testInstance;
            // Store the test instance in the context for static access
            context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestClass()))
                   .put(TEST_INSTANCE_KEY, this.testInstance);
        }
    }

    private Optional<TestLifecycleCallback> getCallback(ExtensionContext context) {
        // First try to get from the stored instance (for non-static callbacks)
        if (testInstance != null) {
            return Optional.of(testInstance);
        }
        
        // Try to get from the context store (for static callbacks)
        return Optional.ofNullable(
            context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestClass()))
                   .get(TEST_INSTANCE_KEY, TestLifecycleCallback.class)
        ).or(() -> {
            // Fall back to getting from test instance (for non-static fields in test class)
            return context.getTestInstance()
                   .filter(TestLifecycleCallback.class::isInstance)
                   .map(TestLifecycleCallback.class::cast);
        });
    }
    
    private void incrementCounter(ExtensionContext context, String counterName) {
        ExtensionContext.Store store = getStore(context);
        store.put(counterName, store.get(counterName, AtomicInteger.class).incrementAndGet());
    }
    
    private int getCounterValue(ExtensionContext context, String counterName) {
        return getStore(context).get(counterName, AtomicInteger.class).get();
    }
    
    private AtomicInteger getOrCreateCounter(ExtensionContext context, String counterName) {
        return getStore(context).getOrComputeIfAbsent(counterName, key -> new AtomicInteger(0), AtomicInteger.class);
    }
    
    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestClass()));
    }
}
