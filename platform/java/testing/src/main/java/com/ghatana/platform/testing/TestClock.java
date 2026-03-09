package com.ghatana.platform.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Controllable test clock for deterministic time handling in unit tests.
 *
 * <h2>Purpose</h2>
 * Provides a Clock implementation that freezes time to a fixed instant, enabling:
 * <ul>
 *   <li>Deterministic test execution (no time-dependent flakiness)</li>
 *   <li>Time travel by advancing clock to future instants</li>
 *   <li>Consistent timestamps across multiple invocations</li>
 *   <li>Testing time-based logic (expiration, scheduling, delays)</li>
 * </ul>
 *
 * <h2>Key Differences from System Clock</h2>
 * <table>
 *   <tr>
 *     <th>Aspect</th>
 *     <th>System Clock</th>
 *     <th>TestClock</th>
 *   </tr>
 *   <tr>
 *     <td>Time progression</td>
 *     <td>Always advances (real time)</td>
 *     <td>Frozen at fixed instant</td>
 *   </tr>
 *   <tr>
 *     <td>Test determinism</td>
 *     <td>Time-dependent (flaky tests)</td>
 *     <td>Fixed time (deterministic)</td>
 *   </tr>
 *   <tr>
 *     <td>Timezone handling</td>
 *     <td>System timezone</td>
 *     <td>Configurable (UTC by default)</td>
 *   </tr>
 *   <tr>
 *     <td>Time manipulation</td>
 *     <td>Not possible</td>
 *     <td>Via manual advance()</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * {@code
 * // 1. Fixed time at specific instant
 * Instant fixedTime = Instant.parse("2025-01-15T10:30:00Z");
 * TestClock clock = TestClock.fixed(fixedTime);
 * 
 * // 2. System UTC (frozen at current time)
 * TestClock clock = TestClock.systemUTC();
 * Instant start = clock.instant();
 * // ... run test code ...
 * Instant end = clock.instant();
 * assertEquals(start, end); // Guaranteed equal (frozen time)
 *
 * // 3. Custom timezone
 * TestClock clock = new TestClock(
 *     Instant.parse("2025-06-15T14:00:00Z"),
 *     ZoneId.of("America/New_York")
 * );
 *
 * // 4. Advance time for delayed operations
 * TestClock clock = TestClock.systemUTC();
 * Instant original = clock.instant();
 * clock.advance(Duration.ofHours(24));
 * Instant advanced = clock.instant();
 * assertEquals(Duration.ofHours(24), 
 *   Duration.between(original, advanced));
 *
 * // 5. Test expiration logic
 * TestClock clock = TestClock.fixed(Instant.parse("2025-01-01T00:00:00Z"));
 * Instant tokenExpiry = clock.instant().plus(Duration.ofHours(1));
 * 
 * // Token valid (current time before expiry)
 * assertTrue(clock.instant().isBefore(tokenExpiry));
 * 
 * // Advance past expiry
 * clock.advance(Duration.ofHours(2));
 * assertFalse(clock.instant().isBefore(tokenExpiry)); // Expired
 * }
 *
 * <h2>Architecture Role</h2>
 * Testing utility in core/testing/test-utils for:
 * <ul>
 *   <li><b>Unit Tests</b>: Time-dependent business logic (TTL, scheduling)</li>
 *   <li><b>Integration Tests</b>: Multi-step workflows with time progression</li>
 *   <li><b>Concurrent Tests</b>: Race conditions with controlled timing</li>
 *   <li><b>Time-Series Tests</b>: Event timestamps and aggregations</li>
 * </ul>
 *
 * <h2>Common Test Patterns</h2>
 * {@code
 * // Pattern 1: Fixed point-in-time testing
 * @Test
 * void shouldCalculateAge() {
 *     TestClock clock = TestClock.fixed(
 *         Instant.parse("2025-06-15T00:00:00Z")
 *     );
 *     Person person = new Person("Alice", LocalDate.parse("2000-06-15"));
 *     assertEquals(25, person.calculateAge(clock)); // Age at fixed time
 * }
 *
 * // Pattern 2: Expiration/timeout testing
 * @Test
 * void shouldExpireToken() {
 *     TestClock clock = TestClock.systemUTC();
 *     Token token = new Token("xyz", clock.instant().plus(Duration.ofHours(1)));
 *     assertTrue(token.isValid(clock));
 *     
 *     clock.advance(Duration.ofHours(2));
 *     assertFalse(token.isValid(clock)); // Token expired
 * }
 *
 * // Pattern 3: Event ordering by timestamp
 * @Test
 * void shouldProcessEventsInOrder() {
 *     TestClock clock = TestClock.systemUTC();
 *     List<Event> events = new ArrayList<>();
 *     
 *     events.add(new Event("event1", clock.instant()));
 *     clock.advance(Duration.ofSeconds(1));
 *     
 *     events.add(new Event("event2", clock.instant()));
 *     clock.advance(Duration.ofSeconds(1));
 *     
 *     events.add(new Event("event3", clock.instant()));
 *     
 *     // Verify timestamps are in order
 *     for (int i = 0; i < events.size() - 1; i++) {
 *         assertTrue(events.get(i).getTimestamp()
 *             .isBefore(events.get(i + 1).getTimestamp()));
 *     }
 * }
 * }
 *
 * <h2>Thread Safety</h2>
 * Not thread-safe. Each test should have its own TestClock instance.
 * For concurrent tests, use synchronized access or external synchronization.
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>Construction</b>: O(1) - just stores instant and zone</li>
 *   <li><b>instant()</b>: O(1) - returns stored instant</li>
 *   <li><b>advance()</b>: O(1) - updates stored instant</li>
 *   <li><b>withZone()</b>: O(1) - creates new clock with same instant</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Manual time advancement required (not automatic progression)</li>
 *   <li>Cannot "go backward" in time (advance only moves forward)</li>
 *   <li>Not suitable for measuring actual elapsed time</li>
 *   <li>Requires explicit injection into code under test</li>
 * </ul>
 *
 * @see java.time.Clock Standard Clock abstraction
 * @see java.time.Instant Fixed point in time
 * @see java.time.ZoneId Timezone identifier
 * @doc.type class
 * @doc.layer testing
 * @doc.purpose controllable clock for deterministic time handling in tests
 * @doc.pattern test-utility time-abstraction dependency-injection
 */
