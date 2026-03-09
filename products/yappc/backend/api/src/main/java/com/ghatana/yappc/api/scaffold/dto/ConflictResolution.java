package com.ghatana.yappc.api.scaffold.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * User decisions to resolve conflicts.
 * 
 * @doc.type record
 * @doc.purpose Conflict resolution strategies per file
 * @doc.layer product
 * @doc.pattern DTO
 */
public record ConflictResolution(
        @JsonProperty("resolutions") Map<String, String> resolutions) {
    
    // Resolution strategies: "KEEP_EXISTING", "USE_NEW", "MERGE", "SKIP"
    
    public boolean isValid() {
        return resolutions != null && !resolutions.isEmpty();
    }
}
