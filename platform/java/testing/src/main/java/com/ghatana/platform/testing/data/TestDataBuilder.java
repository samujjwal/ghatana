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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Base interface for all test data builders.
 *
 * <p>Provides a fluent API for building test data objects with sensible defaults and customizable
 * properties.
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * public class UserTestData implements TestDataBuilder<User> {
 *     private String name = "default-user";
 *     private String email = "user@example.com";
 *
 *     public UserTestData withName(String name) {
 *         this.name = name;
 *         return this;
 *     }
 *
 *     public UserTestData withEmail(String email) {
 *         this.email = email;
 *         return this;
 *     }
 *
 *     @Override
 *     public User build() {
 *         return new User(name, email);
 *     }
 *
 *     // Factory method for fluent API
 *     public static UserTestData aUser() {
 *         return new UserTestData();
 *     }
 * }
 *
 * // Usage in tests:
 * User user = UserTestData.aUser()
 *     .withName("John Doe")
 *     .withEmail("john@example.com")
 *     .build();
 *
 * List<User> users = UserTestData.aUser().buildList(5);
 * }</pre>
 *
 * @param <T> the type of object this builder creates
 
 *
 * @doc.type interface
 * @doc.purpose Test data builder
 * @doc.layer core
 * @doc.pattern Builder
*/
public interface TestDataBuilder<T> {

    /**
     * Builds a single instance of the test data object.
     *
     * @return a new instance of the test data object with configured properties
     */
    T build();

    /**
     * Builds a list of test data objects with the same configuration.
     *
     * @param count the number of objects to create
     * @return a list of test data objects
     */
    default List<T> buildList(int count) {
        return IntStream.range(0, count).mapToObj(i -> build()).collect(Collectors.toList());
    }

    /**
     * Builds an array of test data objects with the same configuration.
     *
     * @param count the number of objects to create
     * @param arraySupplier a function to create an array of the appropriate type
     * @return an array of test data objects
     */
    @SuppressWarnings("unchecked")
    default T[] buildArray(int count, java.util.function.IntFunction<T[]> arraySupplier) {
        return buildList(count).toArray(arraySupplier);
    }
}
