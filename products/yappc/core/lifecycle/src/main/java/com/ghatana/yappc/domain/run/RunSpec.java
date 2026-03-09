package com.ghatana.yappc.domain.run;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Run specification for build/deploy/test
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record RunSpec(
    String id,
    String artifactsRef,
    List<RunTask> tasks,
    String environment,
    Map<String, String> config
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String artifactsRef;
        private List<RunTask> tasks = List.of();
        private String environment = "development";
        private Map<String, String> config = Map.of();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder artifactsRef(String artifactsRef) {
            this.artifactsRef = artifactsRef;
            return this;
        }
        
        public Builder tasks(List<RunTask> tasks) {
            this.tasks = tasks;
            return this;
        }
        
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }
        
        public Builder config(Map<String, String> config) {
            this.config = config;
            return this;
        }
        
        public RunSpec build() {
            return new RunSpec(id, artifactsRef, tasks, environment, config);
        }
    }
}
