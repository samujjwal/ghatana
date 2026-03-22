package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import com.ghatana.tutorputor.contentgeneration.domain.LearningEvidence;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Generate supporting evidence items for claims
 * @doc.layer product
 * @doc.pattern Generator
 */
public class EvidenceGenerator {

    public Promise<List<LearningEvidence>> generateEvidence(List<LearningClaim> claims, ContentGenerationRequest request) {
        return Promise.of(claims.stream()
                .map(claim -> LearningEvidence.builder()
                        .id(UUID.randomUUID().toString())
                        .claimId(claim.getId())
                        .type("REFERENCE")
                        .content("Supporting evidence for " + claim.getText())
                        .build())
                .toList());
    }
}
