/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
    void setUp() {
        registry = EventSchemaRegistry.create();
    }

    // ─────────────────── Helper factories ───────────────────

    private EventSchema orderSchemaV1() {
        return EventSchema.create("order.created", SchemaFormat.JSON_SCHEMA,
                "{\"type\":\"object\"}", List.of(
                        SchemaField.required("orderId", "string"),
                        SchemaField.required("amount", "number")));
    }

    private EventSchema orderSchemaV2WithOptionalField() {
        return orderSchemaV1().nextVersion("{\"type\":\"object\",\"v\":2}", List.of(
                SchemaField.required("orderId", "string"),
                SchemaField.required("amount", "number"),
                SchemaField.optional("priority", "integer")));
    }

    private EventSchema orderSchemaV2WithRequiredField() {
        return orderSchemaV1().nextVersion("{\"type\":\"object\",\"v\":2}", List.of(
                SchemaField.required("orderId", "string"),
                SchemaField.required("amount", "number"),
                SchemaField.required("region", "string")));
    }

    private EventSchema orderSchemaV2WithRequiredFieldAndDefault() {
        return orderSchemaV1().nextVersion("{\"type\":\"object\",\"v\":2}", List.of(
                SchemaField.required("orderId", "string"),
                SchemaField.required("amount", "number"),
                SchemaField.required("region", "string", "US")));
    }

    private EventSchema orderSchemaV2WithFieldRemoved() {
        return orderSchemaV1().nextVersion("{\"type\":\"object\",\"v\":2}", List.of(
                SchemaField.required("orderId", "string")));
    }

    private EventSchema orderSchemaV2WithTypeChange() {
        return orderSchemaV1().nextVersion("{\"type\":\"object\",\"v\":2}", List.of(
                SchemaField.required("orderId", "integer"),
                SchemaField.required("amount", "number")));
    }

    // ─────────────────── Factory Tests ───────────────────

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("create() uses BACKWARD as default")
        void defaultModeIsBackward() {
            EventSchemaRegistry reg = EventSchemaRegistry.create();
            assertEquals(CompatibilityMode.BACKWARD, reg.getCompatibilityMode("any.subject"));
        }

        @Test
        @DisplayName("create(mode) uses custom default")
        void customDefaultMode() {
            EventSchemaRegistry reg = EventSchemaRegistry.create(CompatibilityMode.FULL);
            assertEquals(CompatibilityMode.FULL, reg.getCompatibilityMode("any.subject"));
        }

        @Test
        @DisplayName("empty registry has zero counts")
        void emptyRegistryCounts() {
            assertEquals(0, registry.subjectCount());
            assertEquals(0, registry.totalSchemaCount());
            assertTrue(registry.listSubjects().isEmpty());
        }
    }

    // ─────────────────── Registration Tests ───────────────────

    @Nested
    @DisplayName("Schema registration")
    class Registration {

        @Test
        @DisplayName("first version registers successfully")
        void firstVersionRegisters() {
            EventSchema v1 = orderSchemaV1();
            EventSchema registered = registry.register(v1);

            assertEquals(v1, registered);
            assertEquals(1, registry.subjectCount());
            assertEquals(1, registry.totalSchemaCount());
        }

        @Test
        @DisplayName("multiple subjects coexist")
        void multipleSubjects() {
            registry.register(orderSchemaV1());
            registry.register(EventSchema.create("user.created", SchemaFormat.AVRO,
                    "{}", List.of(SchemaField.required("userId", "string"))));

            assertEquals(2, registry.subjectCount());
            assertEquals(2, registry.totalSchemaCount());
            assertTrue(registry.listSubjects().containsAll(Set.of("order.created", "user.created")));
        }

        @Test
        @DisplayName("null schema throws NullPointerException")
        void nullSchemaThrows() {
            assertThrows(NullPointerException.class, () -> registry.register(null));
        }

        @Test
        @DisplayName("version must increment")
        void versionMustIncrement() {
            registry.register(orderSchemaV1());
            EventSchema duplicateV1 = EventSchema.create("order.created", SchemaFormat.JSON_SCHEMA,
                    "{\"v\":\"dup\"}", List.of(SchemaField.required("orderId", "string"),
                            SchemaField.required("amount", "number")));
            // version 1 again → should throw
            assertThrows(SchemaRegistrationException.class, () -> registry.register(duplicateV1));
        }
    }

    // ─────────────────── Versioning Tests ───────────────────

    @Nested
    @DisplayName("Version management")
    class Versioning {

        @Test
        @DisplayName("getLatest returns most recent version")
        void getLatestReturnsNewest() {
            registry.register(orderSchemaV1());
            EventSchema v2 = orderSchemaV2WithOptionalField();
            registry.register(v2);

            Optional<EventSchema> latest = registry.getLatest("order.created");
            assertTrue(latest.isPresent());
            assertEquals(2, latest.get().version());
        }

        @Test
        @DisplayName("getVersion returns specific version")
        void getSpecificVersion() {
            registry.register(orderSchemaV1());
            registry.register(orderSchemaV2WithOptionalField());

            Optional<EventSchema> v1 = registry.getVersion("order.created", 1);
            assertTrue(v1.isPresent());
            assertEquals(1, v1.get().version());

            Optional<EventSchema> v2 = registry.getVersion("order.created", 2);
            assertTrue(v2.isPresent());
            assertEquals(2, v2.get().version());
        }

        @Test
        @DisplayName("getVersion returns empty for nonexistent version")
        void nonexistentVersionReturnsEmpty() {
            registry.register(orderSchemaV1());
            assertTrue(registry.getVersion("order.created", 99).isEmpty());
        }

        @Test
        @DisplayName("getLatest returns empty for unknown subject")
        void unknownSubjectReturnsEmpty() {
            assertTrue(registry.getLatest("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("getAllVersions returns ordered history")
        void allVersionsOrdered() {
            registry.register(orderSchemaV1());
            registry.register(orderSchemaV2WithOptionalField());

            List<EventSchema> all = registry.getAllVersions("order.created");
            assertEquals(2, all.size());
            assertEquals(1, all.get(0).version());
            assertEquals(2, all.get(1).version());
        }

        @Test
        @DisplayName("getAllVersions returns empty for unknown subject")
        void allVersionsUnknown() {
            assertTrue(registry.getAllVersions("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("totalSchemaCount spans subjects and versions")
        void totalCountSpansAll() {
            registry.register(orderSchemaV1());
            registry.register(orderSchemaV2WithOptionalField());
            registry.register(EventSchema.create("user.created", SchemaFormat.AVRO,
                    "{}", List.of(SchemaField.required("userId", "string"))));

            assertEquals(2, registry.subjectCount());
            assertEquals(3, registry.totalSchemaCount());
        }
    }

    // ─────────────────── BACKWARD Compatibility ───────────────────

    @Nested
    @DisplayName("BACKWARD compatibility")
    class BackwardCompatibility {

        @BeforeEach
        void setMode() {
            registry.setCompatibilityMode("order.created", CompatibilityMode.BACKWARD);
            registry.register(orderSchemaV1());
        }

        @Test
        @DisplayName("adding optional field is backward compatible")
        void addOptionalFieldCompatible() {
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithOptionalField()));
            assertEquals(2, registry.totalSchemaCount());
        }

        @Test
        @DisplayName("adding required field WITHOUT default is incompatible")
        void addRequiredFieldIncompatible() {
            SchemaRegistrationException ex = assertThrows(SchemaRegistrationException.class,
                    () -> registry.register(orderSchemaV2WithRequiredField()));
            assertEquals("order.created", ex.getSubject());
            assertEquals(CompatibilityMode.BACKWARD, ex.getMode());
            assertFalse(ex.getViolations().isEmpty());
        }

        @Test
        @DisplayName("adding required field WITH default is backward compatible")
        void addRequiredFieldWithDefaultCompatible() {
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithRequiredFieldAndDefault()));
        }

        @Test
        @DisplayName("removing field is allowed in backward mode")
        void removeFieldAllowed() {
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithFieldRemoved()));
        }

        @Test
        @DisplayName("changing field type is incompatible")
        void typeChangeIncompatible() {
            assertThrows(SchemaRegistrationException.class,
                    () -> registry.register(orderSchemaV2WithTypeChange()));
        }
    }

    // ─────────────────── FORWARD Compatibility ───────────────────

    @Nested
    @DisplayName("FORWARD compatibility")
    class ForwardCompatibility {

        @BeforeEach
        void setMode() {
            registry.setCompatibilityMode("order.created", CompatibilityMode.FORWARD);
            registry.register(orderSchemaV1());
        }

        @Test
        @DisplayName("adding optional field is forward compatible")
        void addOptionalFieldCompatible() {
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithOptionalField()));
        }

        @Test
        @DisplayName("removing field is incompatible in forward mode")
        void removeFieldIncompatible() {
            assertThrows(SchemaRegistrationException.class,
                    () -> registry.register(orderSchemaV2WithFieldRemoved()));
        }

        @Test
        @DisplayName("changing field type is incompatible")
        void typeChangeIncompatible() {
            assertThrows(SchemaRegistrationException.class,
                    () -> registry.register(orderSchemaV2WithTypeChange()));
        }
    }

    // ─────────────────── FULL Compatibility ───────────────────

    @Nested
    @DisplayName("FULL compatibility")
    class FullCompatibility {

        @BeforeEach
        void setMode() {
            registry.setCompatibilityMode("order.created", CompatibilityMode.FULL);
            registry.register(orderSchemaV1());
        }

        @Test
        @DisplayName("adding optional field is fully compatible")
        void addOptionalFieldCompatible() {
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithOptionalField()));
        }

        @Test
        @DisplayName("adding required field is incompatible under FULL")
        void addRequiredFieldIncompatible() {
            assertThrows(SchemaRegistrationException.class,
                    () -> registry.register(orderSchemaV2WithRequiredField()));
        }

        @Test
        @DisplayName("removing field is incompatible under FULL")
        void removeFieldIncompatible() {
            assertThrows(SchemaRegistrationException.class,
                    () -> registry.register(orderSchemaV2WithFieldRemoved()));
        }

        @Test
        @DisplayName("type change is incompatible under FULL")
        void typeChangeIncompatible() {
            assertThrows(SchemaRegistrationException.class,
                    () -> registry.register(orderSchemaV2WithTypeChange()));
        }
    }

    // ─────────────────── NONE Compatibility ───────────────────

    @Nested
    @DisplayName("NONE compatibility (no checks)")
    class NoneCompatibility {

        @BeforeEach
        void setMode() {
            registry.setCompatibilityMode("order.created", CompatibilityMode.NONE);
            registry.register(orderSchemaV1());
        }

        @Test
        @DisplayName("adding required field is allowed with NONE")
        void requireFieldAllowed() {
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithRequiredField()));
        }

        @Test
        @DisplayName("removing field is allowed with NONE")
        void removeFieldAllowed() {
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithFieldRemoved()));
        }

        @Test
        @DisplayName("type change is allowed with NONE")
        void typeChangeAllowed() {
            assertDoesNotThrow(() -> registry.register(orderSchemaV2WithTypeChange()));
        }
    }

    // ─────────────────── testCompatibility ───────────────────

    @Nested
    @DisplayName("Test compatibility without registration")
    class TestCompatibilityDryRun {

        @Test
        @DisplayName("first schema is always compatible")
        void firstSchemaCompatible() {
            CompatibilityResult result = registry.testCompatibility(orderSchemaV1());
            assertTrue(result.compatible());
        }

        @Test
        @DisplayName("dry-run reports violations without registering")
        void dryRunDoesNotRegister() {
            registry.register(orderSchemaV1());
            registry.setCompatibilityMode("order.created", CompatibilityMode.BACKWARD);

            CompatibilityResult result = registry.testCompatibility(orderSchemaV2WithRequiredField());
            assertFalse(result.compatible());
            assertFalse(result.violations().isEmpty());
            // Schema was NOT registered
            assertEquals(1, registry.totalSchemaCount());
        }

        @Test
        @DisplayName("dry-run succeeds for compatible schema")
        void dryRunSucceeds() {
            registry.register(orderSchemaV1());
            CompatibilityResult result = registry.testCompatibility(orderSchemaV2WithOptionalField());
            assertTrue(result.compatible());
            assertTrue(result.violations().isEmpty());
        }
    }

    // ─────────────────── Compatibility Mode Config ───────────────────

    @Nested
    @DisplayName("Compatibility mode configuration")
    class ModeConfiguration {

        @Test
        @DisplayName("subject-level mode overrides default")
        void subjectOverridesDefault() {
            assertEquals(CompatibilityMode.BACKWARD, registry.getCompatibilityMode("order.created"));
            registry.setCompatibilityMode("order.created", CompatibilityMode.NONE);
            assertEquals(CompatibilityMode.NONE, registry.getCompatibilityMode("order.created"));
            // Other subjects still use default
            assertEquals(CompatibilityMode.BACKWARD, registry.getCompatibilityMode("other.subject"));
        }

        @Test
        @DisplayName("setDefaultCompatibilityMode changes default")
        void changeDefault() {
            registry.setDefaultCompatibilityMode(CompatibilityMode.FULL);
            assertEquals(CompatibilityMode.FULL, registry.getCompatibilityMode("any.new.subject"));
        }
    }

    // ─────────────────── Subject Management ───────────────────

    @Nested
    @DisplayName("Subject management")
    class SubjectManagement {

        @Test
        @DisplayName("deleteSubject removes all versions")
        void deleteRemovesAll() {
            registry.register(orderSchemaV1());
            registry.register(orderSchemaV2WithOptionalField());

            int deleted = registry.deleteSubject("order.created");
            assertEquals(2, deleted);
            assertTrue(registry.getLatest("order.created").isEmpty());
            assertEquals(0, registry.subjectCount());
        }

        @Test
        @DisplayName("deleteSubject for unknown returns zero")
        void deleteUnknownReturnsZero() {
            assertEquals(0, registry.deleteSubject("nonexistent"));
        }

        @Test
        @DisplayName("deleteSubject also removes compatibility mode")
        void deleteRemovesCompatibilityMode() {
            registry.setCompatibilityMode("order.created", CompatibilityMode.FULL);
            registry.register(orderSchemaV1());
            registry.deleteSubject("order.created");
            // Falls back to default since subject-level config was removed
            assertEquals(CompatibilityMode.BACKWARD, registry.getCompatibilityMode("order.created"));
        }

        @Test
        @DisplayName("re-register after delete works as fresh registration")
        void reRegisterAfterDelete() {
            registry.register(orderSchemaV1());
            registry.deleteSubject("order.created");

            EventSchema fresh = EventSchema.create("order.created", SchemaFormat.JSON_SCHEMA,
                    "{\"fresh\":true}", List.of(SchemaField.required("id", "string")));
            assertDoesNotThrow(() -> registry.register(fresh));
            assertEquals(1, registry.totalSchemaCount());
        }
    }

    // ─────────────────── SchemaRegistrationException ───────────────────

    @Nested
    @DisplayName("SchemaRegistrationException")
    class RegistrationExceptionTests {

        @Test
        @DisplayName("carries all context fields")
        void exceptionContextFields() {
            registry.setCompatibilityMode("order.created", CompatibilityMode.BACKWARD);
            registry.register(orderSchemaV1());

            SchemaRegistrationException ex = assertThrows(SchemaRegistrationException.class,
                    () -> registry.register(orderSchemaV2WithRequiredField()));

            assertEquals("order.created", ex.getSubject());
            assertEquals(2, ex.getVersion());
            assertEquals(CompatibilityMode.BACKWARD, ex.getMode());
            assertFalse(ex.getViolations().isEmpty());
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
        void concurrentDifferentSubjects() throws Exception {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            registry.setDefaultCompatibilityMode(CompatibilityMode.NONE);

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    latch.countDown();
                    try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    registry.register(EventSchema.create("subject." + idx, SchemaFormat.JSON_SCHEMA,
                            "{}", List.of(SchemaField.required("id", "string"))));
                }));
            }

            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            executor.shutdown();

            assertEquals(threadCount, registry.subjectCount());
        }

        @Test
        @DisplayName("concurrent reads while writing are safe")
        void concurrentReadsDuringWrite() throws Exception {
            registry.register(orderSchemaV1());

            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch start = new CountDownLatch(1);

            // Readers
            List<Future<Optional<EventSchema>>> readers = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                readers.add(executor.submit(() -> {
                    start.await();
                    return registry.getLatest("order.created");
                }));
            }

            // Writer
            Future<?> writer = executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                registry.register(orderSchemaV2WithOptionalField());
            });

            start.countDown();
            writer.get(5, TimeUnit.SECONDS);
            for (Future<Optional<EventSchema>> reader : readers) {
                assertTrue(reader.get(5, TimeUnit.SECONDS).isPresent());
            }
            executor.shutdown();
        }
    }

    // ─────────────────── EventSchema record tests ───────────────────

    @Nested
    @DisplayName("EventSchema record")
    class EventSchemaRecordTests {

        @Test
        @DisplayName("create() produces version 1 with UUID id")
        void createProducesV1() {
            EventSchema schema = orderSchemaV1();
            assertEquals(1, schema.version());
            assertEquals("order.created", schema.subject());
            assertNotNull(schema.id());
            assertNotNull(schema.createdAt());
            assertEquals(2, schema.fields().size());
        }

        @Test
        @DisplayName("nextVersion increments version")
        void nextVersionIncrements() {
            EventSchema v1 = orderSchemaV1();
            EventSchema v2 = v1.nextVersion("{}", List.of());
            assertEquals(2, v2.version());
            assertEquals(v1.subject(), v2.subject());
            assertNotEquals(v1.id(), v2.id());
        }

        @Test
        @DisplayName("SchemaField factories work correctly")
        void schemaFieldFactories() {
            SchemaField req = SchemaField.required("name", "string");
            assertTrue(req.required());
            assertNull(req.defaultValue());

            SchemaField opt = SchemaField.optional("age", "integer");
            assertFalse(opt.required());
            assertNull(opt.defaultValue());

            SchemaField optWithDefault = SchemaField.optional("country", "string", "US");
            assertFalse(optWithDefault.required());
            assertEquals("US", optWithDefault.defaultValue());
        }
    }

    // ─────────────────── SchemaCompatibilityChecker unit tests ───────────────────

    @Nested
    @DisplayName("SchemaCompatibilityChecker")
    class CompatibilityCheckerTests {

        private final SchemaCompatibilityChecker checker = new SchemaCompatibilityChecker();

        @Test
        @DisplayName("identical schemas are compatible in all modes")
        void identicalSchemasCompatible() {
            EventSchema v1 = orderSchemaV1();
            EventSchema v2 = v1.nextVersion(v1.definition(), v1.fields());

            for (CompatibilityMode mode : CompatibilityMode.values()) {
                assertTrue(checker.check(v1, v2, mode).compatible(),
                        "Identical schemas should be compatible in " + mode);
            }
        }

        @Test
        @DisplayName("NONE mode always returns compatible")
        void noneModeAlwaysCompatible() {
            EventSchema v1 = orderSchemaV1();
            EventSchema v2 = v1.nextVersion("{}", List.of()); // completely different
            assertTrue(checker.check(v1, v2, CompatibilityMode.NONE).compatible());
        }
    }

    // ─────────────────── Edge Cases ───────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("schema with no fields is valid")
        void emptyFieldsValid() {
            EventSchema empty = EventSchema.create("empty.subject", SchemaFormat.JSON_SCHEMA,
                    "{}", List.of());
            assertDoesNotThrow(() -> registry.register(empty));
        }

        @Test
        @DisplayName("different formats for same subject coexist via versioning")
        void differentFormats() {
            registry.setCompatibilityMode("mixed.format", CompatibilityMode.NONE);
            EventSchema v1 = EventSchema.create("mixed.format", SchemaFormat.JSON_SCHEMA,
                    "{}", List.of(SchemaField.required("id", "string")));
            registry.register(v1);

            EventSchema v2 = v1.nextVersion("{}", List.of(SchemaField.required("id", "string")));
            // Format stays the same because nextVersion preserves it
            assertDoesNotThrow(() -> registry.register(v2));
        }

        @Test
        @DisplayName("PROTOBUF format schemas register")
        void protobufFormat() {
            EventSchema proto = EventSchema.create("proto.event", SchemaFormat.PROTOBUF,
                    "message Event { string id = 1; }", List.of(
                            SchemaField.required("id", "string")));
            assertDoesNotThrow(() -> registry.register(proto));
            assertEquals(SchemaFormat.PROTOBUF, registry.getLatest("proto.event").get().format());
        }

        @Test
        @DisplayName("AVRO format schemas register")
        void avroFormat() {
            EventSchema avro = EventSchema.create("avro.event", SchemaFormat.AVRO,
                    "{\"type\":\"record\",\"name\":\"Event\"}", List.of(
                            SchemaField.required("id", "string")));
            assertDoesNotThrow(() -> registry.register(avro));
            assertEquals(SchemaFormat.AVRO, registry.getLatest("avro.event").get().format());
        }
    }
}
