package com.ghatana.servicemanager.service;

/**
 * Configuration for an AEP service.
 * 
 * @doc.type class
 * @doc.purpose Service configuration model
 * @doc.layer orchestration
 * @doc.pattern Configuration
 */
public class ServiceConfiguration {
    
    private final String name;
    private final int port;
    private final String mainClass;
    private final boolean enabled;
    private final boolean required;
    private final String description;
    private final String[] jvmArgs;
    private final String[] environmentVars;

    private ServiceConfiguration(Builder builder) {
        this.name = builder.name;
        this.port = builder.port;
        this.mainClass = builder.mainClass;
        this.enabled = builder.enabled;
        this.required = builder.required;
        this.description = builder.description;
        this.jvmArgs = builder.jvmArgs.toArray(new String[0]);
        this.environmentVars = builder.environmentVars.toArray(new String[0]);
    }

    public String getName() { return name; }
    public int getPort() { return port; }
    public String getMainClass() { return mainClass; }
    public boolean isEnabled() { return enabled; }
    public boolean isRequired() { return required; }
    public String getDescription() { return description; }
    public String[] getJvmArgs() { return jvmArgs.clone(); }
    public String[] getEnvironmentVars() { return environmentVars.clone(); }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private int port;
        private String mainClass;
        private boolean enabled = false;
        private boolean required = false;
        private String description;
        private java.util.List<String> jvmArgs = new java.util.ArrayList<>();
        private java.util.List<String> environmentVars = new java.util.ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder jvmArg(String arg) {
            this.jvmArgs.add(arg);
            return this;
        }

        public Builder environmentVar(String var) {
            this.environmentVars.add(var);
            return this;
        }

        public ServiceConfiguration build() {
            return new ServiceConfiguration(this);
        }
    }

    @Override
    public String toString() {
        return String.format("ServiceConfiguration{name='%s', port=%d, enabled=%s, required=%s}",
                name, port, enabled, required);
    }
}
