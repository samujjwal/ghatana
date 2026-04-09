package com.ghatana.yappc.knowledge.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Extracts named entities and graph relationships from domain text using the shared YAPPC AI service.
 * @doc.layer product
 * @doc.pattern Extractor
 */
public class EntityExtractor {

  private final YAPPCAIService aiService;
  private final ObjectMapper objectMapper;

  public EntityExtractor(YAPPCAIService aiService) {
    this(aiService, new ObjectMapper());
  }

  public EntityExtractor(YAPPCAIService aiService, ObjectMapper objectMapper) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public Promise<List<ExtractedEntity>> extract(String text, String sourceType) {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(sourceType, "sourceType");
    return aiService.reason(buildPrompt(text, sourceType), buildContext(text, sourceType)).map(this::parseEntities);
  }

  private String buildPrompt(String text, String sourceType) {
    return "Extract entities and relationships from this "
        + sourceType
        + " content. Return a JSON array with name, type, description, and relations.\nContent:\n"
        + text;
  }

  private Map<String, Object> buildContext(String text, String sourceType) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("sourceType", sourceType);
    context.put("contentLength", text.length());
    return context;
  }

  private List<ExtractedEntity> parseEntities(String response) {
    if (response == null || response.isBlank()) {
      return List.of();
    }

    try {
      JsonNode root = objectMapper.readTree(response);
      if (!root.isArray()) {
        return List.of();
      }
      List<ExtractedEntity> entities = new java.util.ArrayList<>();
      for (JsonNode entry : root) {
        entities.add(parseEntity(entry));
      }
      return List.copyOf(entities);
    } catch (IOException exception) {
      return List.of();
    }
  }

  private ExtractedEntity parseEntity(JsonNode entry) {
    String name = textOrDefault(entry.get("name"), "Unnamed entity");
    String description = textOrDefault(entry.get("description"), name);
    EntityType type = parseType(textOrDefault(entry.get("type"), "CONCEPT"));
    List<ExtractedRelation> relations = parseRelations(entry.path("relations"));
    return new ExtractedEntity(name, type, description, relations);
  }

  private EntityType parseType(String value) {
    try {
      return EntityType.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return EntityType.CONCEPT;
    }
  }

  private List<ExtractedRelation> parseRelations(JsonNode root) {
    if (!root.isArray()) {
      return List.of();
    }
    List<ExtractedRelation> relations = new java.util.ArrayList<>();
    for (JsonNode relation : root) {
      String target = textOrDefault(relation.get("target"), "unknown-target");
      String type = textOrDefault(relation.get("type"), "USES");
      relations.add(new ExtractedRelation(target, type));
    }
    return List.copyOf(relations);
  }

  private String textOrDefault(JsonNode node, String fallback) {
    if (node == null || node.isNull()) {
      return fallback;
    }
    String value = node.asText();
    return value.isBlank() ? fallback : value;
  }

  public enum EntityType {
    REQUIREMENT,
    CODE_MODULE,
    CONCEPT,
    DECISION
  }

  public record ExtractedEntity(
      String name, EntityType type, String description, List<ExtractedRelation> relations) {

    public ExtractedEntity {
      name = Objects.requireNonNullElse(name, "Unnamed entity");
      type = type == null ? EntityType.CONCEPT : type;
      description = Objects.requireNonNullElse(description, name);
      relations = relations == null ? List.of() : List.copyOf(relations);
    }
  }

  public record ExtractedRelation(String target, String type) {

    public ExtractedRelation {
      target = Objects.requireNonNullElse(target, "unknown-target");
      type = Objects.requireNonNullElse(type, "USES");
    }
  }
}
