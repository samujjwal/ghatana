package com.ghatana.flashit.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.config.JsonConfig;
import com.ghatana.flashit.agent.dto.*;
import com.ghatana.flashit.agent.util.PromptUtils;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge graph extraction and querying service.
 *
 * <p>Extracts topics, entities, and relationships from moments to build a
 * per-user knowledge graph. Supports three operations:
 * <ul>
 *   <li><b>extract</b> — Extract nodes and edges from new moments</li>
 *   <li><b>query</b> — Traverse the graph from a given node</li>
 *   <li><b>expand</b> — Discover new connections between existing nodes</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Extracts and queries knowledge graphs from user's moments
 * @doc.layer product
 * @doc.pattern Service
 */
public class KnowledgeGraphService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphService.class);

    private final OpenAIClient client;
    private final String model;

    public KnowledgeGraphService(OpenAIClient client, AgentConfig config) {
        this.client = client;
        this.model = config.getOpenAiModel();
    }

    /**
     * Extract topics, entities, and relationships from moments.
     *
     * @param request knowledge graph request with moments to process
     * @return extracted graph nodes and edges
     */
    public KnowledgeGraphResponse extractGraph(KnowledgeGraphRequest request) {
        long start = System.currentTimeMillis();
        log.info("Extracting knowledge graph for user={}, moments={}",
                request.userId(), request.moments() != null ? request.moments().size() : 0);

        String momentsSummary = PromptUtils.buildMomentsSummary(request.moments());

        String systemPrompt = """
                You are a knowledge graph extraction engine. Analyze the user's moments and extract:
                
                1. **Topic nodes**: Key themes, subjects, categories discussed
                2. **Entity nodes**: Named entities (people, places, organizations, concepts)
                3. **Edges**: Relationships between nodes
                
                Edge types: related_to, mentioned_in, co_occurs, part_of, caused_by, led_to
                
                Respond in JSON format:
                {
                  "nodes": [
                    {
                      "id": "generated-uuid-or-slug",
                      "name": "Node Name",
                      "nodeType": "topic|entity",
                      "entityType": "person|place|organization|concept|null",
                      "weight": 0.0-1.0,
                      "momentCount": 1,
                      "relatedNodeIds": ["id1", "id2"]
                    }
                  ],
                  "edges": [
                    {
                      "sourceId": "node-id-1",
                      "sourceType": "topic|entity",
                      "targetId": "node-id-2",
                      "targetType": "topic|entity",
                      "edgeType": "related_to|co_occurs|part_of|...",
                      "weight": 0.0-1.0,
                      "occurrences": 1
                    }
                  ]
                }
                
                Be thorough — extract ALL meaningful topics and entities. Assign higher weights
                to more prominent/recurring themes.
                """;

        String userPrompt = String.format("""
                Extract the knowledge graph from these moments:
                
                %s
                """, momentsSummary);

        try {
            ChatCompletion completion = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .addSystemMessage(systemPrompt)
                            .addUserMessage(userPrompt)
                            .temperature(0.3)
                            .build()
            );

            String content = completion.choices().getFirst().message().content().orElse("{}");
            long elapsed = System.currentTimeMillis() - start;

            return parseGraphResponse(content, elapsed);

        } catch (Exception e) {
            log.error("Knowledge graph extraction failed for user={}", request.userId(), e);
            long elapsed = System.currentTimeMillis() - start;
            return new KnowledgeGraphResponse(List.of(), List.of(), 0, 0, elapsed, model);
        }
    }

    /**
     * Query the knowledge graph from a starting node.
     *
     * @param request query request with starting node and traversal depth
     * @return subgraph reachable from the query node
     */
    public KnowledgeGraphResponse queryGraph(KnowledgeGraphRequest request) {
        long start = System.currentTimeMillis();
        log.info("Querying knowledge graph for user={}, queryNode={}, depth={}",
                request.userId(), request.queryNode(), request.depth());

        String momentsSummary = PromptUtils.buildMomentsSummary(request.moments());

        String systemPrompt = String.format("""
                You are a knowledge graph query engine. Given a starting concept "%s",
                find all related topics, entities, and connections up to depth %d.
                
                Respond in the same JSON format as graph extraction:
                {
                  "nodes": [...],
                  "edges": [...]
                }
                
                Focus on connections radiating from the query concept.
                """, request.queryNode(), request.depth());

        String userPrompt = String.format("""
                Context — user's moments:
                %s
                
                Starting from concept: "%s"
                Find related nodes and edges (max depth: %d, limit: %d)
                """, momentsSummary, request.queryNode(), request.depth(), request.limit());

        try {
            ChatCompletion completion = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .addSystemMessage(systemPrompt)
                            .addUserMessage(userPrompt)
                            .temperature(0.3)
                            .build()
            );

            String content = completion.choices().getFirst().message().content().orElse("{}");
            long elapsed = System.currentTimeMillis() - start;

            return parseGraphResponse(content, elapsed);

        } catch (Exception e) {
            log.error("Knowledge graph query failed for user={}", request.userId(), e);
            long elapsed = System.currentTimeMillis() - start;
            return new KnowledgeGraphResponse(List.of(), List.of(), 0, 0, elapsed, model);
        }
    }

    /**
     * Expand the graph by discovering new connections between existing nodes.
     *
     * @param request expand request with existing moments context
     * @return newly discovered edges and nodes
     */
    public KnowledgeGraphResponse expandGraph(KnowledgeGraphRequest request) {
        long start = System.currentTimeMillis();
        log.info("Expanding knowledge graph for user={}", request.userId());

        String momentsSummary = PromptUtils.buildMomentsSummary(request.moments());

        String systemPrompt = """
                You are a knowledge graph expansion engine. Analyze the user's moments and discover
                NEW, non-obvious connections between topics and entities that weren't explicitly stated.
                
                Focus on:
                - Cross-sphere connections (themes that span different life areas)
                - Temporal patterns (recurring topics over time)
                - Causal relationships (one event leading to another)
                - Hidden thematic threads
                
                Respond in the same graph JSON format with newly discovered nodes and edges.
                """;

        String userPrompt = String.format("""
                Discover new connections in these moments:
                
                %s
                
                Focus on non-obvious, insightful relationships.
                """, momentsSummary);

        try {
            ChatCompletion completion = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .addSystemMessage(systemPrompt)
                            .addUserMessage(userPrompt)
                            .temperature(0.5)
                            .build()
            );

            String content = completion.choices().getFirst().message().content().orElse("{}");
            long elapsed = System.currentTimeMillis() - start;

            return parseGraphResponse(content, elapsed);

        } catch (Exception e) {
            log.error("Knowledge graph expansion failed for user={}", request.userId(), e);
            long elapsed = System.currentTimeMillis() - start;
            return new KnowledgeGraphResponse(List.of(), List.of(), 0, 0, elapsed, model);
        }
    }

    private KnowledgeGraphResponse parseGraphResponse(String jsonContent, long elapsed) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdgeInfo> edges = new ArrayList<>();

        try {
            var mapper = JsonConfig.objectMapper();
            JsonNode tree = mapper.readTree(jsonContent);

            if (tree.has("nodes") && tree.get("nodes").isArray()) {
                for (JsonNode n : tree.get("nodes")) {
                    List<String> relatedIds = new ArrayList<>();
                    if (n.has("relatedNodeIds") && n.get("relatedNodeIds").isArray()) {
                        n.get("relatedNodeIds").forEach(id -> relatedIds.add(id.asText()));
                    }
                    nodes.add(new GraphNode(
                            n.path("id").asText(""),
                            n.path("name").asText(""),
                            n.path("nodeType").asText("topic"),
                            n.path("entityType").asText(null),
                            n.path("weight").asDouble(0.5),
                            n.path("momentCount").asInt(1),
                            relatedIds
                    ));
                }
            }

            if (tree.has("edges") && tree.get("edges").isArray()) {
                for (JsonNode e : tree.get("edges")) {
                    edges.add(new GraphEdgeInfo(
                            e.path("sourceId").asText(""),
                            e.path("sourceType").asText("topic"),
                            e.path("targetId").asText(""),
                            e.path("targetType").asText("topic"),
                            e.path("edgeType").asText("related_to"),
                            e.path("weight").asDouble(0.5),
                            e.path("occurrences").asInt(1)
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse knowledge graph JSON: {}", e.getMessage());
        }

        log.info("Parsed graph: {} nodes, {} edges in {}ms", nodes.size(), edges.size(), elapsed);
        return new KnowledgeGraphResponse(nodes, edges, nodes.size(), edges.size(), elapsed, model);
    }
}
