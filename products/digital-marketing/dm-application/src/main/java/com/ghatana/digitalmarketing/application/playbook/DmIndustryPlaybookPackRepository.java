package com.ghatana.digitalmarketing.application.playbook;

import com.ghatana.digitalmarketing.domain.playbook.DmIndustryPlaybookPack;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for industry playbook pack persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for industry playbook pack storage (DMOS-F4-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmIndustryPlaybookPackRepository {

    Promise<DmIndustryPlaybookPack> save(DmIndustryPlaybookPack pack);

    Promise<DmIndustryPlaybookPack> update(DmIndustryPlaybookPack pack);

    Promise<Optional<DmIndustryPlaybookPack>> findById(String id);

    Promise<List<DmIndustryPlaybookPack>> listAll();

    Promise<List<DmIndustryPlaybookPack>> listByIndustry(String industry);

    Promise<List<DmIndustryPlaybookPack>> listPublished();
}
