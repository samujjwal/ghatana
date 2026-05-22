package com.ghatana.kernel.interaction;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GenericServiceAdapter.
 *
 * @doc.type class
 * @doc.purpose Test generic service adapter functionality
 * @doc.layer kernel
 */
@DisplayName("GenericServiceAdapter Tests")
class GenericServiceAdapterTest {

    @Test
    @DisplayName("Successfully adapts kernel request to service call")
    void successfullyAdaptsKernelRequestToServiceCall() {
        Eventloop eventloop = Eventloop.create();
        
        GenericServiceAdapter<String, Integer, Integer, String> adapter = new GenericServiceAdapter.Builder<String, Integer, Integer, String>()
            .eventloop(eventloop)
            .serviceName("TestService")
            .requestTransformer(s -> Integer.parseInt(s))
            .serviceInvoker(i -> Promise.of(i * 2))
            .responseTransformer(r -> "result:" + r)
            .errorTransformer(e -> "error:" + e.getMessage())
            .build();

        String result = adapter.adapt("5").getResult();
        assertEquals("result:10", result);
    }

    @Test
    @DisplayName("Handles request transformation errors")
    void handlesRequestTransformationErrors() {
        Eventloop eventloop = Eventloop.create();
        
        GenericServiceAdapter<String, Integer, Integer, String> adapter = new GenericServiceAdapter.Builder<String, Integer, Integer, String>()
            .eventloop(eventloop)
            .serviceName("TestService")
            .requestTransformer(s -> { throw new RuntimeException("transform failed"); })
            .serviceInvoker(i -> Promise.of(i * 2))
            .responseTransformer(r -> "result:" + r)
            .errorTransformer(e -> "error:" + e.getMessage())
            .build();

        String result = adapter.adapt("invalid").getResult();
        assertEquals("error:transform failed", result);
    }

    @Test
    @DisplayName("Handles service invocation errors")
    void handlesServiceInvocationErrors() {
        Eventloop eventloop = Eventloop.create();
        
        GenericServiceAdapter<String, Integer, Integer, String> adapter = new GenericServiceAdapter.Builder<String, Integer, Integer, String>()
            .eventloop(eventloop)
            .serviceName("TestService")
            .requestTransformer(s -> Integer.parseInt(s))
            .serviceInvoker(i -> Promise.ofException(new RuntimeException("service failed")))
            .responseTransformer(r -> "result:" + r)
            .errorTransformer(e -> "error:" + e.getMessage())
            .build();

        String result = adapter.adapt("5").getResult();
        assertEquals("error:service failed", result);
    }

    @Test
    @DisplayName("Handles response transformation errors")
    void handlesResponseTransformationErrors() {
        Eventloop eventloop = Eventloop.create();
        
        GenericServiceAdapter<String, Integer, Integer, String> adapter = new GenericServiceAdapter.Builder<String, Integer, Integer, String>()
            .eventloop(eventloop)
            .serviceName("TestService")
            .requestTransformer(s -> Integer.parseInt(s))
            .serviceInvoker(i -> Promise.of(i * 2))
            .responseTransformer(r -> { throw new RuntimeException("response failed"); })
            .errorTransformer(e -> "error:" + e.getMessage())
            .build();

        String result = adapter.adapt("5").getResult();
        assertEquals("error:response failed", result);
    }

    @Test
    @DisplayName("Null kernel request throws exception")
    void nullKernelRequestThrowsException() {
        Eventloop eventloop = Eventloop.create();
        
        GenericServiceAdapter<String, Integer, Integer, String> adapter = new GenericServiceAdapter.Builder<String, Integer, Integer, String>()
            .eventloop(eventloop)
            .serviceName("TestService")
            .requestTransformer(s -> Integer.parseInt(s))
            .serviceInvoker(i -> Promise.of(i * 2))
            .responseTransformer(r -> "result:" + r)
            .errorTransformer(e -> "error:" + e.getMessage())
            .build();

        assertThrows(NullPointerException.class, () -> {
            adapter.adapt(null);
        });
    }

    @Test
    @DisplayName("Returns service name")
    void returnsServiceName() {
        Eventloop eventloop = Eventloop.create();
        
        GenericServiceAdapter<String, Integer, Integer, String> adapter = new GenericServiceAdapter.Builder<String, Integer, Integer, String>()
            .eventloop(eventloop)
            .serviceName("TestService")
            .requestTransformer(s -> Integer.parseInt(s))
            .serviceInvoker(i -> Promise.of(i * 2))
            .responseTransformer(r -> "result:" + r)
            .errorTransformer(e -> "error:" + e.getMessage())
            .build();

        assertEquals("TestService", adapter.serviceName());
    }

    @Test
    @DisplayName("Builder requires non-null eventloop")
    void builderRequiresNonNullEventloop() {
        assertThrows(NullPointerException.class, () -> {
            new GenericServiceAdapter.Builder<String, Integer, Integer, String>()
                .eventloop(null)
                .serviceName("TestService")
                .requestTransformer(s -> Integer.parseInt(s))
                .serviceInvoker(i -> Promise.of(i * 2))
                .responseTransformer(r -> "result:" + r)
                .errorTransformer(e -> "error:" + e.getMessage())
                .build();
        });
    }

    @Test
    @DisplayName("Builder requires non-null serviceName")
    void builderRequiresNonNullServiceName() {
        Eventloop eventloop = Eventloop.create();
        
        assertThrows(NullPointerException.class, () -> {
            new GenericServiceAdapter.Builder<String, Integer, Integer, String>()
                .eventloop(eventloop)
                .serviceName(null)
                .requestTransformer(s -> Integer.parseInt(s))
                .serviceInvoker(i -> Promise.of(i * 2))
                .responseTransformer(r -> "result:" + r)
                .errorTransformer(e -> "error:" + e.getMessage())
                .build();
        });
    }
}
