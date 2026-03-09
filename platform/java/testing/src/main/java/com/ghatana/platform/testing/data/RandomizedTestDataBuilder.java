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
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Abstract base class for test data builders with randomization support.
 *
 * <p>Extends {@link TestDataBuilder} with capabilities for generating randomized test data using
 * Faker library. Useful for property-based testing and generating diverse test datasets.
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * public class AgentTestData extends RandomizedTestDataBuilder<Agent> {
 *     private AgentId id = AgentId.random();
 *     private String name = "test-agent";
 *     private AgentStatus status = AgentStatus.ACTIVE;
 *
 *     public AgentTestData withId(AgentId id) {
 *         this.id = id;
 *         return this;
 *     }
 *
 *     public AgentTestData withName(String name) {
 *         this.name = name;
 *         return this;
 *     }
 *
 *     @Override
 *     public Agent build() {
 *         return Agent.builder()
 *             .id(id)
 *             .name(name)
 *             .status(status)
 *             .build();
 *     }
 *
 *     @Override
 *     public Agent buildRandom() {
 *         return Agent.builder()
 *             .id(AgentId.random())
 *             .name(faker.name().fullName())
 *             .status(faker.options().option(AgentStatus.class))
 *             .build();
 *     }
 *
 *     public static AgentTestData anAgent() {
 *         return new AgentTestData();
 *     }
 * }
 *
 * // Usage in tests:
 * Agent agent = AgentTestData.anAgent().buildRandom();
 * List<Agent> agents = AgentTestData.anAgent().buildRandomList(10);
 * }</pre>
 *
 * @param <T> the type of object this builder creates
 
 *
 * @doc.type abstract-class
 * @doc.purpose Randomized test data builder
 * @doc.layer core
 * @doc.pattern Builder
*/
public abstract class RandomizedTestDataBuilder<T> implements TestDataBuilder<T> {

    /** Faker instance for generating random data. */
    protected final Faker faker;

    /** Random instance for generating random numbers. */
    protected final Random random;

    /** Creates a new randomized builder with default Faker and Random instances. */
    protected RandomizedTestDataBuilder() {
        this(new Faker(), new Random());
    }

    /**
     * Creates a new randomized builder with specified Faker and Random instances.
     *
     * @param faker the Faker instance to use for data generation
     * @param random the Random instance to use for randomization
     */
    protected RandomizedTestDataBuilder(Faker faker, Random random) {
        this.faker = faker;
        this.random = random;
    }

    /**
     * Builds a single instance with randomized data.
     *
     * <p>Override this method to provide randomization logic for your test data objects.
     *
     * @return a new instance with randomized properties
     */
    public abstract T buildRandom();

    /**
     * Builds a list of instances with randomized data.
     *
     * @param count the number of objects to create
     * @return a list of randomized test data objects
     */
    public List<T> buildRandomList(int count) {
        return IntStream.range(0, count).mapToObj(i -> buildRandom()).collect(Collectors.toList());
    }

    /**
     * Builds an array of instances with randomized data.
     *
     * @param count the number of objects to create
     * @param arraySupplier a function to create an array of the appropriate type
     * @return an array of randomized test data objects
     */
    @SuppressWarnings("unchecked")
    public T[] buildRandomArray(int count, java.util.function.IntFunction<T[]> arraySupplier) {
        return buildRandomList(count).toArray(arraySupplier);
    }

    /**
     * Returns a random integer between min (inclusive) and max (inclusive).
     *
     * @param min the minimum value
     * @param max the maximum value
     * @return a random integer
     */
    protected int randomInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    /**
     * Returns a random long.
     *
     * @return a random long value
     */
    protected long randomLong() {
        return random.nextLong();
    }

    /**
     * Returns a random boolean.
     *
     * @return a random boolean value
     */
    protected boolean randomBoolean() {
        return random.nextBoolean();
    }
}
