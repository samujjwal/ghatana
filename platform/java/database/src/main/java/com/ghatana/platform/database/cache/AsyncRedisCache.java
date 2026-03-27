package com.ghatana.platform.database.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.promise.SettablePromise;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Asynchronous Redis cache adapter with JSON serialization and ActiveJ Promise API.
 *
 * <p>Provides a canonical Redis-backed async cache implementation for shared platform callers.
 * This is the canonical home for the adapter previously stranded under the legacy
 * `com.ghatana.core.cache.redis` namespace.
 *
 * @param <T> value type stored in the cache
 * @doc.type class
 * @doc.purpose Canonical asynchronous Redis cache adapter with JSON serialization
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public final class AsyncRedisCache<T> implements AutoCloseable {

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> commands;
    private final ObjectMapper objectMapper;
    private final JavaType valueType;
    private final RedisCacheConfig config;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong lookupCount = new AtomicLong();
    private final AtomicLong totalLookupTimeNanos = new AtomicLong();

    public static <T> AsyncRedisCache<T> create(RedisCacheConfig config, ObjectMapper mapper, Class<T> valueClass) {
        return new AsyncRedisCache<>(config, mapper, mapper.getTypeFactory().constructType(valueClass));
    }

    public AsyncRedisCache(RedisCacheConfig config, ObjectMapper objectMapper, JavaType valueType) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.valueType = valueType;

        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(config.getHost())
                .withPort(config.getPort())
                .withDatabase(config.getDatabase());

        Duration timeout = config.getTimeout();
        if (timeout != null) {
            uriBuilder.withTimeout(timeout);
        }

        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            uriBuilder.withPassword(config.getPassword());
        }

        this.client = RedisClient.create(uriBuilder.build());
        this.connection = client.connect();
        this.commands = connection.async();
    }

    public Promise<Optional<T>> get(String key) {
        long start = System.nanoTime();
        RedisFuture<String> future = commands.get(key);
        return toPromise(future)
                .map(json -> {
                    recordLookup(System.nanoTime() - start);
                    if (json == null) {
                        misses.incrementAndGet();
                        return Optional.empty();
                    }
                    try {
                        T value = objectMapper.readValue(json, valueType);
                        hits.incrementAndGet();
                        return Optional.of(value);
                    } catch (IOException exception) {
                        misses.incrementAndGet();
                        throw new CompletionException(exception);
                    }
                });
    }

    public Promise<Map<String, T>> getAll(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Promise.of(Collections.emptyMap());
        }
        long start = System.nanoTime();
        RedisFuture<List<KeyValue<String, String>>> future = commands.mget(keys.toArray(new String[0]));
        return toPromise(future)
                .map(entries -> {
                    long elapsed = System.nanoTime() - start;
                    totalLookupTimeNanos.addAndGet(elapsed);
                    lookupCount.addAndGet(keys.size());

                    Map<String, T> result = new HashMap<>();
                    for (KeyValue<String, String> entry : entries) {
                        if (entry == null) {
                            misses.incrementAndGet();
                            continue;
                        }
                        String redisKey = entry.getKey();
                        if (!entry.hasValue()) {
                            misses.incrementAndGet();
                            continue;
                        }
                        try {
                            T value = objectMapper.readValue(entry.getValue(), valueType);
                            hits.incrementAndGet();
                            result.put(redisKey, value);
                        } catch (IOException exception) {
                            misses.incrementAndGet();
                            throw new CompletionException(exception);
                        }
                    }
                    return result;
                });
    }

    public Promise<Void> put(String key, T value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            RedisFuture<String> future;
            if (config.getTtlSeconds() > 0) {
                future = commands.setex(key, config.getTtlSeconds(), json);
            } else {
                future = commands.set(key, json);
            }
            return toPromise(future).map(ignore -> null);
        } catch (JsonProcessingException exception) {
            throw new CompletionException(exception);
        }
    }

    public Promise<Void> putAll(Map<String, T> values) {
        if (values == null || values.isEmpty()) {
            return Promise.of(null);
        }
        List<Promise<Void>> promises = values.entrySet().stream()
                .map(entry -> put(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        return Promises.all(promises).toVoid();
    }

    public Promise<Boolean> remove(String key) {
        RedisFuture<Long> future = commands.del(key);
        return toPromise(future).map(count -> count != null && count > 0);
    }

    public Promise<Long> removeAll(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Promise.of(0L);
        }
        RedisFuture<Long> future = commands.del(keys.toArray(new String[0]));
        return toPromise(future).map(count -> count != null ? count : 0L);
    }

    public Promise<Boolean> exists(String key) {
        RedisFuture<Long> future = commands.exists(key);
        return toPromise(future).map(count -> count != null && count > 0);
    }

    public Promise<Void> clearWithPrefix(String prefix) {
        String pattern = prefix.endsWith("*") ? prefix : prefix + "*";
        return scanKeys(pattern)
                .then(keys -> {
                    if (keys.isEmpty()) {
                        return Promise.of(null);
                    }
                    return removeAll(keys).map(ignore -> null);
                });
    }

    public Promise<Long> countWithPrefix(String prefix) {
        String pattern = prefix.endsWith("*") ? prefix : prefix + "*";
        return scanKeys(pattern).map(keys -> (long) keys.size());
    }

    public CacheStats snapshotStats() {
        long hitCount = hits.get();
        long missCount = misses.get();
        long lookups = lookupCount.get();
        long totalTime = totalLookupTimeNanos.get();

        double total = hitCount + missCount;
        double hitRate = total > 0 ? hitCount / total : 0.0;
        double avgTime = lookups > 0 ? (double) totalTime / lookups : 0.0;
        return new CacheStats(hitCount, missCount, lookups, hitRate, avgTime);
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
    }

    private Promise<List<String>> scanKeys(String pattern) {
        SettablePromise<List<String>> result = new SettablePromise<>();
        List<String> keys = new ArrayList<>();
        scan(pattern, ScanCursor.INITIAL, keys, result);
        return result;
    }

    private void scan(String pattern, ScanCursor cursor, List<String> accumulator, SettablePromise<List<String>> target) {
        ScanArgs args = ScanArgs.Builder.matches(pattern).limit(500);
        toPromise(commands.scan(cursor, args))
                .whenComplete((scanResult, throwable) -> {
                    if (throwable != null) {
                        target.setException(throwable);
                        return;
                    }
                    accumulator.addAll(scanResult.getKeys());
                    if (scanResult.isFinished()) {
                        target.set(accumulator);
                    } else {
                        scan(pattern, scanResult, accumulator, target);
                    }
                });
    }

    private void recordLookup(long durationNanos) {
        lookupCount.incrementAndGet();
        totalLookupTimeNanos.addAndGet(durationNanos);
    }

    private static <T> Promise<T> toPromise(RedisFuture<T> future) {
        SettablePromise<T> promise = new SettablePromise<>();
        future.whenComplete((value, throwable) -> {
            if (throwable != null) {
                promise.setException(throwable instanceof Exception exception ? exception : new RuntimeException(throwable));
            } else {
                promise.set(value);
            }
        });
        return promise;
    }

    public record CacheStats(
            long hits,
            long misses,
            long lookups,
            double hitRate,
            double averageLookupTimeNanos) {
    }
}