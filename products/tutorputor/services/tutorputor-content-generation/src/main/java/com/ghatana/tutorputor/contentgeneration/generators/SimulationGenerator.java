package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import com.ghatana.tutorputor.contentgeneration.domain.SimulationManifest;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Generate simple simulation manifests for claims
 * @doc.layer product
 * @doc.pattern Generator
 */
public class SimulationGenerator {

    public Promise<List<SimulationManifest>> generateSimulations(List<LearningClaim> claims, ContentGenerationRequest request) {
        return Promise.of(claims.stream()
                .map(claim -> SimulationManifest.builder()
                        .id(UUID.randomUUID().toString())
                        .title("Simulation: " + claim.getText())
                        .description("Interactive exploration for " + claim.getText())
                        .domain(request.getDomain().name())
                        .configuration(Map.of("claimId", claim.getId()))
                        .build())
                .toList());
    }
}
