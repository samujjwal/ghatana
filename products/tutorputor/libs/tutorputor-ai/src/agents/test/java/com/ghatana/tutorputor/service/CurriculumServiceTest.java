package com.ghatana.tutorputor.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for CurriculumService.
 *
 * @doc.type class
 * @doc.purpose Unit tests for curriculum management
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CurriculumService Tests")
class CurriculumServiceTest {

    private CurriculumService curriculumService;

    @BeforeEach
    void setUp() {
        curriculumService = new CurriculumService(new SimpleMeterRegistry());
        
        // Create a basic curriculum structure
        createBasicCurriculum();
    }

    private void createBasicCurriculum() {
        // Basic math topics
        curriculumService.createTopic(CurriculumService.Topic.builder()
            .id("numbers")
            .name("Number Basics")
            .subject("Mathematics")
            .gradeLevel(1)
            .estimatedMinutes(30)
            .build());
        
        curriculumService.createTopic(CurriculumService.Topic.builder()
            .id("addition")
            .name("Addition")
            .subject("Mathematics")
            .gradeLevel(1)
            .addPrerequisite("numbers")
            .estimatedMinutes(45)
            .build());
        
        curriculumService.createTopic(CurriculumService.Topic.builder()
            .id("subtraction")
            .name("Subtraction")
            .subject("Mathematics")
            .gradeLevel(1)
            .addPrerequisite("numbers")
            .estimatedMinutes(45)
            .build());
        
        curriculumService.createTopic(CurriculumService.Topic.builder()
            .id("multiplication")
            .name("Multiplication")
            .subject("Mathematics")
            .gradeLevel(2)
            .addPrerequisite("addition")
            .estimatedMinutes(60)
            .build());
        
        curriculumService.createTopic(CurriculumService.Topic.builder()
            .id("division")
            .name("Division")
            .subject("Mathematics")
            .gradeLevel(2)
            .addPrerequisite("subtraction")
            .addPrerequisite("multiplication")
            .estimatedMinutes(60)
            .build());
    }

