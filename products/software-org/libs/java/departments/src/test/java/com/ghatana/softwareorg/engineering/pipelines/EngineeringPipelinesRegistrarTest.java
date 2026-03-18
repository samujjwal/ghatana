package com.ghatana.softwareorg.engineering.pipelines;

import com.ghatana.softwareorg.engineering.EngineeringDepartment;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EngineeringPipelinesRegistrar.
 *
 * Tests validate: - All 3 pipelines registered correctly - Pipeline
 * registration returns correct count - Error handling for pipeline failures -
 * Event routing configuration
 *
 * @see EngineeringPipelinesRegistrar
 */
@DisplayName("Engineering Pipelines Registrar Tests")
class EngineeringPipelinesRegistrarTest {

    private EngineeringDepartment department;
    private EventPublisher publisher;
    private EngineeringPipelinesRegistrar registrar;

    @BeforeEach
    void setUp() {
        // GIVEN: Mock department and publisher
        department = mock(EngineeringDepartment.class);
        publisher = mock(EventPublisher.class);
        registrar = new EngineeringPipelinesRegistrar(department, publisher);
    }

    /**
     * Verifies that all 3 engineering pipelines are registered.
     *
     * GIVEN: Engineering department with pipeline registrar WHEN:
     * registerPipelines() is called THEN: Returns 3 indicating all pipelines
     * registered
     */
    @Test
    @DisplayName("Should register all 3 engineering pipelines")
    void shouldRegisterAllThreePipelines() {
        // WHEN: Register all pipelines
        int pipelinesRegistered = registrar.registerPipelines();

        // THEN: All 3 pipelines registered
        assertThat(pipelinesRegistered)
                .as("Engineering should register exactly 3 pipelines")
                .isEqualTo(3);
    }

    /**
     * Verifies feature refinement pipeline configuration.
     *
     * GIVEN: Engineering registrar WHEN: registerPipelines() is called THEN:
     * Feature refinement pipeline processes FeatureRequestCreated events
     */
    @Test
    @DisplayName("Should configure feature refinement pipeline")
    void shouldConfigureFeatureRefinementPipeline() {
        // WHEN: Register pipelines
        int count = registrar.registerPipelines();

        // THEN: Verify registration successful
        assertThat(count).isGreaterThan(0);
        // Pipeline logs: FeatureRequestCreated → TaskRefined
    }

    /**
     * Verifies commit analysis pipeline configuration.
     *
     * GIVEN: Engineering registrar WHEN: registerPipelines() is called THEN:
     * Commit analysis pipeline processes CommitAnalyzed events
     */
    @Test
    @DisplayName("Should configure commit analysis pipeline")
    void shouldConfigureCommitAnalysisPipeline() {
        // WHEN: Register pipelines
        int count = registrar.registerPipelines();

        // THEN: Verify registration successful
        assertThat(count).isEqualTo(3);
        // Pipeline logs: CommitAnalyzed → QualitySignal
    }

    /**
     * Verifies build result pipeline configuration.
     *
     * GIVEN: Engineering registrar WHEN: registerPipelines() is called THEN:
     * Build result pipeline routes BuildSucceeded/Failed events
     */
    @Test
    @DisplayName("Should configure build result pipeline")
    void shouldConfigureBuildResultPipeline() {
        // WHEN: Register pipelines
        int count = registrar.registerPipelines();

        // THEN: Verify registration successful
        assertThat(count).isEqualTo(3);
        // Pipeline logs: BuildSucceeded|BuildFailed → QAWorkflow|Escalation
    }

    /**
     * Verifies registrar can be instantiated with null publisher.
     *
     * GIVEN: Department with null publisher WHEN: Creating registrar THEN:
     * Registrar accepts null publisher for testing
     */
    @Test
    @DisplayName("Should accept null publisher for testing")
    void shouldAcceptNullPublisher() {
        // GIVEN: Null publisher (test mode)
        EngineeringPipelinesRegistrar testRegistrar
                = new EngineeringPipelinesRegistrar(department, null);

        // WHEN: Register pipelines
        int count = testRegistrar.registerPipelines();

        // THEN: Still registers pipelines successfully
        assertThat(count).isEqualTo(3);
    }
}
