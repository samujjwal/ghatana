/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("MemoryItemType - extensible memory tier classification")
class MemoryItemTypeTest {

    /**
     * Note: MemoryItemType.CUSTOM_TYPES is a static ConcurrentHashSet.
     * Tests that register custom types must be careful not to pollute other tests.
     * We check for specific names we registered rather than exact set equality.
     */

    @Nested
    @DisplayName("Built-in enum values")
    class BuiltInValuesTests {

        @Test
        @DisplayName("All eight built-in types are present")
        void allBuiltInTypes_present() { 
            assertThat(MemoryItemType.values()).containsExactlyInAnyOrder( 
                    MemoryItemType.EPISODE,
                    MemoryItemType.FACT,
                    MemoryItemType.PROCEDURE,
                    MemoryItemType.NEGATIVE_KNOWLEDGE,
                    MemoryItemType.TASK_STATE,
                    MemoryItemType.WORKING,
                    MemoryItemType.PREFERENCE,
                    MemoryItemType.ARTIFACT,
                    MemoryItemType.CUSTOM);
        }

        @Test
        @DisplayName("CUSTOM_TYPE_LABEL_KEY is the expected constant string")
        void customTypeLabelKey_isExpected() { 
            assertThat(MemoryItemType.CUSTOM_TYPE_LABEL_KEY).isEqualTo("memory.custom.type");
        }
    }

    @Nested
    @DisplayName("registerCustomType - happy path")
    class RegisterCustomTypeTests {

        @Test
        @DisplayName("Registering a new custom type makes it discoverable")
        void register_newType_isDiscoverable() { 
            MemoryItemType.registerCustomType("TEST_CONVERSATION_UNIQUE_A1");
            assertThat(MemoryItemType.isCustomTypeRegistered("TEST_CONVERSATION_UNIQUE_A1"))
                    .isTrue(); 
        }

        @Test
        @DisplayName("Registration is case-insensitive: lower-case lookup finds upper-cased entry")
        void register_caseInsensitive_lookup() { 
            MemoryItemType.registerCustomType("TEST_INSIGHT_UNIQUE_B2");
            assertThat(MemoryItemType.isCustomTypeRegistered("test_insight_unique_b2")).isTrue();
        }

        @Test
        @DisplayName("Registering an existing type is idempotent (no exception)")
        void register_existingType_isIdempotent() { 
            MemoryItemType.registerCustomType("TEST_IDEMPOTENT_TYPE_C3");
            MemoryItemType.registerCustomType("TEST_IDEMPOTENT_TYPE_C3"); // second call
            assertThat(MemoryItemType.isCustomTypeRegistered("TEST_IDEMPOTENT_TYPE_C3")).isTrue();
        }

        @Test
        @DisplayName("registeredCustomTypes() returns a copy containing the registered name")
        void registeredCustomTypes_containsRegisteredName() { 
            MemoryItemType.registerCustomType("TEST_COPY_CONTAINS_D4");
            Set<String> types = MemoryItemType.registeredCustomTypes(); 
            assertThat(types).contains("TEST_COPY_CONTAINS_D4");
        }

        @Test
        @DisplayName("registeredCustomTypes() returns an unmodifiable set (add throws)")
        void registeredCustomTypes_isUnmodifiable() { 
            Set<String> types = MemoryItemType.registeredCustomTypes(); 
            assertThatThrownBy(() -> types.add("SHOULD_FAIL"))
                    .isInstanceOf(UnsupportedOperationException.class); 
        }
    }

    @Nested
    @DisplayName("registerCustomType - validation")
    class RegisterCustomTypeValidationTests {

        @Test
        @DisplayName("Null name throws NullPointerException")
        void registerNull_throwsNPE() { 
            assertThatThrownBy(() -> MemoryItemType.registerCustomType(null)) 
                    .isInstanceOf(NullPointerException.class) 
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("Blank name throws IllegalArgumentException")
        void registerBlank_throwsIllegalArgument() { 
            assertThatThrownBy(() -> MemoryItemType.registerCustomType("  "))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("Empty string name throws IllegalArgumentException")
        void registerEmpty_throwsIllegalArgument() { 
            assertThatThrownBy(() -> MemoryItemType.registerCustomType(""))
                    .isInstanceOf(IllegalArgumentException.class); 
        }
    }

    @Nested
    @DisplayName("isCustomTypeRegistered - not registered")
    class IsCustomTypeRegisteredTests {

        @Test
        @DisplayName("Unregistered type name returns false")
        void unregisteredType_returnsFalse() { 
            assertThat(MemoryItemType.isCustomTypeRegistered("DEFINITELY_NOT_REGISTERED_XYZ_999"))
                    .isFalse(); 
        }
    }
}
