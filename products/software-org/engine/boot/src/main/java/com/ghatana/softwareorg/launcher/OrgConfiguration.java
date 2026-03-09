package com.ghatana.softwareorg.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Organization configuration aggregation.
 *
 * <p><b>Purpose</b><br>
 * Aggregates all configuration entities for the software organization including
 * departments, personas, phases, stages, agents, workflows, operators, services,
 * integrations, flows, and KPIs. Immutable value object holding all loaded configs.
 *
 * @doc.type class
 * @doc.purpose Aggregated organization configuration holder
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class OrgConfiguration {

    private final String name;
    private final String version;
    private final String description;
    private final List<Map<String, Object>> personas;
    private final List<Map<String, Object>> departments;
    private final List<Map<String, Object>> phases;
    private final List<Map<String, Object>> stages;
    private final List<Map<String, Object>> agents;
    private final List<Map<String, Object>> workflows;
    private final List<Map<String, Object>> operators;
    private final List<Map<String, Object>> services;
    private final List<Map<String, Object>> integrations;
    private final List<Map<String, Object>> flows;
    private final List<Map<String, Object>> kpis;

    private OrgConfiguration(Builder builder) {
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.personas = Collections.unmodifiableList(new ArrayList<>(builder.personas));
        this.departments = Collections.unmodifiableList(new ArrayList<>(builder.departments));
        this.phases = Collections.unmodifiableList(new ArrayList<>(builder.phases));
        this.stages = Collections.unmodifiableList(new ArrayList<>(builder.stages));
        this.agents = Collections.unmodifiableList(new ArrayList<>(builder.agents));
        this.workflows = Collections.unmodifiableList(new ArrayList<>(builder.workflows));
        this.operators = Collections.unmodifiableList(new ArrayList<>(builder.operators));
        this.services = Collections.unmodifiableList(new ArrayList<>(builder.services));
        this.integrations = Collections.unmodifiableList(new ArrayList<>(builder.integrations));
        this.flows = Collections.unmodifiableList(new ArrayList<>(builder.flows));
        this.kpis = Collections.unmodifiableList(new ArrayList<>(builder.kpis));
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public List<Map<String, Object>> getPersonas() {
        return personas;
    }

    public List<Map<String, Object>> getDepartments() {
        return departments;
    }

    public List<Map<String, Object>> getPhases() {
        return phases;
    }

    public List<Map<String, Object>> getStages() {
        return stages;
    }

    public List<Map<String, Object>> getAgents() {
        return agents;
    }

    public List<Map<String, Object>> getWorkflows() {
        return workflows;
    }

    public List<Map<String, Object>> getOperators() {
        return operators;
    }

    public List<Map<String, Object>> getServices() {
        return services;
    }

    public List<Map<String, Object>> getIntegrations() {
        return integrations;
    }

    public List<Map<String, Object>> getFlows() {
        return flows;
    }

    public List<Map<String, Object>> getKpis() {
        return kpis;
    }

    /**
     * Builder for OrgConfiguration.
     */
    public static class Builder {
        private String name = "Software Organization";
        private String version = "1.0.0";
        private String description = "Agentic software organization simulation";
        private List<Map<String, Object>> personas = new ArrayList<>();
        private List<Map<String, Object>> departments = new ArrayList<>();
        private List<Map<String, Object>> phases = new ArrayList<>();
        private List<Map<String, Object>> stages = new ArrayList<>();
        private List<Map<String, Object>> agents = new ArrayList<>();
        private List<Map<String, Object>> workflows = new ArrayList<>();
        private List<Map<String, Object>> operators = new ArrayList<>();
        private List<Map<String, Object>> services = new ArrayList<>();
        private List<Map<String, Object>> integrations = new ArrayList<>();
        private List<Map<String, Object>> flows = new ArrayList<>();
        private List<Map<String, Object>> kpis = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder personas(List<Map<String, Object>> personas) {
            this.personas = personas;
            return this;
        }

        public Builder departments(List<Map<String, Object>> departments) {
            this.departments = departments;
            return this;
        }

        public Builder phases(List<Map<String, Object>> phases) {
            this.phases = phases;
            return this;
        }

        public Builder stages(List<Map<String, Object>> stages) {
            this.stages = stages;
            return this;
        }

        public Builder agents(List<Map<String, Object>> agents) {
            this.agents = agents;
            return this;
        }

        public Builder workflows(List<Map<String, Object>> workflows) {
            this.workflows = workflows;
            return this;
        }

        public Builder operators(List<Map<String, Object>> operators) {
            this.operators = operators;
            return this;
        }

        public Builder services(List<Map<String, Object>> services) {
            this.services = services;
            return this;
        }

        public Builder integrations(List<Map<String, Object>> integrations) {
            this.integrations = integrations;
            return this;
        }

        public Builder flows(List<Map<String, Object>> flows) {
            this.flows = flows;
            return this;
        }

        public Builder kpis(List<Map<String, Object>> kpis) {
            this.kpis = kpis;
            return this;
        }

        public OrgConfiguration build() {
            return new OrgConfiguration(this);
        }
    }

    @Override
    public String toString() {
        return "OrgConfiguration{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", personas=" + personas.size() +
                ", departments=" + departments.size() +
                ", phases=" + phases.size() +
                ", stages=" + stages.size() +
                ", agents=" + agents.size() +
                ", workflows=" + workflows.size() +
                ", operators=" + operators.size() +
                ", services=" + services.size() +
                ", integrations=" + integrations.size() +
                ", flows=" + flows.size() +
                ", kpis=" + kpis.size() +
                '}';
    }
}
