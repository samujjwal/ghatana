/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MemoryItemType} — extensible memory tier classification.
 *
 * @doc.type class
 * @doc.purpose Unit tests for MemoryItemType enum and custom type registry
 * @doc.layer agent-memory
 * @doc.pattern Test
 */
@DisplayName("MemoryItemType - extensible memory tier classification [GH-90000]")
class MemoryItemTypeTest {

    /**
     * Note: MemoryItemType.CUSTOM_TYPES is a static ConcurrentHashSet.
     * Tests that register custom types must be careful not to pollute other tests.
     * We check for specific names we registered rather than exact set equality.
     */

    @Nested
    @DisplayName("Built-in enum values [GH-90000]")
    class BuiltInValuesTests {

        @Test
        @DisplayName("All seven built-in types are present [GH-90000]")
        void allBuiltInTypes_present() { // GH-90000
            assertThat(MemoryItemType.values()).containsExactlyInAnyOrder( // GH-90000
                    MemoryItemType.EPISODE,
                    MemoryItemType.FACT,
                    MemoryItemType.PROCEDURE,
                    MemoryItemType.TASK_STATE,
                    MemoryItemType.WORKING,
                    MemoryItemType.PREFERENCE,
                    MemoryItemType.ARTIFACT,
                    MemoryItemType.CUSTOM);
        }

        @Test
        @DisplayName("CUSTOM_TYPE_LABEL_KEY is the expected constant string [GH-90000]")
        void customTypeLabelKey_isExpected() { // GH-90000
            assertThat(MemoryItemType.CUSTOM_TYPE_LABEL_KEY).isEqualTo("memory.custom.type [GH-90000]");
        }
    }

    @Nested
    @DisplayName("registerCustomType - happy path [GH-90000]")
    class RegisterCustomTypeTests {

        @Test
        @DisplayName("Registering a new custom type makes it discoverable [GH-90000]")
        void register_newType_isDiscoverable() { // GH-90000
            MemoryItemType.registerCustomType("TEST_CONVERSATION_UNIQUE_A1 [GH-90000]");
            assertThat(MemoryItemType.isCustomTypeRegistered("TEST_CONVERSATION_UNIQUE_A1 [GH-90000]"))
                    .isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Registration is case-insensitive: lower-case lookup finds upper-cased entry [GH-90000]")
        void register_caseInsensitive_lookup() { // GH-90000
            MemoryItemType.registerCustomType("TEST_INSIGHT_UNIQUE_B2 [GH-90000]");
            assertThat(MemoryItemType.isCustomTypeRegistered("test_insight_unique_b2 [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("Registering an existing type is idempotent (no exception) [GH-90000]")
        void register_existingType_isIdempotent() { // GH-90000
            MemoryItemType.registerCustomType("TEST_IDEMPOTENT_TYPE_C3 [GH-90000]");
            MemoryItemType.registerCustomType("TEST_IDEMPOTENT_TYPE_C3 [GH-90000]"); // second call
            assertThat(MemoryItemType.isCustomTypeRegistered("TEST_IDEMPOTENT_TYPE_C3 [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("registeredCustomTypes() returns a copy containing the registered name [GH-90000]")
        void registeredCustomTypes_containsRegisteredName() { // GH-90000
            MemoryItemType.registerCustomType("TEST_COPY_CONTAINS_D4 [GH-90000]");
            Set<String> types = MemoryItemType.registeredCustomTypes(); // GH-90000
            assertThat(types).contains("TEST_COPY_CONTAINS_D4 [GH-90000]");
        }

        @Test
        @DisplayName("registeredCustomTypes() returns an unmodifiable set (add throws) [GH-90000]")
        void registeredCustomTypes_isUnmodifiable() { // GH-90000
            Set<String> types = MemoryItemType.registeredCustomTypes(); // GH-90000
            assertThatThrownBy(() -> types.add("SHOULD_FAIL [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("registerCustomType - validation [GH-90000]")
    class RegisterCustomTypeValidationTests {

        @Test
        @DisplayName("Null name throws NullPointerException [GH-90000]")
        void registerNull_throwsNPE() { // GH-90000
            assertThatThrownBy(() -> MemoryItemType.registerCustomType(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("name [GH-90000]");
        }

        @Test
        @DisplayName("Blank name throws IllegalArgumentException [GH-90000]")
        void registerBlank_throwsIllegalArgument() { // GH-90000
            assertThatThrownBy(() -> MemoryItemType.registerCustomType("   [GH-90000]"))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("blank [GH-90000]");
        }

        @Test
        @DisplayName("Empty string name throws IllegalArgumentException [GH-90000]")
        void registerEmpty_throwsIllegalArgument() { // GH-90000
            assertThatThrownBy(() -> MemoryItemType.registerCustomType(" [GH-90000]"))
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("isCustomTypeRegistered - not registered [GH-90000]")
    class IsCustomTypeRegisteredTests {

        @Test
        @DisplayName("Unregistered type name returns false [GH-90000]")
        void unregisteredType_returnsFalse() { // GH-90000
            assertThat(MemoryItemType.isCustomTypeRegistered("DEFINITELY_NOT_REGISTERED_XYZ_999 [GH-90000]"))
                    .isFalse(); // GH-90000
        }
    }
}
