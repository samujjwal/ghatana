package com.ghatana.datacloud.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonProperty("entities")
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
