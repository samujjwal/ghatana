package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.ContentExample;
import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Generate simple worked examples for claims
 * @doc.layer product
 * @doc.pattern Generator
 */
public class ExampleGenerator {

    public Promise<List<ContentExample>> generateExamples(List<LearningClaim> claims, ContentGenerationRequest request) {
        return Promise.of(claims.stream()
                .map(claim -> ContentExample.builder()
                        .id(UUID.randomUUID().toString())
                        .claimId(claim.getId())
                        .title("Example for " + claim.getText())
                        .description("Worked example illustrating " + claim.getText())
                        .steps(List.of("Identify the concept", "Apply it", "Check the result"))
                        .visualAidDescription("A simple annotated diagram")
                        .gradeLevel(request.getGradeLevel())
                        .domain(request.getDomain().name())
                        .createdAt(System.currentTimeMillis())
                        .build())
                .toList());
    }
}
