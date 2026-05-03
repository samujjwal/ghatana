package com.ghatana.digitalmarketing.application.email;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.email.DmEmailFollowUp;
import com.ghatana.digitalmarketing.domain.email.DmEmailFollowUpStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for email follow-up execution or safe CSV export.
 *
 * @doc.type interface
 * @doc.purpose Schedules, executes, and tracks email follow-up sends or safe exports (DMOS-F2-012)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmEmailFollowUpService {

    Promise<DmEmailFollowUp> schedule(DmOperationContext ctx, ScheduleEmailFollowUpCommand command);

    Promise<DmEmailFollowUp> markSent(DmOperationContext ctx, String followUpId, int sentCount, int failedCount);

    Promise<DmEmailFollowUp> markFailed(DmOperationContext ctx, String followUpId, String reason);

    Promise<DmEmailFollowUp> cancel(DmOperationContext ctx, String followUpId);

    Promise<Optional<DmEmailFollowUp>> findById(DmOperationContext ctx, String followUpId);

    Promise<List<DmEmailFollowUp>> listByStatus(DmOperationContext ctx, DmEmailFollowUpStatus status, int limit);

    /**
     * Command to schedule an email follow-up job.
     */
    record ScheduleEmailFollowUpCommand(
        String connectorId,
        List<String> recipientEmails,
        String subject,
        String bodyHtml
    ) {
        public ScheduleEmailFollowUpCommand {
            Objects.requireNonNull(connectorId, "connectorId must not be null");
            Objects.requireNonNull(recipientEmails, "recipientEmails must not be null");
            Objects.requireNonNull(subject, "subject must not be null");
            Objects.requireNonNull(bodyHtml, "bodyHtml must not be null");
            if (connectorId.isBlank()) throw new IllegalArgumentException("connectorId must not be blank");
            if (subject.isBlank()) throw new IllegalArgumentException("subject must not be blank");
            if (recipientEmails.isEmpty()) throw new IllegalArgumentException("recipientEmails must not be empty");
            recipientEmails = List.copyOf(recipientEmails);
        }
    }
}
