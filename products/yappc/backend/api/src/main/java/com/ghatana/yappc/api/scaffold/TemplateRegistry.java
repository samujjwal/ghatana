package com.ghatana.yappc.api.scaffold;

import java.util.List;
import java.util.Optional;

/**
 * TemplateRegistry.
 *
 * @doc.type interface
 * @doc.purpose template registry
 * @doc.layer product
 * @doc.pattern Registry
 */
public interface TemplateRegistry {
    List<Template> getAllTemplates();
    Optional<Template> getTemplate(String id);
    List<FeaturePack> getAllFeaturePacks();
    Optional<FeaturePack> getFeaturePack(String id);
}
