package com.ghatana.kernel.interaction;

import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DefaultProductInteractionHandlerRegistry.
 *
 * @doc.type class
 * @doc.purpose Test handler registry discovery functionality
 * @doc.layer kernel
 */
@DisplayName("DefaultProductInteractionHandlerRegistry Tests")
class DefaultProductInteractionHandlerRegistryTest {

    @Test
    @DisplayName("Empty registry has no handlers")
    void emptyRegistryHasNoHandlers() {
        ProductInteractionHandlerRegistry registry = DefaultProductInteractionHandlerRegistry.empty();
        assertTrue(registry.isEmpty());
        assertEquals(0, registry.size());
        assertFalse(registry.hasHandler("any-contract"));
        assertNull(registry.getHandler("any-contract"));
        assertTrue(registry.registeredContractIds().isEmpty());
    }

    @Test
    @DisplayName("Builder creates registry with registered handlers")
    void builderCreatesRegistryWithHandlers() {
        TestHandler handler1 = new TestHandler("contract-1");
        TestHandler handler2 = new TestHandler("contract-2");

        ProductInteractionHandlerRegistry registry = DefaultProductInteractionHandlerRegistry.builder()
            .register(handler1)
            .register(handler2)
            .build();

        assertFalse(registry.isEmpty());
        assertEquals(2, registry.size());
        assertTrue(registry.hasHandler("contract-1"));
        assertTrue(registry.hasHandler("contract-2"));
        assertSame(handler1, registry.getHandler("contract-1"));
        assertSame(handler2, registry.getHandler("contract-2"));
        assertEquals(2, registry.registeredContractIds().size());
    }

    @Test
    @DisplayName("RegisterAll adds multiple handlers")
    void registerAllAddsMultipleHandlers() {
        TestHandler handler1 = new TestHandler("contract-1");
        TestHandler handler2 = new TestHandler("contract-2");
        TestHandler handler3 = new TestHandler("contract-3");

        ProductInteractionHandlerRegistry registry = DefaultProductInteractionHandlerRegistry.builder()
            .registerAll(handler1, handler2, handler3)
            .build();

        assertEquals(3, registry.size());
        assertTrue(registry.hasHandler("contract-1"));
        assertTrue(registry.hasHandler("contract-2"));
        assertTrue(registry.hasHandler("contract-3"));
    }

    @Test
    @DisplayName("RegisterAll from another registry copies handlers")
    void registerAllFromRegistryCopiesHandlers() {
        TestHandler handler1 = new TestHandler("contract-1");
        TestHandler handler2 = new TestHandler("contract-2");

        ProductInteractionHandlerRegistry sourceRegistry = DefaultProductInteractionHandlerRegistry.builder()
            .register(handler1)
            .register(handler2)
            .build();

        ProductInteractionHandlerRegistry targetRegistry = DefaultProductInteractionHandlerRegistry.builder()
            .registerAll(sourceRegistry)
            .build();

        assertEquals(2, targetRegistry.size());
        assertTrue(targetRegistry.hasHandler("contract-1"));
        assertTrue(targetRegistry.hasHandler("contract-2"));
    }

    @Test
    @DisplayName("Duplicate contract ID throws exception")
    void duplicateContractIdThrowsException() {
        TestHandler handler1 = new TestHandler("contract-1");
        TestHandler handler2 = new TestHandler("contract-1");

        assertThrows(IllegalArgumentException.class, () -> {
            DefaultProductInteractionHandlerRegistry.builder()
                .register(handler1)
                .register(handler2)
                .build();
        });
    }

    @Test
    @DisplayName("Null handler throws exception")
    void nullHandlerThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            DefaultProductInteractionHandlerRegistry.builder()
                .register(null)
                .build();
        });
    }

    @Test
    @DisplayName("Blank contract ID throws exception")
    void blankContractIdThrowsException() {
        TestHandler handler = new TestHandler("");

        assertThrows(IllegalArgumentException.class, () -> {
            DefaultProductInteractionHandlerRegistry.builder()
                .register(handler)
                .build();
        });
    }

    @Test
    @DisplayName("Registry returns immutable handler map")
    void registryReturnsImmutableHandlerMap() {
        TestHandler handler = new TestHandler("contract-1");
        ProductInteractionHandlerRegistry registry = DefaultProductInteractionHandlerRegistry.builder()
            .register(handler)
            .build();

        assertThrows(UnsupportedOperationException.class, () -> {
            registry.allHandlers().put("contract-2", new TestHandler("contract-2"));
        });
    }

    @Test
    @DisplayName("Registry returns immutable contract ID set")
    void registryReturnsImmutableContractIdSet() {
        TestHandler handler = new TestHandler("contract-1");
        ProductInteractionHandlerRegistry registry = DefaultProductInteractionHandlerRegistry.builder()
            .register(handler)
            .build();

        assertThrows(UnsupportedOperationException.class, () -> {
            registry.registeredContractIds().add("contract-2");
        });
    }

    private static class TestHandler implements ProductInteractionHandler<String, String> {
        private final String contractId;

        TestHandler(String contractId) {
            this.contractId = contractId;
        }

        @Override
        public String contractId() {
            return contractId;
        }

        @Override
        public Class<String> requestType() {
            return String.class;
        }

        @Override
        public Class<String> responseType() {
            return String.class;
        }

        @Override
        public Promise<ProductInteractionOutcome<String>> handle(ProductInteractionRequest<String> request) {
            return Promise.of(ProductInteractionOutcome.succeeded(request.interactionId(), List.of(), "response"));
        }
    }
}
