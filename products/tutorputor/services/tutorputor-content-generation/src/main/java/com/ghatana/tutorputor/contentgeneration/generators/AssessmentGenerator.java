package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.AssessmentItem;
import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Generate simple assessment items for claims
 * @doc.layer product
 * @doc.pattern Generator
 */
public class AssessmentGenerator {

    public Promise<List<AssessmentItem>> generateAssessments(List<LearningClaim> claims, ContentGenerationRequest request) {
        return Promise.of(claims.stream()
                .map(claim -> AssessmentItem.builder()
                        .id(UUID.randomUUID().toString())
                        .question("Which option best matches: " + claim.getText() + "?")
                        .options(List.of("Correct concept", "Distractor A", "Distractor B", "Distractor C"))
                        .correctAnswerIndex(0)
                        .build())
                .toList());
    }
}
