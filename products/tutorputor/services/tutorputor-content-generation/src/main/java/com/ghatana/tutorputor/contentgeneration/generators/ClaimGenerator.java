package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Generate baseline claims for a topic
 * @doc.layer product
 * @doc.pattern Generator
 */
public class ClaimGenerator {

    public Promise<List<LearningClaim>> generateClaims(ContentGenerationRequest request) {
        LearningClaim claim = LearningClaim.builder()
                .id(UUID.randomUUID().toString())
                .text("Understanding " + request.getTopic())
                .domain(request.getDomain().name())
                .gradeLevel(request.getGradeLevel())
                .prerequisites(List.of())
                .build();
        return Promise.of(List.of(claim));
    }
}
