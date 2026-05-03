package com.ghatana.digitalmarketing.application.playbook;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.playbook.DmIndustryPlaybookPack;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Application service for managing industry playbook packs.
 *
 * @doc.type interface
 * @doc.purpose Use-case boundary for industry playbook pack management (DMOS-F4-004)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmIndustryPlaybookPackService {

    Promise<DmIndustryPlaybookPack> create(DmOperationContext ctx, CreateIndustryPlaybookPackCommand cmd);

    Promise<DmIndustryPlaybookPack> publish(DmOperationContext ctx, String packId);

    Promise<Optional<DmIndustryPlaybookPack>> findById(DmOperationContext ctx, String packId);

    Promise<List<DmIndustryPlaybookPack>> listPublished(DmOperationContext ctx);

    Promise<List<DmIndustryPlaybookPack>> listByIndustry(DmOperationContext ctx, String industry);

    record CreateIndustryPlaybookPackCommand(
            String name,
            String industry,
            String description,
            String version,
            List<String> playbookIds
    ) {
        public CreateIndustryPlaybookPackCommand {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (industry == null || industry.isBlank()) throw new IllegalArgumentException("industry must not be blank");
            if (version == null || version.isBlank()) throw new IllegalArgumentException("version must not be blank");
            if (playbookIds == null) throw new IllegalArgumentException("playbookIds must not be null");
        }
    }
}
