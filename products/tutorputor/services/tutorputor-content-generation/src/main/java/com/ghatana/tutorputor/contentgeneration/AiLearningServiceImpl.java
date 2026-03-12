package com.ghatana.tutorputor.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.service.LLMService;
import com.ghatana.tutorputor.contracts.ai.AiLearningProto;
import com.ghatana.tutorputor.contracts.ai.AiLearningServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of AI Learning gRPC service.
 */
public class AiLearningServiceImpl extends AiLearningServiceGrpc.AiLearningServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(AiLearningServiceImpl.class);
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;

    public AiLearningServiceImpl(LLMService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
        this.promptTemplate = loadPromptTemplate();
    }

    private String loadPromptTemplate() {
        try (InputStream is = getClass().getResourceAsStream("/prompts/learning-path-generator.st")) {
            if (is == null) {
                logger.warn("Prompt /prompts/learning-path-generator.st not found, using fallback.");
                return "Generate a learning path for Subject: %s, Goal: %s, Level: %s (JSON)";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load prompt template", e);
            return "Generate a learning path for Subject: %s, Goal: %s, Level: %s (JSON)";
        }
    }

    @Override
    public void generateLearningPath(
            AiLearningProto.GeneratePathRequest request,
            StreamObserver<AiLearningProto.GeneratePathResponse> responseObserver) {
        String subject = request.getSubject().isBlank() ? "General" : request.getSubject();
        String goal = request.getGoal().isBlank() ? "Learning Goal" : request.getGoal();
        String learnerLevel = request.getLearnerLevel().isBlank() ? "Intermediate" : request.getLearnerLevel();

        String prompt = promptTemplate
                .replace("<subject>", subject)
                .replace("<goal>", goal)
                .replace("<learnerLevel>", learnerLevel);

        llmService.generate(prompt).whenComplete((generatedJson, error) -> {
            if (error != null) {
                logger.error("LLM generation failed", error);
                responseObserver.onError(error);
                return;
            }

            try {
                AiLearningProto.GeneratePathResponse response = parseResponse(generatedJson, subject, goal);
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } catch (Exception e) {
                logger.error("Failed to parse LLM response", e);
                // Fallback valid response
                responseObserver.onNext(createFallbackResponse(subject, goal));
                responseObserver.onCompleted();
            }
        });
    }

    private AiLearningProto.GeneratePathResponse parseResponse(String json, String subject, String goal) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        
        AiLearningProto.GeneratePathResponse.Builder builder = AiLearningProto.GeneratePathResponse.newBuilder()
                .setPathId(root.path("pathId").asText(UUID.randomUUID().toString()))
                .setTitle(root.path("title").asText(subject + " Mastery"))
                .setDescription(root.path("description").asText("AI-generated path for " + goal));

        if (root.has("nodes") && root.get("nodes").isArray()) {
            for (JsonNode node : root.get("nodes")) {
                AiLearningProto.LearningNode.Builder nodeBuilder = AiLearningProto.LearningNode.newBuilder()
                        .setId(node.path("id").asText())
                        .setTitle(node.path("title").asText())
                        .setDescription(node.path("description").asText())
                        .setEstimatedMinutes(node.path("estimatedMinutes").asInt(30));

                String type = node.path("type").asText("READING").toUpperCase();
                nodeBuilder.setType(type);

                if (node.has("prerequisites")) {
                    node.get("prerequisites").forEach(p -> nodeBuilder.addPrerequisites(p.asText()));
                }
                
                if (node.has("learningObjectives")) {
                    node.get("learningObjectives").forEach(obj -> nodeBuilder.addLearningObjectives(obj.asText()));
                }

                builder.addNodes(nodeBuilder);
            }
        }
        
        return builder.build();
    }

    private AiLearningProto.GeneratePathResponse createFallbackResponse(String subject, String goal) {
        return AiLearningProto.GeneratePathResponse.newBuilder()
                .setPathId(UUID.randomUUID().toString())
                .setTitle(subject + " Fallback Path")
                .setDescription("Fallback path for " + goal)
                .addNodes(AiLearningProto.LearningNode.newBuilder()
                        .setId("node-1")
                        .setTitle("Introduction")
                        .setType("READING")
                        .setDescription("Intro to " + subject)
                        .setEstimatedMinutes(15)
                        .build())
                .build();
    }
}
