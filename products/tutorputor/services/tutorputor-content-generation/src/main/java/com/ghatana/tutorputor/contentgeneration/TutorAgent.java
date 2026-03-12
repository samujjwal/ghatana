package com.ghatana.tutorputor.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI Tutor Agent for personalized learning interaction.
 * Uses LangChain4j declarative service definition.
 *
 * @doc.type agent
 * @doc.purpose Provide Socratic tutoring and explanations
 * @doc.layer service
 * @doc.pattern Agent
 */
public interface TutorAgent {

    /**
     * Conducts a tutoring session by responding to learner messages.
     * Uses Socratic method to guide learning.
     */
    @SystemMessage("""
            You are TutorPutor, an expert AI tutor specialized in Socratic teaching.
            Your goal is to help students learn deeply by asking guiding questions rather than just giving answers.
            Adapt your language to the learner's expertise level.
            Be encouraging, patient, and precise.
            """)
    String chat(@UserMessage String message);

    /**
     * Generates an explanation for a specific concept.
     */
    @SystemMessage("Explain the concept of {{concept}} to a student at {{level}} level. Use analogies.")
    String explain(@V("concept") String concept, @V("level") String level);

    /**
     * Generates a quiz question.
     */
    @SystemMessage("Generate a multiple-choice question about {{topic}} for {{level}} level. Output JSON format.")
    String generateQuiz(@V("topic") String topic, @V("level") String level);
}
