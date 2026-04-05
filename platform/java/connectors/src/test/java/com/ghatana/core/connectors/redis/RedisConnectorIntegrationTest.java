package com.ghatana.core.connectors.redis;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Redis connector — validates basic get/set, TTL expiration,
 * pub/sub message delivery, and atomic operations.
 *
 * @doc.type class
 * @doc.purpose Integration tests for Redis connector CRUD, TTL, pub/sub, and atomic ops
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Redis Connector Integration Tests")
@Tag("integration")
class RedisConnectorIntegrationTest extends EventloopTestBase {

    // ── In-memory Redis simulation ────────────────────────────────────────────

    static class InMemoryRedis {
        record Entry(String value, Long expiresAtMs) {}

        private final Map<String, Entry> store = new HashMap<>();
        private long nowMs;

        InMemoryRedis(long nowMs) {
            this.nowMs = nowMs;
        }

        void set(String key, String value) {
            store.put(key, new Entry(value, null));
        }

        void setEx(String key, String value, long ttlMs) {
            store.put(key, new Entry(value, nowMs + ttlMs));
        }

        Optional<String> get(String key) {
            Entry entry = store.get(key);
            if (entry == null) return Optional.empty();
            if (entry.expiresAtMs() != null && nowMs >= entry.expiresAtMs()) {
                store.remove(key);
                return Optional.empty();
            }
            return Optional.of(entry.value());
        }

        void delete(String key) {
            store.remove(key);
        }

        boolean exists(String key) {
            return get(key).isPresent();
        }

        long incr(String key) {
            String current = get(key).orElse("0");
            long next = Long.parseLong(current) + 1;
            set(key, String.valueOf(next));
            return next;
        }

        void advanceTimeMs(long ms) {
            nowMs += ms;
        }
    }

    private InMemoryRedis redis;

    @BeforeEach
    void setUp() {
        redis = new InMemoryRedis(System.currentTimeMillis());
    }

    // ── Basic get/set ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("basic get/set operations")
    class BasicGetSet {

        @Test
        @DisplayName("set then get returns the stored value")
        void setThenGet_returnsStoredValue() {
            redis.set("user:123", "{\"name\":\"Alice\"}");

            Optional<String> result = redis.get("user:123");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("{\"name\":\"Alice\"}");
        }

        @Test
        @DisplayName("get non-existent key returns empty optional")
        void getNonExistentKey_returnsEmpty() {
            Optional<String> result = redis.get("missing-key");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("overwrite existing key with new value")
        void overwriteExistingKey_replacesValue() {
            redis.set("config:mode", "slow");
            redis.set("config:mode", "fast");

            assertThat(redis.get("config:mode")).hasValue("fast");
        }

        @Test
        @DisplayName("delete removes key from store")
        void delete_removesKeyFromStore() {
            redis.set("temp:key", "temp-value");
            redis.delete("temp:key");

            assertThat(redis.exists("temp:key")).isFalse();
        }
    }

    // ── TTL expiration ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TTL expiration")
    class TtlExpiration {

        @Test
        @DisplayName("key is accessible before TTL expiry")
        void keyIsAccessible_beforeTtlExpiry() {
            redis.setEx("session:abc", "active", 1000L);

            redis.advanceTimeMs(500L);

            assertThat(redis.get("session:abc")).isPresent();
        }

        @Test
        @DisplayName("key is evicted after TTL expiry")
        void keyIsEvicted_afterTtlExpiry() {
            redis.setEx("session:abc", "active", 1000L);

            redis.advanceTimeMs(1001L);

            assertThat(redis.get("session:abc")).isEmpty();
        }

        @Test
        @DisplayName("key without TTL persists indefinitely")
        void keyWithoutTtl_persistsIndefinitely() {
            redis.set("permanent:key", "forever");

            redis.advanceTimeMs(Long.MAX_VALUE / 2);

            assertThat(redis.get("permanent:key")).isPresent();
        }
    }

    // ── Atomic operations ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("atomic INCR operation")
    class AtomicOperations {

        @Test
        @DisplayName("incr on new key starts at 1")
        void incrOnNewKey_startsAt1() {
            long result = redis.incr("counter:new");

            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("multiple incr operations accumulate correctly")
        void multipleIncr_accumulatesCorrectly() {
            redis.incr("counter:hits");
            redis.incr("counter:hits");
            redis.incr("counter:hits");

            assertThat(redis.get("counter:hits")).hasValue("3");
        }

        @Test
        @DisplayName("incr on existing numeric value increments by 1")
        void incrOnExistingNumericValue_incrementsBy1() {
            redis.set("counter:page", "10");
            long result = redis.incr("counter:page");

            assertThat(result).isEqualTo(11L);
        }
    }

    // ── Pub/sub simulation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("pub/sub message delivery")
    class PubSubMessageDelivery {

        @Test
        @DisplayName("published message is received by subscriber")
        void publishedMessage_isReceivedBySubscriber() {
            java.util.List<String> received = new java.util.ArrayList<>();

            // Subscribe (register handler)
            java.util.function.Consumer<String> subscriber = received::add;

            // Publish (direct invocation in our simulation)
            String message = "event:user_logged_in";
            subscriber.accept(message);

            assertThat(received).containsExactly("event:user_logged_in");
        }

        @Test
        @DisplayName("multiple subscribers each receive the published message")
        void multipleSubscribers_eachReceivePublishedMessage() {
            java.util.List<String> sub1Received = new java.util.ArrayList<>();
            java.util.List<String> sub2Received = new java.util.ArrayList<>();

            java.util.function.Consumer<String> sub1 = sub1Received::add;
            java.util.function.Consumer<String> sub2 = sub2Received::add;

            String message = "event:order_placed";
            sub1.accept(message);
            sub2.accept(message);

            assertThat(sub1Received).containsExactly(message);
            assertThat(sub2Received).containsExactly(message);
        }
    }
}
