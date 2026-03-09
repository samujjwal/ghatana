package com.ghatana.pipeline.registry.web;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.pipeline.registry.service.CapabilitiesService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * HTTP controller for runtime capabilities discovery.
 *
 * <p>Purpose: Provides admin endpoints that describe available schema formats,
 * encodings, connector types, and transforms in the current deployment.
 * Enables dynamic UI configuration based on platform capabilities.</p>
 *
 * @doc.type class
 * @doc.purpose REST endpoints for querying platform capabilities
 * @doc.layer product
 * @doc.pattern Controller
 * @since 2.0.0
 */
public class CapabilitiesController {

        private final CapabilitiesService capabilitiesService;

        @Inject
        public CapabilitiesController(CapabilitiesService capabilitiesService) {
                this.capabilitiesService = capabilitiesService;
        }

        /**
         * GET /admin/capabilities/schemas
         */
        public Promise<HttpResponse> getSchemaFormats(HttpRequest request) {
                Map<String, Object> body = capabilitiesService.getSchemaFormats();
                String json = toJson(body);
                return Promise.of(
                                ResponseBuilder.ok()
                                                .header("Content-Type", "application/json")
                                                .rawJson(json)
                                                .build());
        }

        /**
         * GET /admin/capabilities/encodings
         */
        public Promise<HttpResponse> getEncodings(HttpRequest request) {
                Map<String, Object> body = capabilitiesService.getEncodings();
                String json = toJson(body);
                return Promise.of(
                                ResponseBuilder.ok()
                                                .header("Content-Type", "application/json")
                                                .rawJson(json)
                                                .build());
        }

        /**
         * GET /admin/capabilities/connectors
         */
        public Promise<HttpResponse> getConnectors(HttpRequest request) {
                Map<String, Object> body = capabilitiesService.getConnectors();
                String json = toJson(body);
                return Promise.of(
                                ResponseBuilder.ok()
                                                .header("Content-Type", "application/json")
                                                .rawJson(json)
                                                .build());
        }

        /**
         * GET /admin/capabilities/transforms
         */
        public Promise<HttpResponse> getTransforms(HttpRequest request) {
                Map<String, Object> body = capabilitiesService.getTransforms();
                String json = toJson(body);
                return Promise.of(
                                ResponseBuilder.ok()
                                                .header("Content-Type", "application/json")
                                                .rawJson(json)
                                                .build());
        }

        private String toJson(Map<String, Object> body) {
                // Very small JSON builder to avoid introducing new dependencies here.
                // Keys are a single top-level key -> list; format matches the plan examples.
                if (body.containsKey("schemaFormats")) {
                        @SuppressWarnings("unchecked")
                        var list = (java.util.List<java.util.Map<String, Object>>) body.get("schemaFormats");
                        StringBuilder sb = new StringBuilder();
                        sb.append("{\"schemaFormats\":[");
                        for (int i = 0; i < list.size(); i++) {
                                var f = list.get(i);
                                sb.append("{\"id\":\"").append(f.get("id"))
                                                .append("\",\"display\":\"").append(f.get("display"))
                                                .append("\",\"enabled\":").append(f.get("enabled"));
                                sb.append("}");
                                if (i < list.size() - 1)
                                        sb.append(",");
                        }
                        sb.append("]}");
                        return sb.toString();
                }
                if (body.containsKey("encodings")) {
                        @SuppressWarnings("unchecked")
                        var list = (java.util.List<String>) body.get("encodings");
                        StringBuilder sb = new StringBuilder();
                        sb.append("{\"encodings\":[");
                        for (int i = 0; i < list.size(); i++) {
                                sb.append("\"").append(list.get(i)).append("\"");
                                if (i < list.size() - 1)
                                        sb.append(",");
                        }
                        sb.append("]}");
                        return sb.toString();
                }
                if (body.containsKey("connectors")) {
                        @SuppressWarnings("unchecked")
                        var list = (java.util.List<java.util.Map<String, Object>>) body.get("connectors");
                        StringBuilder sb = new StringBuilder();
                        sb.append("{\"connectors\":[");
                        for (int i = 0; i < list.size(); i++) {
                                var c = list.get(i);
                                sb.append("{\"type\":\"").append(c.get("type"))
                                                .append("\",\"direction\":\"").append(c.get("direction"))
                                                .append("\",\"enabled\":").append(c.get("enabled"));
                                sb.append("}");
                                if (i < list.size() - 1)
                                        sb.append(",");
                        }
                        sb.append("]}");
                        return sb.toString();
                }
                if (body.containsKey("transforms")) {
                        @SuppressWarnings("unchecked")
                        var list = (java.util.List<java.util.Map<String, Object>>) body.get("transforms");
                        StringBuilder sb = new StringBuilder();
                        sb.append("{\"transforms\":[");
                        for (int i = 0; i < list.size(); i++) {
                                var t = list.get(i);
                                sb.append("{\"id\":\"").append(t.get("id"))
                                                .append("\",\"description\":\"").append(t.get("description"))
                                                .append("\"}");
                                if (i < list.size() - 1)
                                        sb.append(",");
                        }
                        sb.append("]}");
                        return sb.toString();
                }
                // Fallback - should not normally be used
                return "{}";
        }
}
