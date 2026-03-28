/**
 * Platform testing utilities: base classes, test data builders, container fixtures,
 * ActiveJ event-loop helpers, assertions, and chaos testing support.
 *
 * <h2>Test Fixtures (TEST-001)</h2>
 *
 * <p>Use the test utilities provided here instead of writing custom fixtures in each module:</p>
 *
 * <ul>
 * <li>{@link com.ghatana.platform.testing.data.RandomDataBuilder} — randomized test data
 *     generation. Preferred over {@code UUID.randomUUID().toString()} literals scattered
 *     through test code.</li>
 * <li>{@link com.ghatana.platform.testing.data.TestDataBuilders} — pre-built domain object
 *     factories for common domain types.</li>
 * <li>{@link com.ghatana.platform.testing.data.CommonTestData} — well-known stable
 *     constant test values (tenant IDs, user IDs, etc.).</li>
 * <li>{@link com.ghatana.platform.testing.fixtures.TestFixture} — fixture lifecycle
 *     contract for stateful test resources.</li>
 * </ul>
 *
 * <pre>{@code
 * RandomDataBuilder data = new RandomDataBuilder();
 * String tenantId = data.uuid().get();
 * String username = data.alphaNumeric(10).get();
 * int age = data.integer(18, 65).get();
 * }</pre>
 *
 * <h2>Mock Objects (TEST-002)</h2>
 *
 * <p>Use Mockito (already on the test classpath via {@code platform:java:testing}) for
 * all mocked dependencies. Keep mocks in test scope only. Use
 * {@link com.ghatana.platform.testing.utils.ServiceTestUtils} for common service-layer
 * mock setups that span multiple test classes.</p>
 *
 * <pre>{@code
 * // Standard Mockito mock:
 * MyService mockService = Mockito.mock(MyService.class);
 * Mockito.when(mockService.process(any())).thenReturn(expectedResult);
 * }</pre>
 *
 * <h2>Test Configuration (TEST-003)</h2>
 *
 * <p>Use {@link com.ghatana.platform.testing.utils.ConfigTestUtils} to build in-memory
 * {@code ConfigManager} instances for tests. Do not rely on environment variables or
 * real config files in unit tests:</p>
 *
 * <pre>{@code
 * ConfigManager config = ConfigTestUtils.inMemoryConfig(Map.of(
 *     "JWT_SECRET", "test-secret",
 *     "PORT",       "8080"
 * ));
 * }</pre>
 *
 * <h2>ActiveJ Async Tests</h2>
 *
 * <p>All tests that exercise ActiveJ {@code Promise}-returning code must extend
 * {@link com.ghatana.platform.testing.activej.EventloopTestBase} and use
 * {@code runPromise(() -> ...)} to drive promises:</p>
 *
 * <pre>{@code
 * class MyServiceTest extends EventloopTestBase {
 *     \@Test
 *     void shouldProcessAsync() {
 *         MyService svc = new MyService();
 *         String result = runPromise(() -> svc.processAsync("input"));
 *         assertThat(result).isEqualTo("expected");
 *     }
 * }
 * }</pre>
 *
 * <h2>Container-Based Integration Tests</h2>
 *
 * <p>Use the pre-configured Testcontainers wrappers for infrastructure dependencies:</p>
 * <ul>
 * <li>{@link com.ghatana.platform.testing.internal.containers.PostgresTestContainer}</li>
 * <li>{@link com.ghatana.platform.testing.internal.containers.RedisTestContainer}</li>
 * <li>{@link com.ghatana.platform.testing.internal.containers.KafkaTestContainer}</li>
 * </ul>
 */
package com.ghatana.platform.testing;