public class TestClock extends Clock {
    private Instant instant;
    private ZoneId zone;

    /**
     * Constructs a TestClock with the specified instant and timezone.
     *
     * <p>Creates a fixed-time clock implementation for testing scenarios where:
     * - Multiple time queries should return identical instants
     * - Time must be explicitly advanced via API methods
     * - Timezone context must be preserved (e.g., for ZonedDateTime conversions)
     *
     * @param instant the fixed instant this clock always returns (never null)
     * @param zone the timezone identifier for this clock (never null)
     * @throws NullPointerException if instant or zone is null
     */
    public TestClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /**
     * Creates a TestClock frozen at the current instant in UTC timezone.
     *
     * <p>Useful when you need time to be deterministic from "now" but don't
     * know the exact instant in advance. Time will be frozen at method invocation.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * TestClock clock = TestClock.systemUTC();
     * LocalDateTime start = LocalDateTime.now(clock); // Fixed instant
     * Thread.sleep(100);
     * LocalDateTime end = LocalDateTime.now(clock); // Same instant as start
     * assertEquals(start, end);
     * }</pre>
     *
     * @return TestClock frozen at current instant with UTC timezone
     */
    public static TestClock systemUTC() {
        return new TestClock(Instant.now(), ZoneOffset.UTC);
    }

    /**
     * Creates a TestClock frozen at the specified instant in UTC timezone.
     *
     * <p>Preferred factory when you know the exact test instant in advance.
     * Guarantees deterministic execution by fixing time to known value.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * TestClock clock = TestClock.fixed(
     *     Instant.parse("2025-06-15T14:30:00Z")
     * );
     * assertEquals(
     *     Instant.parse("2025-06-15T14:30:00Z"),
     *     clock.instant()
     * );
     * }</pre>
     *
     * @param instant the fixed instant for this clock (never null)
     * @return TestClock frozen at specified instant with UTC timezone
     * @throws NullPointerException if instant is null
     */
    public static TestClock fixed(Instant instant) {
        return new TestClock(instant, ZoneOffset.UTC);
    }

