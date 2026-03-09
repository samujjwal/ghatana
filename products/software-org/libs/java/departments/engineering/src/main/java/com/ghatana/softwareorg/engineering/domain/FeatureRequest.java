package com.ghatana.softwareorg.engineering.domain;

/**
 * Represents a feature request in the engineering department.
 *
 * @doc.type class
 * @doc.purpose Feature request domain model
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class FeatureRequest {

    private final String id;
    private final String title;
    private final String description;
    private final String status;
    private final String priority;

    public FeatureRequest(String id, String title, String description, String status, String priority) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public String getPriority() {
        return priority;
    }
}
