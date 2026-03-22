package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.AnimationConfig;
import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Generate simple animation configs for claims
 * @doc.layer product
 * @doc.pattern Generator
 */
public class AnimationGenerator {

    public Promise<List<AnimationConfig>> generateAnimations(List<LearningClaim> claims, ContentGenerationRequest request) {
        return Promise.of(claims.stream()
                .map(claim -> AnimationConfig.builder()
                        .id(UUID.randomUUID().toString())
                        .title("Animation: " + claim.getText())
                        .keyframes(List.of("Start", "Middle", "End"))
                        .durationMs(5000)
                        .build())
                .toList());
    }
}
