package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for EntityValidationHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EntityValidationHandler")
@ExtendWith(MockitoExtension.class) // GH-90000
class EntityValidationHandlerTest extends EventloopTestBase {

    @Mock
    private EntitySchemaValidator schemaValidator;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private EntityValidationHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        handler = new EntityValidationHandler(schemaValidator, http); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("single validation rejects missing tenant before reading body")
    void singleValidationRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleValidateEntity(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
        verify(schemaValidator, never()).validate("default", "default", java.util.Map.of()); // GH-90000
    }

    @Test
    @DisplayName("batch validation rejects missing tenant before reading body")
    void batchValidationRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleBatchValidateEntities(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
        verify(schemaValidator, never()).validate("default", "default", java.util.Map.of()); // GH-90000
    }
}