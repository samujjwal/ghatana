package com.ghatana.softwareorg.hr;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.domain.common.BaseSoftwareOrgDepartment;
import com.ghatana.platform.types.identity.Identifier;

/**
 * HR Department for software-org.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides extension hooks for HR-specific operations. Core behavior
 * (event types, workflows, agents) is defined in YAML configuration.
 *
 * <p>
 * <b>Extension Points</b><br>
 * - Recruiting and hiring
 * - Employee onboarding
 * - Performance reviews
 * - Capacity planning
 *
 * @doc.type class
 * @doc.purpose HR department extension hooks
 * @doc.layer product
 * @doc.pattern Extension Point
 */
public class HrDepartment extends BaseSoftwareOrgDepartment {

    public static final String DEPARTMENT_TYPE = "HR";
    public static final String DEPARTMENT_NAME = "HR";

    public HrDepartment(AbstractOrganization org, EventPublisher publisher) {
        super(org, publisher, DEPARTMENT_NAME, DEPARTMENT_TYPE);
    }

    /**
     * Hook: Create new job opening.
     *
     * @param positionTitle  job title
     * @param department     department to hire for
     * @param seniorityLevel seniority level
     * @return job ID
     */
    public String createJobOpening(String positionTitle, String department, String seniorityLevel) {
        String jobId = Identifier.random().raw();

        publishEvent("JobOpeningCreated", newPayload()
                .withField("job_id", jobId)
                .withField("position_title", positionTitle)
                .withField("department", department)
                .withField("seniority_level", seniorityLevel)
                .withField("status", "CREATED")
                .withTimestamp()
                .build());

        return jobId;
    }

    /**
     * Hook: Update candidate pipeline stage.
     *
     * @param candidateId candidate to advance
     * @param newStage    new stage
     */
    public void advanceCandidateStage(String candidateId, String newStage) {
        publishEvent("CandidatePipelineStageChanged", newPayload()
                .withField("candidate_id", candidateId)
                .withField("new_stage", newStage)
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Onboard new employee.
     *
     * @param candidateId hired candidate
     * @param jobId       job opening
     * @return employee ID
     */
    public String onboardEmployee(String candidateId, String jobId) {
        String employeeId = Identifier.random().raw();

        publishEvent("EmployeeOnboarded", newPayload()
                .withField("employee_id", employeeId)
                .withField("candidate_id", candidateId)
                .withField("job_id", jobId)
                .withField("status", "ONBOARDED")
                .withTimestamp()
                .build());

        return employeeId;
    }

    /**
     * Hook: Complete performance review.
     *
     * @param employeeId employee being reviewed
     * @param rating     rating (1.0-5.0)
     */
    public void completePerformanceReview(String employeeId, float rating) {
        publishEvent("PerformanceReviewCompleted", newPayload()
                .withField("employee_id", employeeId)
                .withField("rating", rating)
                .withField("status", "COMPLETED")
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Update team capacity.
     *
     * @param department    department to update
     * @param currentCount  current headcount
     * @param targetCount   target headcount
     */
    public void updateTeamCapacity(String department, int currentCount, int targetCount) {
        publishEvent("TeamCapacityUpdated", newPayload()
                .withField("department", department)
                .withField("current_count", currentCount)
                .withField("target_count", targetCount)
                .withTimestamp()
                .build());
    }
}
