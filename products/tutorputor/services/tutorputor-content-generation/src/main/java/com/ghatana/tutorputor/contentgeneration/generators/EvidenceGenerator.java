package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.tutorputor.explorer.model.ContentGenerationRequest;
import com.ghatana.tutorputor.explorer.model.LearningClaim;
import com.ghatana.tutorputor.explorer.model.LearningEvidence;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EvidenceGenerator {
    
    public Promise<List<LearningEvidence>> generateEvidence(List<LearningClaim> claims, ContentGenerationRequest request) {
        return Promise.ofBlocking(() -> {
            List<LearningEvidence> evidence = new ArrayList<>();
            for (LearningClaim claim : claims) {
                evidence.add(LearningEvidence.builder()
                    .id(UUID.randomUUID().toString())
                    .claimId(claim.getId())
                    .type("EXAMPLE")
                    .content("Evidence for: " + claim.getText())
                    .build());
            }
            return evidence;
        });
    }
}
