package com.ghatana.softwareorg.engineering;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.domain.common.BaseSoftwareOrgDepartment;
import com.ghatana.platform.types.identity.Identifier;

import java.util.Map;

/**
 * Engineering Department for software-org.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides extension hooks for engineering-specific operations. Core behavior
 * (event types, workflows, agents) is defined in YAML configuration.
 *
 * <p>
 * <b>Extension Points</b><br>
 * - Task refinement logic
 * - Commit analysis customization
 * - Build result processing
 *
 * @doc.type class
 * @doc.purpose Engineering department extension hooks
 * @doc.layer product
 * @doc.pattern Extension Point
 */
public class EngineeringDepartment extends BaseSoftwareOrgDepartment {

    public static final String DEPARTMENT_TYPE = "ENGINEERING";
    public static final String DEPARTMENT_NAME = "Engineering";

    public EngineeringDepartment(AbstractOrganization organization, EventPublisher publisher) {
        super(organization, publisher, DEPARTMENT_NAME, DEPARTMENT_TYPE);
    }

    /**
     * Hook: Process a feature request by refining it into tasks.
     *
     * @param featureRequestJson serialized feature request
     * @return task ID
     */
    public String refineFeatureIntoTasks(byte[] featureRequestJson) {
        String taskId = Identifier.random().raw();
        String featureId = Identifier.random().raw();

        publishEvent("TaskRefined", newPayload()
                .withField("task_id", taskId)
                .withField("feature_id", featureId)
                .withField("title", "Refined task from feature request")
                .withTimestamp("created_at")
                .build());

        return taskId;
    }

    /**
     * Hook: Record a commit for analysis.
     *
     * @param commitSha git commit SHA
     * @param filePath  file path in commit
     * @return analysis result
     */
    public String analyzeCommit(String commitSha, String filePath) {
        publishEvent("CommitAnalyzed", newPayload()
                .withField("commit_sha", commitSha)
                .withField("file_path", filePath)
                .withField("analysis_status", "OK")
                .withTimestamp("created_at")
                .build());

        return "ANALYSIS_OK";
    }

    /**
     * Hook: Report build result.
     *
     * @param buildId build identifier
     * @param status  "SUCCESS" or "FAILURE"
     * @return confirmation
     */
    public String reportBuildResult(String buildId, String status) {
        String eventType = "SUCCESS".equals(status) ? "BuildSucceeded" : "BuildFailed";

        publishEvent(eventType, newPayload()
                .withField("build_id", buildId)
                .withField("status", status)
                .withTimestamp()
                .build());

        return "RECORDED";
    }
}
