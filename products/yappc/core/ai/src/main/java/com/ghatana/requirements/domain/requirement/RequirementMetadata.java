package com.ghatana.requirements.domain.requirement;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable metadata for requirements.
 *
 * <p><b>Purpose</b><br>
 * Holds requirement metadata for search, linking, and AI integration.
 * Supports tags, related requirements, acceptance criteria, and attachments.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RequirementMetadata metadata = new RequirementMetadata(
 *     List.of("authentication", "security", "user-management"),
 *     List.of("req-456", "req-789"),  // Related requirements
 *     "User can successfully log in with valid credentials",
 *     Map.of("complexity", "medium", "effort_hours", "8"),
 *     List.of("https://example.com/design.pdf")
 * );
 * }</pre>
 *
 * <p><b>Integration</b><br>
 * - Tags: Used for search, categorization, and reporting
 * - Related IDs: Track requirement dependencies
 * - Acceptance Criteria: Definition of done for requirement
 * - Custom Fields: Extensible for project-specific metadata
 * - Attachments: Supporting documents and specifications
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - completely thread-safe
 *
 * @doc.type record
 * @doc.purpose Requirement metadata container
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record RequirementMetadata(
    /**
     * Tags for categorization and search.
     */
    List<String> tags,

    /**
     * IDs of related requirements.
     */
    List<String> relatedRequirementIds,

    /**
     * Acceptance criteria for this requirement.
     */
    String acceptanceCriteria,

    /**
     * Custom metadata fields.
     */
    Map<String, String> customFields,

    /**
     * URLs to attachment documents.
     */
    List<String> attachmentUrls) {

  /**
   * Create requirement metadata with defensive copying.
   *
   * @param tags tags list
   * @param relatedRequirementIds related IDs
   * @param acceptanceCriteria acceptance criteria
   * @param customFields custom fields
   * @param attachmentUrls attachment URLs
   */
  public RequirementMetadata {
    // Defensive copy for immutability
    tags = List.copyOf(tags);
    relatedRequirementIds = List.copyOf(relatedRequirementIds);
    customFields = Map.copyOf(customFields);
    attachmentUrls = List.copyOf(attachmentUrls);
  }

  /**
   * Create empty metadata.
   *
   * @return new RequirementMetadata with defaults
   */
  public static RequirementMetadata empty() {
    return new RequirementMetadata(
        List.of(), List.of(), "", Map.of(), List.of());
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
   * Check if requirement has acceptance criteria.
   *
   * @return true if acceptance criteria is non-empty
   */
  public boolean hasAcceptanceCriteria() {
    return acceptanceCriteria != null && !acceptanceCriteria.isBlank();
  }

  /**
   * Check if requirement has related requirements.
   *
   * @return true if any related requirements exist
   */
  public boolean hasRelatedRequirements() {
    return !relatedRequirementIds.isEmpty();
  }

  /**
   * Check if requirement has attachments.
   *
   * @return true if any attachments exist
   */
  public boolean hasAttachments() {
    return !attachmentUrls.isEmpty();
  }

  /**
   * Get unmodifiable view of tags.
   *
   * @return unmodifiable list
   */
  public List<String> getTagsView() {
    return Collections.unmodifiableList(tags);
  }

  /**
   * Get unmodifiable view of related IDs.
   *
   * @return unmodifiable list
   */
  public List<String> getRelatedRequirementIdsView() {
    return Collections.unmodifiableList(relatedRequirementIds);
  }

  /**
   * Get unmodifiable view of custom fields.
   *
   * @return unmodifiable map
   */
  public Map<String, String> getCustomFieldsView() {
    return Collections.unmodifiableMap(customFields);
  }

  /**
   * Get unmodifiable view of attachment URLs.
   *
   * @return unmodifiable list
   */
  public List<String> getAttachmentUrlsView() {
    return Collections.unmodifiableList(attachmentUrls);
  }

  /**
   * Get custom field value.
   *
   * @param key field key
   * @return field value or null if not found
   */
  public String getCustomField(String key) {
    return customFields.get(key);
  }
}