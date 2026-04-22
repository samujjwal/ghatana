package com.ghatana.datacloud.analytics;

import java.util.UUID;

public final class Report {
    private String id;
    private String name;
    private String status;

    public Report() { // GH-90000
        this.id = "report-" + UUID.randomUUID(); // GH-90000
        this.status = "PENDING";
    }

    public String getId() { // GH-90000
        return id;
    }

    public Report withId(String id) { // GH-90000
        this.id = id;
        return this;
    }

    public String getName() { // GH-90000
        return name;
    }

    public Report withName(String name) { // GH-90000
        this.name = name;
        return this;
    }

    public String getStatus() { // GH-90000
        return status;
    }

    public Report withStatus(String status) { // GH-90000
        this.status = status;
        return this;
    }
}
