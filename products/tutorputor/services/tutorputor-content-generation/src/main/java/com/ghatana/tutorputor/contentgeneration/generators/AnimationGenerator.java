package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.tutorputor.explorer.model.AnimationConfig;
import com.ghatana.tutorputor.explorer.model.ContentGenerationRequest;
import com.ghatana.tutorputor.explorer.model.LearningClaim;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AnimationGenerator {
    
    public Promise<List<AnimationConfig>> generateAnimations(List<LearningClaim> claims, ContentGenerationRequest request) {
        return Promise.ofBlocking(() -> {
            List<AnimationConfig> animations = new ArrayList<>();
            for (LearningClaim claim : claims) {
                animations.add(AnimationConfig.builder()
                    .id(UUID.randomUUID().toString())
                    .title("Animation: " + claim.getText())
                    .keyframes(List.of("frame1", "frame2", "frame3"))
                    .durationMs(5000)
                    .build());
            }
            return animations;
        });
    }
}
