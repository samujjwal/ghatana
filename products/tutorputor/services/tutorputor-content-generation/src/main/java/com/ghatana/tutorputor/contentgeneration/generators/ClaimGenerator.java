package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.tutorputor.explorer.model.ContentGenerationRequest;
import com.ghatana.tutorputor.explorer.model.LearningClaim;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClaimGenerator {
    
    public Promise<List<LearningClaim>> generateClaims(ContentGenerationRequest request) {
        return Promise.ofBlocking(() -> {
            List<LearningClaim> claims = new ArrayList<>();
            // Stub implementation - would use LLM in production
            claims.add(LearningClaim.builder()
                .id(UUID.randomUUID().toString())
                .text("Understanding " + request.getTopic())
                .domain(request.getDomain().toString())
                .gradeLevel(request.getGradeLevel())
                .prerequisites(List.of())
                .build());
            return claims;
        });
    }
}
