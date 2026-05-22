package com.ghatana.kernel.interaction;

import com.ghatana.kernel.context.DefaultKernelContext;
import com.ghatana.kernel.registry.ServiceRegistry;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ServiceDiscoveryProductInteractionHandlerRegistry.
 *
 * @doc.type class
 * @doc.purpose Test service-discovery-backed handler registry functionality
 * @doc.layer kernel
 */
@DisplayName("ServiceDiscoveryProductInteractionHandlerRegistry Tests")
class ServiceDiscoveryProductInteractionHandlerRegistryTest {

    @Test
    @DisplayName("Registry discovers handlers from service registry")
    void registryDiscoversHandlersFromServiceRegistry() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of(
            "product-interaction-handler.contract-1",
            "product-interaction-handler.contract-2"
        ));
        
        TestHandler handler1 = new TestHandler("contract-1");
        TestHandler handler2 = new TestHandler("contract-2");
        
        when(mockServiceRegistry.getService("product-interaction-handler.contract-1", ProductInteractionHandler.class))
            .thenReturn(Optional.of(handler1));
        when(mockServiceRegistry.getService("product-interaction-handler.contract-2", ProductInteractionHandler.class))
            .thenReturn(Optional.of(handler2));
        
        ServiceDiscoveryProductInteractionHandlerRegistry registry = 
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry).build();
        
        assertEquals(2, registry.size());
        assertTrue(registry.hasHandler("contract-1"));
        assertTrue(registry.hasHandler("contract-2"));
        assertSame(handler1, registry.getHandler("contract-1"));
        assertSame(handler2, registry.getHandler("contract-2"));
    }

    @Test
    @DisplayName("Registry validates contract ID matches service ID")
    void registryValidatesContractIdMatchesServiceId() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of("product-interaction-handler.contract-1"));
        
        TestHandler handler = new TestHandler("different-contract");
        
        when(mockServiceRegistry.getService("product-interaction-handler.contract-1", ProductInteractionHandler.class))
            .thenReturn(Optional.of(handler));
        
        assertThrows(IllegalStateException.class, () -> {
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry).build();
        });
    }

    @Test
    @DisplayName("Manual registration takes precedence over service discovery")
    void manualRegistrationTakesPrecedence() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of(
            "product-interaction-handler.contract-1"
        ));
        
        TestHandler serviceHandler = new TestHandler("contract-1");
        TestHandler manualHandler = new TestHandler("contract-1");
        
        when(mockServiceRegistry.getService("product-interaction-handler.contract-1", ProductInteractionHandler.class))
            .thenReturn(Optional.of(serviceHandler));
        
        ServiceDiscoveryProductInteractionHandlerRegistry registry = 
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry)
                .register(manualHandler)
                .build();
        
        assertEquals(1, registry.size());
        assertSame(manualHandler, registry.getHandler("contract-1"));
    }

    @Test
    @DisplayName("Duplicate contract ID between manual and service discovery throws exception")
    void duplicateContractIdThrowsException() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of(
            "product-interaction-handler.contract-1"
        ));
        
        TestHandler serviceHandler = new TestHandler("contract-1");
        TestHandler manualHandler = new TestHandler("contract-1");
        
        when(mockServiceRegistry.getService("product-interaction-handler.contract-1", ProductInteractionHandler.class))
            .thenReturn(Optional.of(serviceHandler));
        
        assertThrows(IllegalStateException.class, () -> {
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry)
                .register(serviceHandler)
                .build();
        });
    }

    @Test
    @DisplayName("Registry handles empty service registry")
    void registryHandlesEmptyServiceRegistry() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of());
        
        ServiceDiscoveryProductInteractionHandlerRegistry registry = 
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry).build();
        
        assertTrue(registry.isEmpty());
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("Registry ignores non-handler service IDs")
    void registryIgnoresNonHandlerServiceIds() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of(
            "some-other-service",
            "product-interaction-handler.contract-1"
        ));
        
        TestHandler handler = new TestHandler("contract-1");
        
        when(mockServiceRegistry.getService("product-interaction-handler.contract-1", ProductInteractionHandler.class))
            .thenReturn(Optional.of(handler));
        
        ServiceDiscoveryProductInteractionHandlerRegistry registry = 
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry).build();
        
        assertEquals(1, registry.size());
        assertTrue(registry.hasHandler("contract-1"));
    }

    @Test
    @DisplayName("RegisterAll adds multiple manual handlers")
    void registerAllAddsMultipleHandlers() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of());
        
        TestHandler handler1 = new TestHandler("contract-1");
        TestHandler handler2 = new TestHandler("contract-2");
        TestHandler handler3 = new TestHandler("contract-3");
        
        ServiceDiscoveryProductInteractionHandlerRegistry registry = 
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry)
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
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of());
        
        TestHandler handler1 = new TestHandler("contract-1");
        TestHandler handler2 = new TestHandler("contract-2");
        
        ProductInteractionHandlerRegistry sourceRegistry = DefaultProductInteractionHandlerRegistry.builder()
            .register(handler1)
            .register(handler2)
            .build();
        
        ServiceDiscoveryProductInteractionHandlerRegistry targetRegistry = 
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry)
                .registerAll(sourceRegistry)
                .build();
        
        assertEquals(2, targetRegistry.size());
        assertTrue(targetRegistry.hasHandler("contract-1"));
        assertTrue(targetRegistry.hasHandler("contract-2"));
    }

    @Test
    @DisplayName("Null service registry throws exception")
    void nullServiceRegistryThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(null);
        });
    }

    @Test
    @DisplayName("Null handler throws exception")
    void nullHandlerThrowsException() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of());
        
        assertThrows(NullPointerException.class, () -> {
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry)
                .register(null)
                .build();
        });
    }

    @Test
    @DisplayName("Blank contract ID throws exception")
    void blankContractIdThrowsException() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of());
        
        TestHandler handler = new TestHandler("");
        
        assertThrows(IllegalArgumentException.class, () -> {
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry)
                .register(handler)
                .build();
        });
    }

    @Test
    @DisplayName("Registry returns immutable handler map")
    void registryReturnsImmutableHandlerMap() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of());
        
        TestHandler handler = new TestHandler("contract-1");
        
        ServiceDiscoveryProductInteractionHandlerRegistry registry = 
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry)
                .register(handler)
                .build();
        
        assertThrows(UnsupportedOperationException.class, () -> {
            registry.allHandlers().put("contract-2", new TestHandler("contract-2"));
        });
    }

    @Test
    @DisplayName("Registry returns immutable contract ID set")
    void registryReturnsImmutableContractIdSet() {
        ServiceRegistry mockServiceRegistry = mock(ServiceRegistry.class);
        com.ghatana.kernel.context.KernelContext mockContext = mock(com.ghatana.kernel.context.KernelContext.class);
        
        when(mockServiceRegistry.getKernelContext()).thenReturn(mockContext);
        when(mockServiceRegistry.getServiceIds()).thenReturn(Set.of());
        
        TestHandler handler = new TestHandler("contract-1");
        
        ServiceDiscoveryProductInteractionHandlerRegistry registry = 
            ServiceDiscoveryProductInteractionHandlerRegistry.builder(mockServiceRegistry)
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