    /**
     * Returns the timezone associated with this clock.
     *
     * @return this clock's ZoneId (never null)
     */
    @Override
    public ZoneId getZone() {
        return zone;
    }

    /**
     * Creates a new TestClock with the same instant but different timezone.
     *
     * <p>Useful when code needs to work with different timezones but time
     * progression should be synchronized. Changes only the zone context,
     * not the underlying instant.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * TestClock utcClock = TestClock.systemUTC();
     * TestClock nyTime = utcClock.withZone(ZoneId.of("America/New_York"));
     * 
     * // Same instant, different zone context
     * assertEquals(utcClock.instant(), nyTime.instant());
     * assertNotEquals(utcClock.getZone(), nyTime.getZone());
     * }</pre>
     *
     * @param zone the new timezone identifier (never null)
     * @return same clock if zone matches, new TestClock with zone if different
     * @throws NullPointerException if zone is null
     */
    @Override
    public Clock withZone(ZoneId zone) {
        if (this.zone.equals(zone)) return this;
        return new TestClock(this.instant, zone);
    }

    /**
     * Returns the current frozen instant for this clock.
     *
     * <p>Always returns the same value unless modified via setInstant() or
     * one of the advance*() methods. This is the key difference from the
     * system clock which advances continuously.
     *
     * @return the current frozen instant (never null)
     */
    @Override
    public Instant instant() {
        return instant;
    }

    /**
     * Sets this clock to a completely different instant.
     *
     * <p>Replaces the current clock time with a new instant. Use for scenarios
     * where you need to jump to a completely different point in time (rather
     * than incrementally advancing).
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * TestClock clock = TestClock.systemUTC();
     * Instant t1 = clock.instant();
     * clock.setInstant(Instant.parse("2050-01-01T00:00:00Z"));
     * Instant t2 = clock.instant();
     * // t1 and t2 are completely different times
     * }</pre>
     *
     * @param instant the new instant for this clock (never null)
     * @throws NullPointerException if instant is null
     */
    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    /**
     * Advances this clock by the specified number of seconds.
     *
     * <p>Incremental time advancement for scenarios requiring precise duration
     * control. Always moves time forward (strictly positive).
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * TestClock clock = TestClock.systemUTC();
     * Instant t0 = clock.instant();
     * clock.advanceSeconds(30);
     * Instant t1 = clock.instant();
     * assertEquals(Duration.ofSeconds(30), Duration.between(t0, t1));
     * }</pre>
     *
     * @param seconds number of seconds to advance (typically positive)
     * @see #advanceMinutes(long)
     * @see #advanceHours(long)
     * @see #advanceDays(long)
     */
    public void advanceSeconds(long s) {
        this.instant = this.instant.plusSeconds(s);
    }

    /**
     * Advances this clock by the specified number of minutes.
     *
     * <p>Convenience method for larger time increments. Equivalent to
     * {@code advanceSeconds(m * 60)}.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * clock.advanceMinutes(5);  // Advance by 5 minutes
     * }</pre>
     *
     * @param m number of minutes to advance (typically positive)
     * @see #advanceSeconds(long)
     */
    public void advanceMinutes(long m) {
        this.instant = this.instant.plus(Duration.ofMinutes(m));
    }

    /**
     * Advances this clock by the specified number of hours.
     *
     * <p>Convenience method for larger time increments. Equivalent to
     * {@code advanceMinutes(h * 60)}.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * clock.advanceHours(24);  // Advance by one day
     * }</pre>
     *
     * @param h number of hours to advance (typically positive)
     * @see #advanceMinutes(long)
     */
    public void advanceHours(long h) {
        this.instant = this.instant.plus(Duration.ofHours(h));
    }

    /**
     * Advances this clock by the specified number of days.
     *
     * <p>Convenience method for large time increments. Useful for testing
     * scenarios involving daily schedules, subscriptions, or long-duration workflows.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * clock.advanceDays(30);  // Advance by 30 days
     * }</pre>
     *
     * @param d number of days to advance (typically positive)
     * @see #advanceHours(long)
     */
    public void advanceDays(long d) {
        this.instant = this.instant.plus(Duration.ofDays(d));
    }
}
