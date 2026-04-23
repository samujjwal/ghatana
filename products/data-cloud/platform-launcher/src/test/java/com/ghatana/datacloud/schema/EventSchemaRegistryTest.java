/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Task 5.3 — Comprehensive tests for EventSchemaRegistry.
 */
package com.ghatana.datacloud.schema;

import com.ghatana.datacloud.schema.EventSchema.SchemaField;
import com.ghatana.datacloud.schema.EventSchemaRegistry.SchemaRegistrationException;
import com.ghatana.datacloud.schema.SchemaCompatibilityChecker.CompatibilityResult;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EventSchemaRegistry}.
 *
 * <p>Covers: registration, versioning, all 4 compatibility modes,
 * subject management, edge cases, and concurrent access.</p>
 */
class EventSchemaRegistryTest {

    private EventSchemaRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = EventSchemaRegistry.create(); // GH-90000
    }

    // ─────────────────── Helper factories ───────────────────

    private EventSchema orderSchemaV1() { // GH-90000
        return EventSchema.create("order.created", SchemaFormat.JSON_SCHEMA, // GH-90000
                "{\"type\":\"object\"}", List.of( // GH-90000
                        SchemaField.required("orderId", "string"), // GH-90000
                        SchemaField.required("amount", "number"))); // GH-90000
    }

    private EventSchema orderSchemaV2WithOptionalField() { // GH-90000
        return orderSchemaV1().nextVersion("{\"type\":\"object\",\"v\":2}", List.of( // GH-90000
                SchemaField.required("orderId", "string"), // GH-90000
                SchemaField.required("amount", "number"), // GH-90000
                SchemaField.optional("priority", "integer"))); // GH-90000
    }

    private EventSchema orderSchemaV2WithRequiredField() { // GH-90000
        return orderSchemaV1().nextVersion("{\"type\":\"object\",\"v\":2}", List.of( // GH-90000
                SchemaField.required("orderId", "string"), // GH-90000
                SchemaField.required("amount", "number"), // GH-90000
                SchemaField.required("region", "string"))); // GH-90000
    }

    private EventSchema orderSchemaV2WithRequiredFieldAndDefault() { // GH-90000
        return orderSchemaV1().nextVersion("{\"type\":\"object\",\"v\":2}", List.of( // GH-90000
                SchemaField.required("orderId", "string"), // GH-90000
                SchemaField.required("amount", "number"), // GH-90000
                SchemaField.required("region", "string", "US"))); // GH-90000
    }

    private EventSchema orderSchemaV2WithFieldRemoved() { // GH-90000
        return orderSchemaV1().nextVersion("{\"type\":\"object\",\"v\":2}", List.of( // GH-90000
                SchemaField.required("orderId", "string"))); // GH-90000
    }

    private EventSchema orderSchemaV2WithTypeChange() { // GH-90000
        return orderSchemaV1().nextVersion("{\"type\":\"object\",\"v\":2}", List.of( // GH-90000
                SchemaField.required("orderId", "integer"), // GH-90000
                SchemaField.required("amount", "number"))); // GH-90000
    }

    // ─────────────────── Factory Tests ───────────────────

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("create() uses BACKWARD as default")
        void defaultModeIsBackward() { // GH-90000
            EventSchemaRegistry reg = EventSchemaRegistry.create(); // GH-90000
            assertEquals(CompatibilityMode.BACKWARD, reg.getCompatibilityMode("any.subject"));
        }

        @Test
        @DisplayName("create(mode) uses custom default")
        void customDefaultMode() { // GH-90000
            EventSchemaRegistry reg = EventSchemaRegistry.create(CompatibilityMode.FULL); // GH-90000
            assertEquals(CompatibilityMode.FULL, reg.getCompatibilityMode("any.subject"));
        }

        @Test
        @DisplayName("empty registry has zero counts")
        void emptyRegistryCounts() { // GH-90000
            assertEquals(0, registry.subjectCount()); // GH-90000
            assertEquals(0, registry.totalSchemaCount()); // GH-90000
            assertTrue(registry.listSubjects().isEmpty()); // GH-90000
        }
    }

    // ─────────────────── Registration Tests ───────────────────

    @Nested
    @DisplayName("Schema registration")
    class Registration {

        @Test
        @DisplayName("first version registers successfully")
        void firstVersionRegisters() { // GH-90000
            EventSchema v1 = orderSchemaV1(); // GH-90000
            EventSchema registered = registry.register(v1); // GH-90000

            assertEquals(v1, registered); // GH-90000
            assertEquals(1, registry.subjectCount()); // GH-90000
            assertEquals(1, registry.totalSchemaCount()); // GH-90000
        }

        @Test
        @DisplayName("multiple subjects coexist")
        void multipleSubjects() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            registry.register(EventSchema.create("user.created", SchemaFormat.AVRO, // GH-90000
                    "{}", List.of(SchemaField.required("userId", "string")))); // GH-90000

            assertEquals(2, registry.subjectCount()); // GH-90000
            assertEquals(2, registry.totalSchemaCount()); // GH-90000
            assertTrue(registry.listSubjects().containsAll(Set.of("order.created", "user.created"))); // GH-90000
        }

        @Test
        @DisplayName("null schema throws NullPointerException")
        void nullSchemaThrows() { // GH-90000
            assertThrows(NullPointerException.class, () -> registry.register(null)); // GH-90000
        }

        @Test
        @DisplayName("version must increment")
        void versionMustIncrement() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            EventSchema duplicateV1 = EventSchema.create("order.created", SchemaFormat.JSON_SCHEMA, // GH-90000
                    "{\"v\":\"dup\"}", List.of(SchemaField.required("orderId", "string"), // GH-90000
                            SchemaField.required("amount", "number"))); // GH-90000
            // version 1 again → should throw
            assertThrows(SchemaRegistrationException.class, () -> registry.register(duplicateV1)); // GH-90000
        }
    }

    // ─────────────────── Versioning Tests ───────────────────

    @Nested
    @DisplayName("Version management")
    class Versioning {

        @Test
        @DisplayName("getLatest returns most recent version")
        void getLatestReturnsNewest() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            EventSchema v2 = orderSchemaV2WithOptionalField(); // GH-90000
            registry.register(v2); // GH-90000

            Optional<EventSchema> latest = registry.getLatest("order.created");
            assertTrue(latest.isPresent()); // GH-90000
            assertEquals(2, latest.get().version()); // GH-90000
        }

        @Test
        @DisplayName("getVersion returns specific version")
        void getSpecificVersion() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            registry.register(orderSchemaV2WithOptionalField()); // GH-90000

            Optional<EventSchema> v1 = registry.getVersion("order.created", 1); // GH-90000
            assertTrue(v1.isPresent()); // GH-90000
            assertEquals(1, v1.get().version()); // GH-90000

            Optional<EventSchema> v2 = registry.getVersion("order.created", 2); // GH-90000
            assertTrue(v2.isPresent()); // GH-90000
            assertEquals(2, v2.get().version()); // GH-90000
        }

        @Test
        @DisplayName("getVersion returns empty for nonexistent version")
        void nonexistentVersionReturnsEmpty() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            assertTrue(registry.getVersion("order.created", 99).isEmpty()); // GH-90000
        }

        @Test
        @DisplayName("getLatest returns empty for unknown subject")
        void unknownSubjectReturnsEmpty() { // GH-90000
            assertTrue(registry.getLatest("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("getAllVersions returns ordered history")
        void allVersionsOrdered() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            registry.register(orderSchemaV2WithOptionalField()); // GH-90000

            List<EventSchema> all = registry.getAllVersions("order.created");
            assertEquals(2, all.size()); // GH-90000
            assertEquals(1, all.get(0).version()); // GH-90000
            assertEquals(2, all.get(1).version()); // GH-90000
        }

        @Test
        @DisplayName("getAllVersions returns empty for unknown subject")
        void allVersionsUnknown() { // GH-90000
            assertTrue(registry.getAllVersions("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("totalSchemaCount spans subjects and versions")
        void totalCountSpansAll() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            registry.register(orderSchemaV2WithOptionalField()); // GH-90000
            registry.register(EventSchema.create("user.created", SchemaFormat.AVRO, // GH-90000
                    "{}", List.of(SchemaField.required("userId", "string")))); // GH-90000

            assertEquals(2, registry.subjectCount()); // GH-90000
            assertEquals(3, registry.totalSchemaCount()); // GH-90000
        }
    }

    // ─────────────────── BACKWARD Compatibility ───────────────────

    @Nested
    @DisplayName("BACKWARD compatibility")
    class BackwardCompatibility {

        @BeforeEach
        void setMode() { // GH-90000
            registry.setCompatibilityMode("order.created", CompatibilityMode.BACKWARD); // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
        }

        @Test
        @DisplayName("adding optional field is backward compatible")
        void addOptionalFieldCompatible() { // GH-90000
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithOptionalField())); // GH-90000
            assertEquals(2, registry.totalSchemaCount()); // GH-90000
        }

        @Test
        @DisplayName("adding required field WITHOUT default is incompatible")
        void addRequiredFieldIncompatible() { // GH-90000
            SchemaRegistrationException ex = assertThrows(SchemaRegistrationException.class, // GH-90000
                    () -> registry.register(orderSchemaV2WithRequiredField())); // GH-90000
            assertEquals("order.created", ex.getSubject()); // GH-90000
            assertEquals(CompatibilityMode.BACKWARD, ex.getMode()); // GH-90000
            assertFalse(ex.getViolations().isEmpty()); // GH-90000
        }

        @Test
        @DisplayName("adding required field WITH default is backward compatible")
        void addRequiredFieldWithDefaultCompatible() { // GH-90000
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithRequiredFieldAndDefault())); // GH-90000
        }

        @Test
        @DisplayName("removing field is allowed in backward mode")
        void removeFieldAllowed() { // GH-90000
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithFieldRemoved())); // GH-90000
        }

        @Test
        @DisplayName("changing field type is incompatible")
        void typeChangeIncompatible() { // GH-90000
            assertThrows(SchemaRegistrationException.class, // GH-90000
                    () -> registry.register(orderSchemaV2WithTypeChange())); // GH-90000
        }
    }

    // ─────────────────── FORWARD Compatibility ───────────────────

    @Nested
    @DisplayName("FORWARD compatibility")
    class ForwardCompatibility {

        @BeforeEach
        void setMode() { // GH-90000
            registry.setCompatibilityMode("order.created", CompatibilityMode.FORWARD); // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
        }

        @Test
        @DisplayName("adding optional field is forward compatible")
        void addOptionalFieldCompatible() { // GH-90000
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithOptionalField())); // GH-90000
        }

        @Test
        @DisplayName("removing field is incompatible in forward mode")
        void removeFieldIncompatible() { // GH-90000
            assertThrows(SchemaRegistrationException.class, // GH-90000
                    () -> registry.register(orderSchemaV2WithFieldRemoved())); // GH-90000
        }

        @Test
        @DisplayName("changing field type is incompatible")
        void typeChangeIncompatible() { // GH-90000
            assertThrows(SchemaRegistrationException.class, // GH-90000
                    () -> registry.register(orderSchemaV2WithTypeChange())); // GH-90000
        }
    }

    // ─────────────────── FULL Compatibility ───────────────────

    @Nested
    @DisplayName("FULL compatibility")
    class FullCompatibility {

        @BeforeEach
        void setMode() { // GH-90000
            registry.setCompatibilityMode("order.created", CompatibilityMode.FULL); // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
        }

        @Test
        @DisplayName("adding optional field is fully compatible")
        void addOptionalFieldCompatible() { // GH-90000
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithOptionalField())); // GH-90000
        }

        @Test
        @DisplayName("adding required field is incompatible under FULL")
        void addRequiredFieldIncompatible() { // GH-90000
            assertThrows(SchemaRegistrationException.class, // GH-90000
                    () -> registry.register(orderSchemaV2WithRequiredField())); // GH-90000
        }

        @Test
        @DisplayName("removing field is incompatible under FULL")
        void removeFieldIncompatible() { // GH-90000
            assertThrows(SchemaRegistrationException.class, // GH-90000
                    () -> registry.register(orderSchemaV2WithFieldRemoved())); // GH-90000
        }

        @Test
        @DisplayName("type change is incompatible under FULL")
        void typeChangeIncompatible() { // GH-90000
            assertThrows(SchemaRegistrationException.class, // GH-90000
                    () -> registry.register(orderSchemaV2WithTypeChange())); // GH-90000
        }
    }

    // ─────────────────── NONE Compatibility ───────────────────

    @Nested
    @DisplayName("NONE compatibility (no checks)")
    class NoneCompatibility {

        @BeforeEach
        void setMode() { // GH-90000
            registry.setCompatibilityMode("order.created", CompatibilityMode.NONE); // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
        }

        @Test
        @DisplayName("adding required field is allowed with NONE")
        void requireFieldAllowed() { // GH-90000
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithRequiredField())); // GH-90000
        }

        @Test
        @DisplayName("removing field is allowed with NONE")
        void removeFieldAllowed() { // GH-90000
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithFieldRemoved())); // GH-90000
        }

        @Test
        @DisplayName("type change is allowed with NONE")
        void typeChangeAllowed() { // GH-90000
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithTypeChange())); // GH-90000
        }
    }

    // ─────────────────── testCompatibility ───────────────────

    @Nested
    @DisplayName("Test compatibility without registration")
    class TestCompatibilityDryRun {

        @Test
        @DisplayName("first schema is always compatible")
        void firstSchemaCompatible() { // GH-90000
            CompatibilityResult result = registry.testCompatibility(orderSchemaV1()); // GH-90000
            assertTrue(result.compatible()); // GH-90000
        }

        @Test
        @DisplayName("dry-run reports violations without registering")
        void dryRunDoesNotRegister() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            registry.setCompatibilityMode("order.created", CompatibilityMode.BACKWARD); // GH-90000

            CompatibilityResult result = registry.testCompatibility(orderSchemaV2WithRequiredField()); // GH-90000
            assertFalse(result.compatible()); // GH-90000
            assertFalse(result.violations().isEmpty()); // GH-90000
            // Schema was NOT registered
            assertEquals(1, registry.totalSchemaCount()); // GH-90000
        }

        @Test
        @DisplayName("dry-run succeeds for compatible schema")
        void dryRunSucceeds() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            CompatibilityResult result = registry.testCompatibility(orderSchemaV2WithOptionalField()); // GH-90000
            assertTrue(result.compatible()); // GH-90000
            assertTrue(result.violations().isEmpty()); // GH-90000
        }
    }

    // ─────────────────── Compatibility Mode Config ───────────────────

    @Nested
    @DisplayName("Compatibility mode configuration")
    class ModeConfiguration {

        @Test
        @DisplayName("subject-level mode overrides default")
        void subjectOverridesDefault() { // GH-90000
            assertEquals(CompatibilityMode.BACKWARD, registry.getCompatibilityMode("order.created"));
            registry.setCompatibilityMode("order.created", CompatibilityMode.NONE); // GH-90000
            assertEquals(CompatibilityMode.NONE, registry.getCompatibilityMode("order.created"));
            // Other subjects still use default
            assertEquals(CompatibilityMode.BACKWARD, registry.getCompatibilityMode("other.subject"));
        }

        @Test
        @DisplayName("setDefaultCompatibilityMode changes default")
        void changeDefault() { // GH-90000
            registry.setDefaultCompatibilityMode(CompatibilityMode.FULL); // GH-90000
            assertEquals(CompatibilityMode.FULL, registry.getCompatibilityMode("any.new.subject"));
        }
    }

    // ─────────────────── Subject Management ───────────────────

    @Nested
    @DisplayName("Subject management")
    class SubjectManagement {

        @Test
        @DisplayName("deleteSubject removes all versions")
        void deleteRemovesAll() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            registry.register(orderSchemaV2WithOptionalField()); // GH-90000

            int deleted = registry.deleteSubject("order.created");
            assertEquals(2, deleted); // GH-90000
            assertTrue(registry.getLatest("order.created").isEmpty());
            assertEquals(0, registry.subjectCount()); // GH-90000
        }

        @Test
        @DisplayName("deleteSubject for unknown returns zero")
        void deleteUnknownReturnsZero() { // GH-90000
            assertEquals(0, registry.deleteSubject("nonexistent"));
        }

        @Test
        @DisplayName("deleteSubject also removes compatibility mode")
        void deleteRemovesCompatibilityMode() { // GH-90000
            registry.setCompatibilityMode("order.created", CompatibilityMode.FULL); // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            registry.deleteSubject("order.created");
            // Falls back to default since subject-level config was removed
            assertEquals(CompatibilityMode.BACKWARD, registry.getCompatibilityMode("order.created"));
        }

        @Test
        @DisplayName("re-register after delete works as fresh registration")
        void reRegisterAfterDelete() { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000
            registry.deleteSubject("order.created");

            EventSchema fresh = EventSchema.create("order.created", SchemaFormat.JSON_SCHEMA, // GH-90000
                    "{\"fresh\":true}", List.of(SchemaField.required("id", "string"))); // GH-90000
            assertDoesNotThrow(() -> registry.register(fresh)); // GH-90000
            assertEquals(1, registry.totalSchemaCount()); // GH-90000
        }
    }

    // ─────────────────── SchemaRegistrationException ───────────────────

    @Nested
    @DisplayName("SchemaRegistrationException")
    class RegistrationExceptionTests {

        @Test
        @DisplayName("carries all context fields")
        void exceptionContextFields() { // GH-90000
            registry.setCompatibilityMode("order.created", CompatibilityMode.BACKWARD); // GH-90000
            registry.register(orderSchemaV1()); // GH-90000

            SchemaRegistrationException ex = assertThrows(SchemaRegistrationException.class, // GH-90000
                    () -> registry.register(orderSchemaV2WithRequiredField())); // GH-90000

            assertEquals("order.created", ex.getSubject()); // GH-90000
            assertEquals(2, ex.getVersion()); // GH-90000
            assertEquals(CompatibilityMode.BACKWARD, ex.getMode()); // GH-90000
            assertFalse(ex.getViolations().isEmpty()); // GH-90000
            assertTrue(ex.getMessage().contains("order.created"));
            assertTrue(ex.getMessage().contains("BACKWARD"));
        }
    }

    // ─────────────────── Concurrent Access ───────────────────

    @Nested
    @DisplayName("Concurrent access")
    class ConcurrentAccess {

        @Test
        @DisplayName("concurrent registrations for different subjects are safe")
        void concurrentDifferentSubjects() throws Exception { // GH-90000
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount); // GH-90000
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            registry.setDefaultCompatibilityMode(CompatibilityMode.NONE); // GH-90000

            List<Future<?>> futures = new ArrayList<>(); // GH-90000
            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int idx = i;
                futures.add(executor.submit(() -> { // GH-90000
                    latch.countDown(); // GH-90000
                    try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000
                    registry.register(EventSchema.create("subject." + idx, SchemaFormat.JSON_SCHEMA, // GH-90000
                            "{}", List.of(SchemaField.required("id", "string")))); // GH-90000
                }));
            }

            for (Future<?> future : futures) { // GH-90000
                future.get(5, TimeUnit.SECONDS); // GH-90000
            }
            executor.shutdown(); // GH-90000

            assertEquals(threadCount, registry.subjectCount()); // GH-90000
        }

        @Test
        @DisplayName("concurrent reads while writing are safe")
        void concurrentReadsDuringWrite() throws Exception { // GH-90000
            registry.register(orderSchemaV1()); // GH-90000

            ExecutorService executor = Executors.newFixedThreadPool(4); // GH-90000
            CountDownLatch start = new CountDownLatch(1); // GH-90000

            // Readers
            List<Future<Optional<EventSchema>>> readers = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 3; i++) { // GH-90000
                readers.add(executor.submit(() -> { // GH-90000
                    start.await(); // GH-90000
                    return registry.getLatest("order.created");
                }));
            }

            // Writer
            Future<?> writer = executor.submit(() -> { // GH-90000
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000
                registry.register(orderSchemaV2WithOptionalField()); // GH-90000
            });

            start.countDown(); // GH-90000
            writer.get(5, TimeUnit.SECONDS); // GH-90000
            for (Future<Optional<EventSchema>> reader : readers) { // GH-90000
                assertTrue(reader.get(5, TimeUnit.SECONDS).isPresent()); // GH-90000
            }
            executor.shutdown(); // GH-90000
        }
    }

    // ─────────────────── EventSchema record tests ───────────────────

    @Nested
    @DisplayName("EventSchema record")
    class EventSchemaRecordTests {

        @Test
        @DisplayName("create() produces version 1 with UUID id")
        void createProducesV1() { // GH-90000
            EventSchema schema = orderSchemaV1(); // GH-90000
            assertEquals(1, schema.version()); // GH-90000
            assertEquals("order.created", schema.subject()); // GH-90000
            assertNotNull(schema.id()); // GH-90000
            assertNotNull(schema.createdAt()); // GH-90000
            assertEquals(2, schema.fields().size()); // GH-90000
        }

        @Test
        @DisplayName("nextVersion increments version")
        void nextVersionIncrements() { // GH-90000
            EventSchema v1 = orderSchemaV1(); // GH-90000
            EventSchema v2 = v1.nextVersion("{}", List.of()); // GH-90000
            assertEquals(2, v2.version()); // GH-90000
            assertEquals(v1.subject(), v2.subject()); // GH-90000
            assertNotEquals(v1.id(), v2.id()); // GH-90000
        }

        @Test
        @DisplayName("SchemaField factories work correctly")
        void schemaFieldFactories() { // GH-90000
            SchemaField req = SchemaField.required("name", "string"); // GH-90000
            assertTrue(req.required()); // GH-90000
            assertNull(req.defaultValue()); // GH-90000

            SchemaField opt = SchemaField.optional("age", "integer"); // GH-90000
            assertFalse(opt.required()); // GH-90000
            assertNull(opt.defaultValue()); // GH-90000

            SchemaField optWithDefault = SchemaField.optional("country", "string", "US"); // GH-90000
            assertFalse(optWithDefault.required()); // GH-90000
            assertEquals("US", optWithDefault.defaultValue()); // GH-90000
        }
    }

    // ─────────────────── SchemaCompatibilityChecker unit tests ───────────────────

    @Nested
    @DisplayName("SchemaCompatibilityChecker")
    class CompatibilityCheckerTests {

        private final SchemaCompatibilityChecker checker = new SchemaCompatibilityChecker(); // GH-90000

        @Test
        @DisplayName("identical schemas are compatible in all modes")
        void identicalSchemasCompatible() { // GH-90000
            EventSchema v1 = orderSchemaV1(); // GH-90000
            EventSchema v2 = v1.nextVersion(v1.definition(), v1.fields()); // GH-90000

            for (CompatibilityMode mode : CompatibilityMode.values()) { // GH-90000
                assertTrue(checker.check(v1, v2, mode).compatible(), // GH-90000
                        "Identical schemas should be compatible in " + mode);
            }
        }

        @Test
        @DisplayName("NONE mode always returns compatible")
        void noneModeAlwaysCompatible() { // GH-90000
            EventSchema v1 = orderSchemaV1(); // GH-90000
            EventSchema v2 = v1.nextVersion("{}", List.of()); // completely different // GH-90000
            assertTrue(checker.check(v1, v2, CompatibilityMode.NONE).compatible()); // GH-90000
        }
    }

    // ─────────────────── Edge Cases ───────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("schema with no fields is valid")
        void emptyFieldsValid() { // GH-90000
            EventSchema empty = EventSchema.create("empty.subject", SchemaFormat.JSON_SCHEMA, // GH-90000
                    "{}", List.of()); // GH-90000
            assertDoesNotThrow(() -> registry.register(empty)); // GH-90000
        }

        @Test
        @DisplayName("different formats for same subject coexist via versioning")
        void differentFormats() { // GH-90000
            registry.setCompatibilityMode("mixed.format", CompatibilityMode.NONE); // GH-90000
            EventSchema v1 = EventSchema.create("mixed.format", SchemaFormat.JSON_SCHEMA, // GH-90000
                    "{}", List.of(SchemaField.required("id", "string"))); // GH-90000
            registry.register(v1); // GH-90000

            EventSchema v2 = v1.nextVersion("{}", List.of(SchemaField.required("id", "string"))); // GH-90000
            // Format stays the same because nextVersion preserves it
            assertDoesNotThrow(() -> registry.register(v2)); // GH-90000
        }

        @Test
        @DisplayName("PROTOBUF format schemas register")
        void protobufFormat() { // GH-90000
            EventSchema proto = EventSchema.create("proto.event", SchemaFormat.PROTOBUF, // GH-90000
                    "message Event { string id = 1; }", List.of( // GH-90000
                            SchemaField.required("id", "string"))); // GH-90000
            assertDoesNotThrow(() -> registry.register(proto)); // GH-90000
            assertEquals(SchemaFormat.PROTOBUF, registry.getLatest("proto.event").get().format());
        }

        @Test
        @DisplayName("AVRO format schemas register")
        void avroFormat() { // GH-90000
            EventSchema avro = EventSchema.create("avro.event", SchemaFormat.AVRO, // GH-90000
                    "{\"type\":\"record\",\"name\":\"Event\"}", List.of( // GH-90000
                            SchemaField.required("id", "string"))); // GH-90000
            assertDoesNotThrow(() -> registry.register(avro)); // GH-90000
            assertEquals(SchemaFormat.AVRO, registry.getLatest("avro.event").get().format());
        }
    }
}
