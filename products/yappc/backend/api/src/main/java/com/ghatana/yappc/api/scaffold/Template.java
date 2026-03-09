package com.ghatana.yappc.api.scaffold;

import java.util.List;
import java.util.Map;

/**
 * Template.
 *
 * @doc.type record
 * @doc.purpose template
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record Template(
    String id,
    String name,
    String description,
    String category,
    List<String> tags,
    String version,
    Map<String, Object> configSchema
) {
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public List<String> getTags() { return tags; }
    public String getVersion() { return version; }
    public Map<String, Object> getConfigSchema() { return configSchema; }
}
