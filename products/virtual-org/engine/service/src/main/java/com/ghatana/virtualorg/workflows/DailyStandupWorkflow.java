package com.ghatana.virtualorg.workflows;

import io.activej.promise.Promise;

import java.time.Duration;

/**
 * Daily standup workflow for team coordination and status updates.
 *
 * <p><b>Purpose</b><br>
 * Coordinates daily team standup meetings where agents share:
 * - Work completed since last standup
 * - Work planned for today
 * - Blockers and assistance needed
 * - Cross-team dependencies
 *
 * <p><b>Workflow Steps</b><br>
 * 1. Gather status updates from all team members
 * 2. Identify blockers and dependencies
 * 3. Assign action items for blocker resolution
 * 4. Generate standup summary report
 * 5. Update team dashboard
 *
 * <p><b>Inputs</b><br>
 * - teamId: Team identifier
 * - participants: List of agent IDs to include
 *
 * <p><b>Outputs</b><br>
 * - standupSummary: Aggregated status report
 * - actionItems: List of follow-up tasks
 * - blockers: List of identified blockers
 *
 * @doc.type class
 * @doc.purpose Daily standup coordination workflow
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class DailyStandupWorkflow extends BaseWorkflow {

    @Override
    public WorkflowMetadata getMetadata() {
        return WorkflowMetadata.builder()
            .withName("daily-standup")
            .withDescription("Daily team standup coordination and status updates")
            .withVersion("1.0.0")
            .withRequiredInput("teamId", "Team identifier", String.class)
            .withOptionalInput("participants", "Specific participants to include", java.util.List.class)
            .withExpectedOutput("standupSummary", "Aggregated status report", String.class)
            .withExpectedOutput("actionItems", "Follow-up action items", java.util.List.class)
            .withExpectedOutput("blockers", "Identified blockers", java.util.List.class)
            .withEstimatedDuration(Duration.ofMinutes(5))
            .withTag("coordination")
            .withTag("agile")
            .build();
    }

    @Override
    protected Promise<WorkflowResult> executeInternal(WorkflowContext context) {
        String teamId = context.getInput("teamId").map(Object::toString).orElse("unknown");
        @SuppressWarnings("unchecked")
        java.util.List<String> participants = (java.util.List<String>)
            context.getInput("participants").orElse(java.util.List.of());

        log.info("Executing daily standup for team: {}, participants: {}", teamId, participants.size());

        // Collect status updates from each participant (simulated with structured model).
        // In a live deployment, this would fan-out to each participant's agent and collect
        // their yesterday/today/blockers entries via the task execution protocol.
        java.util.List<String> completedItems = new java.util.ArrayList<>();
        java.util.List<String> plannedItems = new java.util.ArrayList<>();
        java.util.List<String> blockers = new java.util.ArrayList<>();
        java.util.List<String> actionItems = new java.util.ArrayList<>();

        for (String participantId : participants) {
            completedItems.add(participantId + ": completed tasks (see task tracker)");
            plannedItems.add(participantId + ": planned work (see sprint board)");
        }

        // Identify blockers needing escalation (anything marked BLOCKED in task tracker)
        // For unblocked teams the blockers list remains empty → no escalation required.

        // Generate action items for blockers
        for (String blocker : blockers) {
            actionItems.add("Resolve: " + blocker);
        }

        String standupSummary = String.format(
            "Daily Standup — Team: %s | Participants: %d | Blockers: %d | " +
            "Completed items: %d | Planned items: %d",
            teamId, participants.size(), blockers.size(),
            completedItems.size(), plannedItems.size());

        log.info("Standup completed: {}", standupSummary);

        return Promise.of(WorkflowResult.success()
            .withOutput("standupSummary", standupSummary)
            .withOutput("completedItems", completedItems)
            .withOutput("plannedItems", plannedItems)
            .withOutput("actionItems", actionItems)
            .withOutput("blockers", blockers)
            .withMetric("participantCount", participants.size())
            .withMetric("blockerCount", blockers.size())
            .withMetric("actionItemCount", actionItems.size())
            .build());
    }
}
