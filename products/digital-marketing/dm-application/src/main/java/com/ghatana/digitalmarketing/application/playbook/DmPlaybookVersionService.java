package com.ghatana.digitalmarketing.application.playbook;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.playbook.DmPlaybookVersion;
import com.ghatana.digitalmarketing.domain.playbook.DmPlaybookVersionStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for playbook version management.
 *
 * @doc.type interface
 * @doc.purpose Create, promote, and archive playbook versions (DMOS-F3-004)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmPlaybookVersionService {

    Promise<DmPlaybookVersion> create(DmOperationContext ctx, CreatePlaybookVersionCommand command);

    Promise<DmPlaybookVersion> promote(DmOperationContext ctx, String versionId);

    Promise<DmPlaybookVersion> archive(DmOperationContext ctx, String versionId);

    Promise<Optional<DmPlaybookVersion>> findById(DmOperationContext ctx, String versionId);

    Promise<List<DmPlaybookVersion>> listByPlaybook(DmOperationContext ctx, String playbookId);

    /**
     * Command to create a new playbook version in DRAFT status.
     */
    record CreatePlaybookVersionCommand(
        String playbookId,
        int versionNumber,
        String contentJson
    ) {
        public CreatePlaybookVersionCommand {
            Objects.requireNonNull(playbookId, "playbookId must not be null");
            Objects.requireNonNull(contentJson, "contentJson must not be null");
            if (playbookId.isBlank()) throw new IllegalArgumentException("playbookId must not be blank");
            if (versionNumber < 1) throw new IllegalArgumentException("versionNumber must be >= 1");
        }
    }
}
