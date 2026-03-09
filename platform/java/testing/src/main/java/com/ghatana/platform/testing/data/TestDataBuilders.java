/*
 * Ghatana Platform
 * Copyright (c) 2025 Ghatana
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.platform.testing.data;

import net.datafaker.Faker;
import java.time.Instant;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Centralized utilities for creating common test data patterns.
 *
 * <p>Provides factory methods and common patterns used across multiple test data builders to reduce
 * duplication and ensure consistency.
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * // Generate random IDs
 * String id = TestDataBuilders.randomId();
 * String tenantId = TestDataBuilders.randomTenantId();
 *
 * // Generate timestamps
 * Instant now = TestDataBuilders.now();
 * Instant pastTime = TestDataBuilders.pastTime(Duration.ofDays(7));
 *
 * // Generate common maps
 * Map<String, Object> payload = TestDataBuilders.simplePayload("action", "test");
 * Map<String, String> headers = TestDataBuilders.simpleHeaders();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized test data factory for IDs, timestamps, payloads, and headers
 * @doc.layer platform
 * @doc.pattern Factory
 */
public final class TestDataBuilders {

    private static final Faker faker = new Faker();

    private TestDataBuilders() {
        // Utility class
    }

    // ==================== ID Generation ====================

    /**
     * Generates a random UUID string.
     *
     * @return a random UUID string
     */
    public static String randomId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a random short ID (8 characters).
     *
     * @return a random short ID
     */
    public static String randomShortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generates a random tenant ID.
     *
     * @return a tenant ID in format "tenant-XXXX"
     */
    public static String randomTenantId() {
        return "tenant-" + randomShortId();
    }

    /**
     * Generates a random agent ID.
     *
     * @return an agent ID in format "agent-XXXX"
     */
    public static String randomAgentId() {
        return "agent-" + randomShortId();
    }

    /**
     * Generates a random event ID.
     *
     * @return an event ID in format "event-XXXX"
     */
    public static String randomEventId() {
        return "event-" + randomShortId();
    }

    // ==================== Time Generation ====================

    /**
     * Returns the current time.
     *
     * @return current Instant
     */
    public static Instant now() {
        return Instant.now();
    }

    /**
     * Returns a time in the past.
     *
     * @param duration how far back in time
     * @return Instant in the past
     */
    public static Instant pastTime(Duration duration) {
        return Instant.now().minus(duration);
    }

    /**
     * Returns a time in the future.
     *
     * @param duration how far forward in time
     * @return Instant in the future
     */
    public static Instant futureTime(Duration duration) {
        return Instant.now().plus(duration);
    }

    // ==================== Map Builders ====================

    /**
     * Creates a simple payload map with a single key-value pair.
     *
     * @param key the key
     * @param value the value
     * @return a mutable map with the single entry
     */
    public static Map<String, Object> simplePayload(String key, Object value) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(key, value);
        return payload;
    }

    /**
     * Creates a simple payload map with test data.
     *
     * @return a mutable map with common test fields
     */
    public static Map<String, Object> simplePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("testData", true);
        payload.put("timestamp", now().toString());
        payload.put("id", randomShortId());
        return payload;
    }

    /**
     * Creates a simple headers map with common test headers.
     *
     * @return a mutable map with common headers
     */
    public static Map<String, String> simpleHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("source", "test");
        headers.put("version", "1.0");
        headers.put("correlationId", randomId());
        return headers;
    }

    // ==================== String Generation ====================

    /**
     * Generates a random name using Faker.
     *
     * @return a random full name
     */
    public static String randomName() {
        return faker.name().fullName();
    }

    /**
     * Generates a random email using Faker.
     *
     * @return a random email address
     */
    public static String randomEmail() {
        return faker.internet().emailAddress();
    }

    /**
     * Generates a random word using Faker.
     *
     * @return a random word
     */
    public static String randomWord() {
        return faker.lorem().word();
    }

    /**
     * Generates a random sentence using Faker.
     *
     * @return a random sentence
     */
    public static String randomSentence() {
        return faker.lorem().sentence();
    }

    // ==================== List Generation ====================

    /**
     * Creates a list of random strings.
     *
     * @param count the number of strings to generate
     * @return a list of random strings
     */
    public static List<String> randomStrings(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> randomWord())
                .collect(Collectors.toList());
    }

    /**
     * Creates a list of random IDs.
     *
     * @param count the number of IDs to generate
     * @return a list of random IDs
     */
    public static List<String> randomIds(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> randomId())
                .collect(Collectors.toList());
    }

    // ==================== Common Patterns ====================

    /**
     * Creates a list of tags for testing.
     *
     * @return a mutable list of test tags
     */
    public static List<String> defaultTags() {
        List<String> tags = new ArrayList<>();
        tags.add("test");
        tags.add("generated");
        tags.add("automation");
        return tags;
    }

    /**
     * Creates a version string.
     *
     * @param major major version
     * @param minor minor version
     * @param patch patch version
     * @return version string in format "major.minor.patch"
     */
    public static String version(int major, int minor, int patch) {
        return String.format("%d.%d.%d", major, minor, patch);
    }

    /**
     * Creates a default version string "1.0.0".
     *
     * @return "1.0.0"
     */
    public static String defaultVersion() {
        return version(1, 0, 0);
    }
}
