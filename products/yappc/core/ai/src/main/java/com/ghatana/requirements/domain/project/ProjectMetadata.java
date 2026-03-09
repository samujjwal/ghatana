package com.ghatana.requirements.domain.project;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable metadata for projects.
 *
 * <p><b>Purpose</b><br>
 * Holds project metadata for search, filtering, and categorization.
 * Supports tags, target release tracking, and custom metadata fields.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ProjectMetadata metadata = new ProjectMetadata(
 *     List.of("web", "mobile"),
 *     "platform",
 *     "2.0.0",
 *     Map.of("budget", "100k", "team_size", "5")
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - safe for concurrent use
 *
 * @doc.type record
 * @doc.purpose Project metadata container
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record ProjectMetadata(
    /**
     * List of tags for project categorization and search.
     */
    List<String> tags,

    /**
     * Project category/classification.
     */
    String category,

    /**
     * Target release version for this project.
     */
    String targetRelease,

    /**
     * Custom metadata fields as key-value pairs.
     */
    Map<String, String> customMetadata) {

  /**
   * Create ProjectMetadata with defensive copying.
   *
   * @param tags list of tags
   * @param category project category
   * @param targetRelease target release version
   * @param customMetadata custom fields map
   */
  public ProjectMetadata {
    // Defensive copy for immutability
    tags = List.copyOf(tags);
    customMetadata = Map.copyOf(customMetadata);
  }

  /**
   * Create empty/default metadata.
   *
   * @return new ProjectMetadata with defaults
   */
  public static ProjectMetadata empty() {
    return new ProjectMetadata(List.of(), "general", "", Map.of());
  }

  /**
   * Check if metadata contains a specific tag.
   *
   * @param tag tag to check
   * @return true if tag is present
   */
  public boolean hasTag(String tag) {
    return tags.contains(tag);
  }

  /**
   * Get unmodifiable view of tags.
   *
   * @return unmodifiable list of tags
   */
  public List<String> getTagsView() {
    return Collections.unmodifiableList(tags);
  }

  /**
   * Get unmodifiable view of custom metadata.
   *
   * @return unmodifiable map of metadata
   */
  public Map<String, String> getCustomMetadataView() {
    return Collections.unmodifiableMap(customMetadata);
  }

  /**
   * Get custom metadata value.
   *
   * @param key metadata key
   * @return metadata value or null if not found
   */
  public String getCustomValue(String key) {
    return customMetadata.get(key);
  }
}