package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.tutorputor.explorer.model.AssessmentItem;
import com.ghatana.tutorputor.explorer.model.ContentGenerationRequest;
import com.ghatana.tutorputor.explorer.model.LearningClaim;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AssessmentGenerator {
    
    public Promise<List<AssessmentItem>> generateAssessments(List<LearningClaim> claims, ContentGenerationRequest request) {
        return Promise.ofBlocking(() -> {
            List<AssessmentItem> assessments = new ArrayList<>();
            for (LearningClaim claim : claims) {
                assessments.add(AssessmentItem.builder()
                    .id(UUID.randomUUID().toString())
                    .question("What is the main concept of: " + claim.getText() + "?")
                    .options(List.of("Option A", "Option B", "Option C", "Option D"))
                    .correctAnswerIndex(0)
                    .build());
            }
            return assessments;
        });
    }
}
