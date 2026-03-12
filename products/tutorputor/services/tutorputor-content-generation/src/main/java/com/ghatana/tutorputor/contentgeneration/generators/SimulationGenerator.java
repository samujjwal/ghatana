package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.tutorputor.explorer.model.ContentGenerationRequest;
import com.ghatana.tutorputor.explorer.model.LearningClaim;
import com.ghatana.tutorputor.explorer.model.SimulationManifest;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class SimulationGenerator {
    
    public Promise<List<SimulationManifest>> generateSimulations(List<LearningClaim> claims, ContentGenerationRequest request) {
        return Promise.ofBlocking(() -> {
            List<SimulationManifest> simulations = new ArrayList<>();
            for (LearningClaim claim : claims) {
                simulations.add(SimulationManifest.builder()
                    .id(UUID.randomUUID().toString())
                    .title("Simulation: " + claim.getText())
                    .description("Interactive simulation for " + claim.getText())
                    .domain(request.getDomain().toString())
                    .configuration(new HashMap<>())
                    .build());
            }
            return simulations;
        });
    }
}
