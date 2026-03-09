package com.ghatana.ingress.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.platform.core.exception.ValidationException;
import com.ghatana.platform.core.exception.ErrorCodeMappers;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import io.activej.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Global exception handler.
 *
 * @doc.type class
 * @doc.purpose Global exception handler
 * @doc.layer core
 * @doc.pattern Handler
 */

public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper mapper;

    /**
     * Creates a new GlobalExceptionHandler with a defensive copy of the provided ObjectMapper.
     * @param mapper The ObjectMapper to use for JSON serialization
     */
    public GlobalExceptionHandler(ObjectMapper mapper) {
        // Create a defensive copy to prevent external modification
        this.mapper = mapper.copy();
        // Configure the mapper as needed (add custom serializers, etc.)
        this.mapper.findAndRegisterModules();
    }

    public HttpResponse handleException(Throwable ex) {
        if (ex instanceof ValidationException ve) {
            try {
                ObjectNode node = mapper.createObjectNode();
                node.put("error", ErrorCodeMappers.fromIngress("INVALID_REQUEST").name());
                node.put("message", ve.getMessage());
                // node.set("details", mapper.valueToTree(ve.getDetails())); // TODO: getDetails() doesn't exist in core ValidationException
                return ResponseBuilder.status(422)
                    .rawJson(mapper.writeValueAsString(node))
                    .build();
            } catch (Exception e) {
                log.error("Failed to serialize validation error", e);
                String errorJson = String.format("{\"error\":\"%s\"}", ErrorCodeMappers.fromIngress("INVALID_REQUEST").name());
                return ResponseBuilder.status(422)
                    .text(errorJson)
                    .build();
            }
        }

        log.error("Unhandled exception", ex);
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("error", ErrorCodeMappers.fromIngress("INTERNAL_ERROR").name());
            node.put("message", "An unexpected error occurred");
            return ResponseBuilder.status(500)
                .rawJson(mapper.writeValueAsString(node))
                .build();
        } catch (Exception e) {
            String errorJson = String.format("{\"error\":\"%s\"}", ErrorCodeMappers.fromIngress("INTERNAL_ERROR").name());
            return ResponseBuilder.status(500)
                .text(errorJson)
                .build();
        }
    }
}
