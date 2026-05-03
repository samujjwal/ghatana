package com.ghatana.digitalmarketing.application.playbook;

import com.ghatana.digitalmarketing.domain.playbook.DmPlaybookVersion;
import com.ghatana.digitalmarketing.domain.playbook.DmPlaybookVersionStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for playbook version persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for playbook version storage (DMOS-F3-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmPlaybookVersionRepository {

    Promise<DmPlaybookVersion> save(DmPlaybookVersion version);

    Promise<DmPlaybookVersion> update(DmPlaybookVersion version);

    Promise<Optional<DmPlaybookVersion>> findById(String id);

    Promise<List<DmPlaybookVersion>> listByPlaybook(String tenantId, String playbookId);

    Promise<List<DmPlaybookVersion>> listByStatus(String tenantId, DmPlaybookVersionStatus status);
}
