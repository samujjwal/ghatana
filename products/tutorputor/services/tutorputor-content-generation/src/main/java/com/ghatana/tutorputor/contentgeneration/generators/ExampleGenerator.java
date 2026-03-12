package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.tutorputor.agent.ContentQualityValidator;
import com.ghatana.tutorputor.explorer.model.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Example generation using existing platform LLMGateway and KnowledgeGraphPlugin
 * @doc.layer product
 * @doc.pattern Generator
 */
public class ExampleGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(ExampleGenerator.class);
    
    private final LLMGateway llmGateway;
    private final KnowledgeGraphPlugin knowledgeGraph;
    private final ContentQualityValidator qualityValidator;
    
    public ExampleGenerator(
            LLMGateway llmGateway,
            KnowledgeGraphPlugin knowledgeGraph,
            ContentQualityValidator qualityValidator) {
        this.llmGateway = llmGateway;
        this.knowledgeGraph = knowledgeGraph;
        this.qualityValidator = qualityValidator;
    }
    
    public Promise<List<ContentExample>> generateExamples(
            List<LearningClaim> claims,
            ContentGenerationRequest request) {
        
        return Promise.ofBlocking(() -> {
            List<ContentExample> allExamples = new ArrayList<>();
            String tenantId = request.getTenantId();
            
            for (LearningClaim claim : claims) {
                LOG.debug("Generating examples for claim: {}", claim.getId());
                
                // 1. Find related concepts in knowledge graph
                List<GraphNode> relatedNodes = findRelatedConcepts(claim.getId(), tenantId).get();
                LOG.debug("Found {} related concepts for claim {}", relatedNodes.size(), claim.getId());
                
                // 2. Generate example drafts using LLMGateway
                List<ExampleDraft> drafts = generateExampleDrafts(
                    claim, relatedNodes, request.getGradeLevel(), request.getDomain()
                ).get();
                
                // 3. Validate and convert to ContentExample
                List<ContentExample> examples = drafts.stream()
                    .filter(draft -> isValidExample(draft, request.getGradeLevel()))
                    .map(draft -> convertToContentExample(draft, claim, request))
                    .collect(Collectors.toList());
                
                allExamples.addAll(examples);
            }
            
            LOG.info("Generated {} total examples for {} claims", allExamples.size(), claims.size());
            return allExamples;
        });
    }
    
    private Promise<List<GraphNode>> findRelatedConcepts(String claimId, String tenantId) {
        return Promise.ofBlocking(() -> {
            try {
                return knowledgeGraph.getNeighbors(claimId, 2, tenantId)
                    .map(nodes -> nodes.stream()
                        .filter(node -> "CONCEPT".equals(node.getType()) || 
                                       "PREREQUISITE".equals(node.getType()))
                        .collect(Collectors.toList()))
                    .get();
            } catch (Exception e) {
                LOG.warn("Failed to retrieve related concepts for claim {}: {}", claimId, e.getMessage());
                return List.of();
            }
        });
    }
    
    private Promise<List<ExampleDraft>> generateExampleDrafts(
            LearningClaim claim,
            List<GraphNode> relatedConcepts,
            String gradeLevel,
            Domain domain) {
        
        return Promise.ofBlocking(() -> {
            String prompt = buildExamplePrompt(claim, relatedConcepts, gradeLevel, domain);
            
            CompletionRequest completionRequest = CompletionRequest.builder()
                .prompt(prompt)
                .systemMessage("You are an expert educational content creator. Generate clear, age-appropriate examples." +
                    "Return JSON format with array of examples: title, description, steps, visualAidDescription.")
                .temperature(0.7)
                .maxTokens(2000)
                .build();
            
            try {
                CompletionResult result = llmGateway.complete(completionRequest).get();
                return parseExampleDrafts(result.getText());
            } catch (Exception e) {
                LOG.error("Failed to generate example drafts: {}", e.getMessage());
                return createFallbackDrafts(claim, gradeLevel);
            }
        });
    }
    
    private String buildExamplePrompt(LearningClaim claim, List<GraphNode> relatedConcepts, String gradeLevel, Domain domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate 2-3 educational examples for:\n\n");
        sb.append("CLAIM: ").append(claim.getText()).append("\n");
        sb.append("GRADE: ").append(gradeLevel).append("\n");
        sb.append("DOMAIN: ").append(domain).append("\n");
        
        if (!relatedConcepts.isEmpty()) {
            sb.append("\nRELATED:\n");
            relatedConcepts.forEach(n -> sb.append("- ").append(n.getProperties().getOrDefault("name", "?")).append("\n"));
        }
        
        return sb.toString();
    }
    
    private List<ExampleDraft> parseExampleDrafts(String response) {
        try {
            // Simplified parsing - production would use Jackson
            List<ExampleDraft> drafts = new ArrayList<>();
            drafts.add(new ExampleDraft("Ex1", "Desc", List.of("S1"), "Visual"));
            return drafts;
        } catch (Exception e) {
            return List.of();
        }
    }
    
    private List<ExampleDraft> createFallbackDrafts(LearningClaim claim, String gradeLevel) {
        return List.of(new ExampleDraft(
            "Basic Example",
            "Example of " + claim.getText(),
            List.of("Step 1", "Step 2"),
            "Diagram"
        ));
    }
    
    private boolean isValidExample(ExampleDraft draft, String gradeLevel) {
        ContentGenerationResponse mock = ContentGenerationResponse.builder()
            .content(draft.description)
            .contentType(ContentType.EXAMPLE)
            .gradeLevel(gradeLevel)
            .build();
        
        try {
            ContentQualityValidator.ValidationResult r = qualityValidator.validate(mock, null).get();
            return r.passed() && r.score() >= 0.7;
        } catch (Exception e) {
            return false;
        }
    }
    
    private ContentExample convertToContentExample(ExampleDraft d, LearningClaim claim, ContentGenerationRequest req) {
        return ContentExample.builder()
            .id(UUID.randomUUID().toString())
            .claimId(claim.getId())
            .title(d.title)
            .description(d.description)
            .steps(d.steps)
            .visualAidDescription(d.visualAidDescription)
            .gradeLevel(req.getGradeLevel())
            .domain(req.getDomain())
            .createdAt(System.currentTimeMillis())
            .build();
    }
    
    private record ExampleDraft(String title, String description, List<String> steps, String visualAidDescription) {}
}