    @Test
    @DisplayName("Should create and retrieve topic")
    void shouldCreateAndRetrieveTopic() {
        // GIVEN
        CurriculumService.Topic topic = CurriculumService.Topic.builder()
            .id("fractions")
            .name("Fractions")
            .description("Introduction to fractions")
            .subject("Mathematics")
            .gradeLevel(3)
            .addPrerequisite("division")
            .addStandard("CCSS.MATH.3.NF.A.1")
            .estimatedMinutes(90)
            .build();

        // WHEN
        curriculumService.createTopic(topic);
        CurriculumService.Topic retrieved = curriculumService.getTopic("fractions");

        // THEN
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.name()).isEqualTo("Fractions");
        assertThat(retrieved.prerequisites()).contains("division");
        assertThat(retrieved.standards()).contains("CCSS.MATH.3.NF.A.1");
    }

    @Test
    @DisplayName("Should reject topic with missing prerequisite")
    void shouldRejectTopicWithMissingPrerequisite() {
        // GIVEN
        CurriculumService.Topic topic = CurriculumService.Topic.builder()
            .id("advanced")
            .name("Advanced Topic")
            .subject("Mathematics")
            .addPrerequisite("nonexistent")
            .build();

        // WHEN/THEN
        assertThatThrownBy(() -> curriculumService.createTopic(topic))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Prerequisite not found");
    }

    @Test
    @DisplayName("Should get prerequisites for topic")
    void shouldGetPrerequisitesForTopic() {
        // WHEN
        List<CurriculumService.Topic> prereqs = curriculumService.getPrerequisites("division");

        // THEN
        assertThat(prereqs).hasSize(2);
        assertThat(prereqs).extracting(CurriculumService.Topic::id)
            .containsExactlyInAnyOrder("subtraction", "multiplication");
    }

    @Test
    @DisplayName("Should get dependent topics")
    void shouldGetDependentTopics() {
        // WHEN
        List<CurriculumService.Topic> dependents = curriculumService.getDependentTopics("numbers");

        // THEN
        assertThat(dependents).hasSize(2);
        assertThat(dependents).extracting(CurriculumService.Topic::id)
            .containsExactlyInAnyOrder("addition", "subtraction");
    }

    @Test
    @DisplayName("Should create valid learning path")
    void shouldCreateValidLearningPath() {
        // GIVEN
        CurriculumService.LearningPath path = new CurriculumService.LearningPath(
            "path-1",
            "Basic Math",
            "Learn basic arithmetic",
            List.of("numbers", "addition", "subtraction", "multiplication", "division"),
            240,
            Map.of()
        );

        // WHEN
        curriculumService.createLearningPath(path);
        CurriculumService.LearningPath retrieved = curriculumService.getLearningPath("path-1");

        // THEN
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.topicIds()).hasSize(5);
    }

    @Test
    @DisplayName("Should reject invalid learning path order")
    void shouldRejectInvalidLearningPathOrder() {
        // GIVEN - multiplication before addition (wrong order)
        CurriculumService.LearningPath path = new CurriculumService.LearningPath(
            "invalid-path",
            "Invalid Path",
            "Bad order",
            List.of("multiplication", "addition", "numbers"), // Wrong order!
            100,
            Map.of()
        );

        // WHEN/THEN
        assertThatThrownBy(() -> curriculumService.createLearningPath(path))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid order");
    }

    @Test
    @DisplayName("Should generate learning path with prerequisites")
    void shouldGenerateLearningPathWithPrerequisites() {
        // GIVEN - learner wants to learn division
        List<String> targetTopics = List.of("division");

        // WHEN
        CurriculumService.LearningPath path = 
            curriculumService.generateLearningPath(targetTopics, "learner-1");

        // THEN
        assertThat(path).isNotNull();
        assertThat(path.topicIds()).contains("division");
        // Should include all prerequisites
        assertThat(path.topicIds()).contains("numbers", "addition", "subtraction", "multiplication");
        
        // Prerequisites should come before dependents
        int numbersIdx = path.topicIds().indexOf("numbers");
        int additionIdx = path.topicIds().indexOf("addition");
        int multiplicationIdx = path.topicIds().indexOf("multiplication");
        int divisionIdx = path.topicIds().indexOf("division");
        
        assertThat(numbersIdx).isLessThan(additionIdx);
        assertThat(additionIdx).isLessThan(multiplicationIdx);
        assertThat(multiplicationIdx).isLessThan(divisionIdx);
    }

    @Test
    @DisplayName("Should track learner progress")
    void shouldTrackLearnerProgress() {
        // WHEN
        curriculumService.recordProgress("learner-1", "numbers", 0.9, 25);
        curriculumService.recordProgress("learner-1", "numbers", 0.95, 10); // Second attempt
        curriculumService.recordProgress("learner-1", "addition", 0.7, 30);

        // THEN
        CurriculumService.LearnerProgress numbersProgress = 
            curriculumService.getProgress("learner-1", "numbers");
        assertThat(numbersProgress).isNotNull();
        assertThat(numbersProgress.mastery()).isEqualTo(0.95); // Takes max
        assertThat(numbersProgress.timeSpentMinutes()).isEqualTo(35); // Cumulative
        assertThat(numbersProgress.attempts()).isEqualTo(2);
        
        Map<String, CurriculumService.LearnerProgress> allProgress = 
            curriculumService.getAllProgress("learner-1");
        assertThat(allProgress).hasSize(2);
    }

    @Test
    @DisplayName("Should check topic readiness")
    void shouldCheckTopicReadiness() {
        // GIVEN - learner has mastered numbers
        curriculumService.recordProgress("learner-2", "numbers", 0.85, 30);

        // WHEN - check readiness for addition (requires numbers)
        CurriculumService.TopicReadiness additionReadiness = 
            curriculumService.checkReadiness("learner-2", "addition");
        
        // AND check readiness for multiplication (requires addition)
        CurriculumService.TopicReadiness multiplicationReadiness = 
            curriculumService.checkReadiness("learner-2", "multiplication");

        // THEN
        assertThat(additionReadiness.ready()).isTrue();
        assertThat(additionReadiness.missingPrerequisites()).isEmpty();
        
        assertThat(multiplicationReadiness.ready()).isFalse();
        assertThat(multiplicationReadiness.missingPrerequisites())
            .extracting(CurriculumService.Topic::id)
            .contains("addition");
    }

    @Test
    @DisplayName("Should recommend next topics based on mastery")
    void shouldRecommendNextTopicsBasedOnMastery() {
        // GIVEN - learner has mastered numbers
        curriculumService.recordProgress("learner-3", "numbers", 0.9, 30);

        // WHEN
        List<CurriculumService.Topic> recommendations = 
            curriculumService.getRecommendedTopics("learner-3", 3);

        // THEN - should recommend addition and subtraction (both have numbers as prereq)
        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations).extracting(CurriculumService.Topic::id)
            .containsAnyOf("addition", "subtraction");
        // Should NOT recommend multiplication (requires addition which isn't mastered)
        assertThat(recommendations).extracting(CurriculumService.Topic::id)
            .doesNotContain("multiplication");
    }

    @Test
    @DisplayName("Should list topics by subject")
    void shouldListTopicsBySubject() {
        // WHEN
        List<CurriculumService.Topic> mathTopics = 
            curriculumService.listTopicsBySubject("Mathematics");

        // THEN
        assertThat(mathTopics).hasSize(5);
        assertThat(mathTopics).allMatch(t -> t.subject().equals("Mathematics"));
    }

    @Test
    @DisplayName("Should generate path excluding mastered topics")
    void shouldGeneratePathExcludingMasteredTopics() {
        // GIVEN - learner has already mastered numbers and addition
        curriculumService.recordProgress("learner-4", "numbers", 0.9, 30);
        curriculumService.recordProgress("learner-4", "addition", 0.85, 45);

        // WHEN - generate path to learn multiplication
        CurriculumService.LearningPath path = 
            curriculumService.generateLearningPath(List.of("multiplication"), "learner-4");

        // THEN - should not include already mastered topics
        assertThat(path.topicIds()).doesNotContain("numbers", "addition");
        assertThat(path.topicIds()).contains("multiplication");
    }
}
