package com.ghatana.digitalmarketing.application.transparency;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionStatus;
import com.ghatana.digitalmarketing.domain.transparency.AiActionType;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Application service for transparency and AI action timeline.
 *
 * @doc.type interface
 * @doc.purpose DMOS F1-025 transparency action log service
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface AiActionLogService {

    Promise<AiActionLogEntry> recordAction(DmOperationContext ctx, RecordActionCommand command);

    Promise<List<AiActionLogEntry>> listActions(DmOperationContext ctx, ListActionsQuery query);

    Promise<AiActionLogEntry> getAction(DmOperationContext ctx, String actionId);

    record RecordActionCommand(
            String correlationId,
            AiActionType actionType,
            AiActionStatus status,
            String actor,
            boolean initiatedByAi,
            String provider,
            String modelVersion,
            boolean humanEdited,
            Double confidence,
            List<String> evidenceLinks,
            List<String> policyChecks,
            String summary,
            String details,
            String relatedEntityId) {

                public RecordActionCommand(
                                String correlationId,
                                AiActionType actionType,
                                AiActionStatus status,
                                String actor,
                                boolean initiatedByAi,
                                Double confidence,
                                List<String> evidenceLinks,
                                List<String> policyChecks,
                                String summary,
                                String details,
                                String relatedEntityId) {
                        this(
                                correlationId,
                                actionType,
                                status,
                                actor,
                                initiatedByAi,
                                null,
                                null,
                                false,
                                confidence,
                                evidenceLinks,
                                policyChecks,
                                summary,
                                details,
                                relatedEntityId
                        );
                }
    }

    record ListActionsQuery(
            String correlationId,
            String relatedEntityId,
            int limit) {
    }
}
