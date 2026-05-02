package com.ghatana.digitalmarketing.application.intake;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.intake.BusinessIntakeProfile;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository contract for draft and submitted intake questionnaire persistence.
 *
 * @doc.type interface
 * @doc.purpose DMOS intake questionnaire repository contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface IntakeQuestionnaireRepository {

    Promise<BusinessIntakeProfile> save(BusinessIntakeProfile profile);

    Promise<Optional<BusinessIntakeProfile>> findByWorkspaceId(DmWorkspaceId workspaceId);
}
