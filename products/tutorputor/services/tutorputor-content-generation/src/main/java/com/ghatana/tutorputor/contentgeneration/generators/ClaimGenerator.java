package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.Domain;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates a structured set of learning claims from a content generation request.
 *
 * <p>Claims are organised around three cognitive levels aligned to Bloom's taxonomy:
 * foundational (remember/understand), application (apply/analyse), and extension
 * (evaluate/create). Domain and grade level are reflected in claim text so that
 * downstream generators (assessment, simulation, animation) have meaningful context
 * to work from.
 *
 * <p>The generator is intentionally deterministic given the same request so that
 * repeated generation runs produce stable claim structures. External LLM integration
 * can be layered on top once the {@link com.ghatana.ai.llm.LLMGateway} wire is ready.
 *
 * @doc.type class
 * @doc.purpose Generate structured, domain-aware learning claims for a topic
 * @doc.layer product
 * @doc.pattern Generator
 */
public class ClaimGenerator {

    public Promise<List<LearningClaim>> generateClaims(ContentGenerationRequest request) {
        String topic = request.getTopic();
        Domain domain = request.getDomain();
                String gradeLevel = request.getGradeLevel();
        String domainLabel = domain.name().toLowerCase().replace('_', ' ');

        List<LearningClaim> claims = new ArrayList<>();

        // Foundational claim — recall / understand level
        LearningClaim foundational = LearningClaim.builder()
                .id(UUID.randomUUID().toString())
                .text("Understand the core principles of " + topic + " within " + domainLabel)
                .domain(domain.name())
                .gradeLevel(gradeLevel)
                .prerequisites(List.of())
                .build();
        claims.add(foundational);

        // Application claim — apply / analyse level, depends on foundational
        LearningClaim application = LearningClaim.builder()
                .id(UUID.randomUUID().toString())
                .text("Apply knowledge of " + topic + " to solve grade-" + gradeLevel + " " + domainLabel + " problems")
                .domain(domain.name())
                .gradeLevel(gradeLevel)
                .prerequisites(List.of(foundational.getId()))
                .build();
        claims.add(application);

        // Extension claim — evaluate / create level, depends on application
        LearningClaim extension = LearningClaim.builder()
                .id(UUID.randomUUID().toString())
                .text("Critically evaluate the significance and limitations of " + topic + " in " + domainLabel + " at grade " + gradeLevel)
                .domain(domain.name())
                .gradeLevel(gradeLevel)
                .prerequisites(List.of(application.getId()))
                .build();
        claims.add(extension);

        return Promise.of(List.copyOf(claims));
    }
}
