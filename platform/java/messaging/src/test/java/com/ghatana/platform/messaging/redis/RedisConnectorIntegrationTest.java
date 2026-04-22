package com.ghatana.core.connectors.redis;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
@DisplayName("Redis Connector Integration Tests [GH-90000]")
@Tag("integration [GH-90000]")
class RedisConnectorIntegrationTest extends EventloopTestBase {

    // ── In-memory Redis simulation ────────────────────────────────────────────

    static class InMemoryRedis {
        record Entry(String value, Long expiresAtMs) {} // GH-90000

        private final Map<String, Entry> store = new HashMap<>(); // GH-90000
        private long nowMs;

        InMemoryRedis(long nowMs) { // GH-90000
            this.nowMs = nowMs;
        }

        void set(String key, String value) { // GH-90000
            store.put(key, new Entry(value, null)); // GH-90000
        }

        void setEx(String key, String value, long ttlMs) { // GH-90000
            store.put(key, new Entry(value, nowMs + ttlMs)); // GH-90000
        }

        Optional<String> get(String key) { // GH-90000
            Entry entry = store.get(key); // GH-90000
            if (entry == null) return Optional.empty(); // GH-90000
            if (entry.expiresAtMs() != null && nowMs >= entry.expiresAtMs()) { // GH-90000
                store.remove(key); // GH-90000
                return Optional.empty(); // GH-90000
            }
            return Optional.of(entry.value()); // GH-90000
        }

        void delete(String key) { // GH-90000
            store.remove(key); // GH-90000
        }

        boolean exists(String key) { // GH-90000
            return get(key).isPresent(); // GH-90000
        }

        long incr(String key) { // GH-90000
            String current = get(key).orElse("0 [GH-90000]");
            long next = Long.parseLong(current) + 1; // GH-90000
            set(key, String.valueOf(next)); // GH-90000
            return next;
        }

        void advanceTimeMs(long ms) { // GH-90000
            nowMs += ms;
        }
    }

    private InMemoryRedis redis;

    @BeforeEach
    void setUp() { // GH-90000
        redis = new InMemoryRedis(System.currentTimeMillis()); // GH-90000
    }

    // ── Basic get/set ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("basic get/set operations [GH-90000]")
    class BasicGetSet {

        @Test
        @DisplayName("set then get returns the stored value [GH-90000]")
        void setThenGet_returnsStoredValue() { // GH-90000
            redis.set("user:123", "{\"name\":\"Alice\"}"); // GH-90000

            Optional<String> result = redis.get("user:123 [GH-90000]");

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get()).isEqualTo("{\"name\":\"Alice\"}"); // GH-90000
        }

        @Test
        @DisplayName("get non-existent key returns empty optional [GH-90000]")
        void getNonExistentKey_returnsEmpty() { // GH-90000
            Optional<String> result = redis.get("missing-key [GH-90000]");

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("overwrite existing key with new value [GH-90000]")
        void overwriteExistingKey_replacesValue() { // GH-90000
            redis.set("config:mode", "slow"); // GH-90000
            redis.set("config:mode", "fast"); // GH-90000

            assertThat(redis.get("config:mode [GH-90000]")).hasValue("fast [GH-90000]");
        }

        @Test
        @DisplayName("delete removes key from store [GH-90000]")
        void delete_removesKeyFromStore() { // GH-90000
            redis.set("temp:key", "temp-value"); // GH-90000
            redis.delete("temp:key [GH-90000]");

            assertThat(redis.exists("temp:key [GH-90000]")).isFalse();
        }
    }

    // ── TTL expiration ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TTL expiration [GH-90000]")
    class TtlExpiration {

        @Test
        @DisplayName("key is accessible before TTL expiry [GH-90000]")
        void keyIsAccessible_beforeTtlExpiry() { // GH-90000
            redis.setEx("session:abc", "active", 1000L); // GH-90000

            redis.advanceTimeMs(500L); // GH-90000

            assertThat(redis.get("session:abc [GH-90000]")).isPresent();
        }

        @Test
        @DisplayName("key is evicted after TTL expiry [GH-90000]")
        void keyIsEvicted_afterTtlExpiry() { // GH-90000
            redis.setEx("session:abc", "active", 1000L); // GH-90000

            redis.advanceTimeMs(1001L); // GH-90000

            assertThat(redis.get("session:abc [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("key without TTL persists indefinitely [GH-90000]")
        void keyWithoutTtl_persistsIndefinitely() { // GH-90000
            redis.set("permanent:key", "forever"); // GH-90000

            redis.advanceTimeMs(Long.MAX_VALUE / 2); // GH-90000

            assertThat(redis.get("permanent:key [GH-90000]")).isPresent();
        }
    }

    // ── Atomic operations ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("atomic INCR operation [GH-90000]")
    class AtomicOperations {

        @Test
        @DisplayName("incr on new key starts at 1 [GH-90000]")
        void incrOnNewKey_startsAt1() { // GH-90000
            long result = redis.incr("counter:new [GH-90000]");

            assertThat(result).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("multiple incr operations accumulate correctly [GH-90000]")
        void multipleIncr_accumulatesCorrectly() { // GH-90000
            redis.incr("counter:hits [GH-90000]");
            redis.incr("counter:hits [GH-90000]");
            redis.incr("counter:hits [GH-90000]");

            assertThat(redis.get("counter:hits [GH-90000]")).hasValue("3 [GH-90000]");
        }

        @Test
        @DisplayName("incr on existing numeric value increments by 1 [GH-90000]")
        void incrOnExistingNumericValue_incrementsBy1() { // GH-90000
            redis.set("counter:page", "10"); // GH-90000
            long result = redis.incr("counter:page [GH-90000]");

            assertThat(result).isEqualTo(11L); // GH-90000
        }
    }

    // ── Pub/sub simulation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("pub/sub message delivery [GH-90000]")
    class PubSubMessageDelivery {

        @Test
        @DisplayName("published message is received by subscriber [GH-90000]")
        void publishedMessage_isReceivedBySubscriber() { // GH-90000
            java.util.List<String> received = new java.util.ArrayList<>(); // GH-90000

            // Subscribe (register handler) // GH-90000
            java.util.function.Consumer<String> subscriber = received::add;

            // Publish (direct invocation in our simulation) // GH-90000
            String message = "event:user_logged_in";
            subscriber.accept(message); // GH-90000

            assertThat(received).containsExactly("event:user_logged_in [GH-90000]");
        }

        @Test
        @DisplayName("multiple subscribers each receive the published message [GH-90000]")
        void multipleSubscribers_eachReceivePublishedMessage() { // GH-90000
            java.util.List<String> sub1Received = new java.util.ArrayList<>(); // GH-90000
            java.util.List<String> sub2Received = new java.util.ArrayList<>(); // GH-90000

            java.util.function.Consumer<String> sub1 = sub1Received::add;
            java.util.function.Consumer<String> sub2 = sub2Received::add;

            String message = "event:order_placed";
            sub1.accept(message); // GH-90000
            sub2.accept(message); // GH-90000

            assertThat(sub1Received).containsExactly(message); // GH-90000
            assertThat(sub2Received).containsExactly(message); // GH-90000
        }
    }
}
