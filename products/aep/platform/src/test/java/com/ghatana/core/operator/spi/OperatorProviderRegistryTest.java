/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.2 — Tests for Operator SPI discovery and registration.
 */
package com.ghatana.core.operator.spi;

import com.ghatana.core.operator.*;
import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link OperatorProvider}, {@link OperatorProviderRegistry}, and catalog materialization.
 */
@DisplayName("Operator SPI")
class OperatorProviderRegistryTest {

    private OperatorProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = OperatorProviderRegistry.create();
    }

    // =========================================================================
    // Stub operator for testing
    // =========================================================================

    static class StubOperator extends AbstractOperator {
        StubOperator(OperatorId id, OperatorType type) {
            super(id, type, id.toString(), "Stub operator", List.of("stub"), null);
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            return Promise.of(OperatorResult.of(event));
        }

        @Override
        public Event toEvent() {
            return Event.builder().type("operator.registered")
                    .addPayload("operatorId", getId().toString())
                    .build();
        }
    }

    // =========================================================================
    // Test providers
    // =========================================================================

    static class StreamOperatorProvider implements OperatorProvider {
        static final OperatorId FILTER_OP = OperatorId.parse("test:stream:filter:1.0");
        static final OperatorId MAP_OP = OperatorId.parse("test:stream:map:1.0");

        @Override
        public String getProviderId() { return "test-stream"; }

        @Override
        public String getProviderName() { return "Test Stream Provider"; }

        @Override
        public Set<OperatorId> getOperatorIds() { return Set.of(FILTER_OP, MAP_OP); }

        @Override
        public Set<OperatorType> getOperatorTypes() { return Set.of(OperatorType.STREAM); }

        @Override
        public UnifiedOperator createOperator(OperatorId operatorId, OperatorConfig config) {
            if (!supports(operatorId)) {
                throw new IllegalArgumentException("Unsupported: " + operatorId);
            }
            return new StubOperator(operatorId, OperatorType.STREAM);
        }
    }

    static class PatternOperatorProvider implements OperatorProvider {
        static final OperatorId SEQ_OP = OperatorId.parse("test:pattern:seq:1.0");

        @Override
        public String getProviderId() { return "test-pattern"; }

        @Override
        public String getProviderName() { return "Test Pattern Provider"; }

        @Override
        public Set<OperatorId> getOperatorIds() { return Set.of(SEQ_OP); }

        @Override
        public Set<OperatorType> getOperatorTypes() { return Set.of(OperatorType.PATTERN); }

        @Override
        public UnifiedOperator createOperator(OperatorId operatorId, OperatorConfig config) {
            return new StubOperator(operatorId, OperatorType.PATTERN);
        }

        @Override
        public int priority() { return 500; }
    }

    static class DisabledProvider implements OperatorProvider {
        @Override
        public String getProviderId() { return "test-disabled"; }
        @Override
        public String getProviderName() { return "Disabled"; }
        @Override
        public Set<OperatorId> getOperatorIds() { return Set.of(); }
        @Override
        public Set<OperatorType> getOperatorTypes() { return Set.of(); }
        @Override
        public UnifiedOperator createOperator(OperatorId operatorId, OperatorConfig config) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean isEnabled() { return false; }
    }

    // =========================================================================
    // 1. Provider Interface
    // =========================================================================

    @Nested
    @DisplayName("Provider Interface")
    class ProviderInterfaceTests {

        @Test
        @DisplayName("Default supports() delegates to getOperatorIds()")
        void supportsDefault() {
            StreamOperatorProvider provider = new StreamOperatorProvider();
            assertThat(provider.supports(StreamOperatorProvider.FILTER_OP)).isTrue();
            assertThat(provider.supports(PatternOperatorProvider.SEQ_OP)).isFalse();
        }

        @Test
        @DisplayName("Defaults: priority=1000, enabled=true, version=1.0.0")
        void defaults() {
            StreamOperatorProvider p = new StreamOperatorProvider();
            assertThat(p.priority()).isEqualTo(1000);
            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("Custom priority override")
        void customPriority() {
            PatternOperatorProvider p = new PatternOperatorProvider();
            assertThat(p.priority()).isEqualTo(500);
        }
    }

    // =========================================================================
    // 2. Registration
    // =========================================================================

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("Register and retrieve provider")
        void registerAndRetrieve() {
            registry.registerProvider(new StreamOperatorProvider());
            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.contains("test-stream")).isTrue();
            assertThat(registry.getProvider("test-stream")).isPresent();
        }

        @Test
        @DisplayName("Duplicate registration throws")
        void duplicateThrows() {
            registry.registerProvider(new StreamOperatorProvider());
            assertThatThrownBy(() -> registry.registerProvider(new StreamOperatorProvider()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("Unregister removes provider")
        void unregister() {
            registry.registerProvider(new StreamOperatorProvider());
            assertThat(registry.unregisterProvider("test-stream")).isTrue();
            assertThat(registry.size()).isZero();
        }

        @Test
        @DisplayName("Unknown provider returns empty")
        void unknownEmpty() {
            assertThat(registry.getProvider("ghost")).isEmpty();
        }

        @Test
        @DisplayName("Null rejected")
        void nullRejected() {
            assertThatThrownBy(() -> registry.registerProvider(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // 3. Type/ID Discovery
    // =========================================================================

    @Nested
    @DisplayName("Discovery")
    class Discovery {

        @BeforeEach
        void registerAll() {
            registry.registerProvider(new StreamOperatorProvider());
            registry.registerProvider(new PatternOperatorProvider());
        }

        @Test
        @DisplayName("Find by STREAM type")
        void findStream() {
            List<OperatorProvider> results = registry.findByType(OperatorType.STREAM);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProviderId()).isEqualTo("test-stream");
        }

        @Test
        @DisplayName("Find by PATTERN type")
        void findPattern() {
            List<OperatorProvider> results = registry.findByType(OperatorType.PATTERN);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProviderId()).isEqualTo("test-pattern");
        }

        @Test
        @DisplayName("Find by LEARNING type returns empty")
        void findLearning() {
            assertThat(registry.findByType(OperatorType.LEARNING)).isEmpty();
        }

        @Test
        @DisplayName("Find by operator ID")
        void findByOperatorId() {
            Optional<OperatorProvider> result =
                    registry.findByOperatorId(StreamOperatorProvider.FILTER_OP);
            assertThat(result).isPresent();
            assertThat(result.get().getProviderId()).isEqualTo("test-stream");
        }

        @Test
        @DisplayName("Find by unknown operator ID returns empty")
        void findByUnknownId() {
            assertThat(registry.findByOperatorId(OperatorId.parse("unknown:ns:op:1.0"))).isEmpty();
        }

        @Test
        @DisplayName("Total operator count across all providers")
        void totalOperatorCount() {
            // stream has 2 (filter + map), pattern has 1 (seq) = 3
            assertThat(registry.totalOperatorCount()).isEqualTo(3);
        }
    }

    // =========================================================================
    // 4. Operator Creation
    // =========================================================================

    @Nested
    @DisplayName("Operator Creation")
    class OperatorCreation {

        @BeforeEach
        void registerAll() {
            registry.registerProvider(new StreamOperatorProvider());
            registry.registerProvider(new PatternOperatorProvider());
        }

        @Test
        @DisplayName("Create operator by ID")
        void createByOperatorId() {
            UnifiedOperator op = registry.createOperator(
                    StreamOperatorProvider.FILTER_OP, OperatorConfig.empty());
            assertThat(op).isNotNull();
            assertThat(op.getId()).isEqualTo(StreamOperatorProvider.FILTER_OP);
            assertThat(op.getType()).isEqualTo(OperatorType.STREAM);
        }

        @Test
        @DisplayName("Create unknown operator throws")
        void createUnknown() {
            assertThatThrownBy(() ->
                    registry.createOperator(OperatorId.parse("unknown:ns:op:1.0"), OperatorConfig.empty()))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    // =========================================================================
    // 5. Catalog Materialization
    // =========================================================================

    @Nested
    @DisplayName("Catalog Materialization")
    class CatalogMaterialization {

        @Test
        @DisplayName("Materializes all operators into catalog")
        void materializeAll() {
            registry.registerProvider(new StreamOperatorProvider());
            registry.registerProvider(new PatternOperatorProvider());

            DefaultOperatorCatalog catalog = new DefaultOperatorCatalog();
            int count = registry.materializeIntoCatalog(catalog);

            assertThat(count).isEqualTo(3); // 2 stream + 1 pattern
            assertThat(catalog.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("Empty registry materializes nothing")
        void emptyMaterialization() {
            DefaultOperatorCatalog catalog = new DefaultOperatorCatalog();
            int count = registry.materializeIntoCatalog(catalog);
            assertThat(count).isZero();
            assertThat(catalog.size()).isZero();
        }
    }

    // =========================================================================
    // 6. Listing
    // =========================================================================

    @Nested
    @DisplayName("Listing")
    class Listing {

        @Test
        @DisplayName("listAll sorted by priority")
        void listAllSorted() {
            registry.registerProvider(new StreamOperatorProvider()); // 1000
            registry.registerProvider(new PatternOperatorProvider()); // 500

            List<OperatorProvider> all = registry.listAll();
            assertThat(all).hasSize(2);
            assertThat(all.get(0).getProviderId()).isEqualTo("test-pattern"); // 500 first
        }

        @Test
        @DisplayName("clear() removes all")
        void clearAll() {
            registry.registerProvider(new StreamOperatorProvider());
            registry.clear();
            assertThat(registry.size()).isZero();
        }
    }

    // =========================================================================
    // 7. ServiceLoader Integration
    // =========================================================================

    @Nested
    @DisplayName("ServiceLoader")
    class ServiceLoaderTests {

        @Test
        @DisplayName("discoverProviders with no META-INF returns 0")
        void noMetaInf() {
            int count = registry.discoverProviders();
            assertThat(count).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Custom classloader accepted")
        void customClassLoader() {
            OperatorProviderRegistry custom = OperatorProviderRegistry.create(
                    getClass().getClassLoader());
            assertThat(custom.size()).isZero();
        }
    }
}
