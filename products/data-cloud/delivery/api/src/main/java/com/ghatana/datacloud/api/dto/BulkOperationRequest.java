package com.ghatana.datacloud.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * DTO for bulk operation requests in BulkController.
 *
 * <p>
 * Represents a list of entity payloads to be processed in a single
 * bulk create or update operation.</n>

 *
 * @doc.type class
 * @doc.purpose Bulk operation request
 * @doc.layer platform
 * @doc.pattern DataTransfer
*/
public class BulkOperationRequest {

    /** Maximum number of entities per bulk request to prevent unbounded memory use. */
    public static final int MAX_ENTITIES = 1_000;

    @JsonProperty("entities")
    @NotNull(message = "entities list must not be null")
    @NotEmpty(message = "entities list must not be empty")
    @Size(max = MAX_ENTITIES, message = "entities list must not exceed " + MAX_ENTITIES + " items")
    private List<Map<String, Object>> entities;

    public BulkOperationRequest() {
    }

    public BulkOperationRequest(List<Map<String, Object>> entities) {
        this.entities = entities;
    }

    public List<Map<String, Object>> getEntities() {
        return entities;
    }

    public void setEntities(List<Map<String, Object>> entities) {
        this.entities = entities;
    }
}
