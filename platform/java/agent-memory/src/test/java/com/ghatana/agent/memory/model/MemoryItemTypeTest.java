package com.ghatana.agent.memory.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MemoryItemType} enum and custom type registry.
 *
 * @doc.type class
 * @doc.purpose MemoryItemType registry tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("MemoryItemType")
class MemoryItemTypeTest {

    @AfterEach
    void cleanUp() {
        // Clean up custom types registered during tests to avoid test pollution
        // Note: MemoryItemType uses static state — tests must be careful
    }

    @Nested
    @DisplayName("built-in types")
    class BuiltInTypes {

        @Test
        @DisplayName("should have all standard memory types")
        void shouldHaveStandardTypes() {
            assertThat(MemoryItemType.values())
                    .extracting(MemoryItemType::name)
                    .contains("EPISODE", "FACT", "PROCEDURE", "TASK_STATE", "WORKING", "PREFERENCE");
        }

        @Test
        @DisplayName("should include ARTIFACT and CUSTOM types")
        void shouldHaveExtensionTypes() {
            assertThat(MemoryItemType.valueOf("ARTIFACT")).isNotNull();
            assertThat(MemoryItemType.valueOf("CUSTOM")).isNotNull();
        }
    }

    @Nested
    @DisplayName("custom type registry")
    class CustomTypeRegistry {

        @Test
        @DisplayName("should register and detect custom type")
        void shouldRegisterCustomType() {
            MemoryItemType.registerCustomType("KNOWLEDGE_GRAPH");

            assertThat(MemoryItemType.isCustomTypeRegistered("KNOWLEDGE_GRAPH")).isTrue();
        }

        @Test
        @DisplayName("should return false for unregistered type")
        void shouldReturnFalseForUnregistered() {
            assertThat(MemoryItemType.isCustomTypeRegistered("NONEXISTENT_TYPE_" + System.nanoTime()))
                    .isFalse();
        }

        @Test
        @DisplayName("should list all registered custom types")
        void shouldListCustomTypes() {
            String uniqueType = "TEST_TYPE_" + System.nanoTime();
            MemoryItemType.registerCustomType(uniqueType);

            assertThat(MemoryItemType.registeredCustomTypes()).contains(uniqueType);
        }

        @Test
        @DisplayName("should handle duplicate registration gracefully")
        void shouldHandleDuplicateRegistration() {
            String type = "DEDUP_TYPE_" + System.nanoTime();
            MemoryItemType.registerCustomType(type);
            MemoryItemType.registerCustomType(type); // should not throw

            assertThat(MemoryItemType.isCustomTypeRegistered(type)).isTrue();
        }
    }
}
