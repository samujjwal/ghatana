package com.ghatana.datacloud.analytics;

import java.util.UUID;

public final class Report {
    private String id;
    private String name;
    private String status;

    public Report() {
        this.id = "report-" + UUID.randomUUID();
        this.status = "PENDING";
    }

    public String getId() {
        return id;
    }

    public Report withId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public Report withName(String name) {
        this.name = name;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public Report withStatus(String status) {
        this.status = status;
        return this;
    }
}
