/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * REST controller for AI assistance endpoints.
 *
 * @doc.type class
 * @doc.purpose AI assistance REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class AIAssistController implements AsyncServlet {

    private final AIAssistService aiAssistService;
    private final PromptTemplateManager templateManager;
    private final ObjectMapper objectMapper;

    public AIAssistController(AIAssistService aiAssistService, PromptTemplateManager templateManager) {
        this.aiAssistService = aiAssistService;
        this.templateManager = templateManager;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        String path = request.getRelativePath();
        String method = request.getMethod().toString();

        if ("POST".equals(method) && path.endsWith("/ai/query")) {
            return processQuery(request);
        } else if ("POST".equals(method) && path.endsWith("/ai/sql")) {
            return generateSQL(request);
        } else if ("POST".equals(method) && path.endsWith("/ai/explain")) {
            return explainResults(request);
        } else if ("GET".equals(method) && path.endsWith("/ai/suggestions")) {
            return getSuggestions(request);
        } else if ("POST".equals(method) && path.endsWith("/ai/conversations")) {
            return createConversation(request);
        } else if ("GET".equals(method) && path.matches(".*/ai/conversations/[^/]+")) {
            return getConversation(request, extractId(path, "conversations"));
        } else if ("POST".equals(method) && path.matches(".*/ai/conversations/[^/]+/messages")) {
            return addMessage(request, extractId(path, "conversations"));
        } else if ("GET".equals(method) && path.endsWith("/ai/status")) {
            return getStatus(request);
        } else if ("GET".equals(method) && path.endsWith("/ai/templates")) {
            return listTemplates(request);
        } else if ("POST".equals(method) && path.endsWith("/ai/templates")) {
            return createTemplate(request);
        } else if ("POST".equals(method) && path.matches(".*/ai/templates/[^/]+/render")) {
            return renderTemplate(request, extractId(path, "templates"));
        }

        return notFound();
    }

    private Promise<HttpResponse> processQuery(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.asString(StandardCharsets.UTF_8));
                    String query = (String) bodyMap.get("query");
                    AIAssistService.QueryContext context = parseQueryContext(bodyMap);

                    return aiAssistService.processQuery(query, context)
                        .then(this::okJson);
                } catch (Exception e) {
                    return badRequest("Invalid request: " + e.getMessage());
                }
            });
    }

    private Promise<HttpResponse> generateSQL(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.asString(StandardCharsets.UTF_8));
                    String description = (String) bodyMap.get("description");
                    AIAssistService.DatabaseSchema schema = parseSchema(bodyMap);

                    return aiAssistService.generateSQL(description, schema)
                        .then(this::okJson);
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private Promise<HttpResponse> explainResults(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.asString(StandardCharsets.UTF_8));
                    String query = (String) bodyMap.get("query");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) bodyMap.get("results");
                    AIAssistService.QueryContext context = parseQueryContext(bodyMap);

                    return aiAssistService.explainResults(query, results, context)
                        .then(this::okJson);
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private Promise<HttpResponse> getSuggestions(HttpRequest request) {
        String tenantId = extractTenantId(request);
        String conversationId = request.getQueryParameter("conversationId");
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Integer.parseInt(limitParam) : 5;

        AIAssistService.QueryContext context = new AIAssistService.QueryContext(
            tenantId, null, conversationId, null, null, null, null
        );

        return aiAssistService.suggestQueries(context, limit)
            .then(suggestions -> okJson(Map.of("suggestions", suggestions)));
    }

    private Promise<HttpResponse> createConversation(HttpRequest request) {
        String tenantId = extractTenantId(request);
        String userId = request.getHeader(HttpHeaders.of("X-User-ID"));

        return aiAssistService.createConversation(tenantId, userId)
            .then(this::okJson);
    }

    private Promise<HttpResponse> getConversation(HttpRequest request, String conversationId) {
        return aiAssistService.getConversation(conversationId)
            .then(this::okJson);
    }

    private Promise<HttpResponse> addMessage(HttpRequest request, String conversationId) {
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.asString(StandardCharsets.UTF_8));
                    AIAssistService.Message message = parseMessage(bodyMap);

                    return aiAssistService.addMessage(conversationId, message)
                        .then(this::okJson);
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private Promise<HttpResponse> getStatus(HttpRequest request) {
        return aiAssistService.getStatus()
            .then(this::okJson);
    }

    private Promise<HttpResponse> listTemplates(HttpRequest request) {
        String tenantId = extractTenantId(request);
        String category = request.getQueryParameter("category");

        return templateManager.listTemplates(tenantId, category)
            .then(templates -> okJson(Map.of("templates", templates, "total", templates.size())));
    }

    private Promise<HttpResponse> createTemplate(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    PromptTemplateManager.PromptTemplate template = parseTemplate(body.asString(StandardCharsets.UTF_8));
                    return templateManager.registerTemplate(template)
                        .then(this::okJson);
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private Promise<HttpResponse> renderTemplate(HttpRequest request, String templateId) {
        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> variables = parseJson(body.asString(StandardCharsets.UTF_8));
                    return templateManager.render(templateId, variables)
                        .then(rendered -> okJson(Map.of("rendered", rendered)));
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private String extractId(String path, String segment) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (segment.equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return "";
    }

    private String extractTenantId(HttpRequest request) {
        return TenantExtractor.fromHttpOrThrow(request);
    }

    private AIAssistService.QueryContext parseQueryContext(Map<String, Object> bodyMap) {
        return new AIAssistService.QueryContext(
            (String) bodyMap.get("tenantId"),
            (String) bodyMap.get("userId"),
            (String) bodyMap.get("conversationId"),
            null, null, null, null
        );
    }

    private AIAssistService.DatabaseSchema parseSchema(Map<String, Object> bodyMap) {
        return new AIAssistService.DatabaseSchema("default", null);
    }

    private AIAssistService.Message parseMessage(Map<String, Object> bodyMap) {
        return new AIAssistService.Message(
            null,
            AIAssistService.MessageRole.USER,
            (String) bodyMap.get("content"),
            java.time.Instant.now(),
            Map.of()
        );
    }

    private PromptTemplateManager.PromptTemplate parseTemplate(String json) {
        return new PromptTemplateManager.PromptTemplate(
            "new-template", "New", "", "tenant-alpha", "general", "{{var}}",
            List.of("var"), null, Map.of(), 1, true
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"Failed to serialize response\"}";
        }
    }

    private Promise<HttpResponse> okJson(Object payload) {
        return Promise.of(HttpResponse.ok200().withJson(toJson(payload)).build());
    }

    private Promise<HttpResponse> badRequest(String message) {
        return Promise.of(HttpResponse.ofCode(400).withPlainText(message).build());
    }

    private Promise<HttpResponse> notFound() {
        return Promise.of(HttpResponse.ofCode(404).withPlainText("Not Found").build());
    }
}
