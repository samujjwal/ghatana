package com.ghatana.platform.http.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Kernel route contract model for product route definitions.
 *
 * <p>This model represents the canonical route contract schema used across
 * all products in the Ghatana platform. It defines route metadata, stability,
 * and entitlements in a structured, type-safe manner.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel route contract model for product route definitions
 * @doc.layer platform
 * @doc.pattern Model
 */
@JsonDeserialize(builder = RouteContract.Builder.class)
public class RouteContract {

    private final String schemaVersion;
    private final String product;
    private final List<Route> routes;
    private final Map<String, Integer> roleOrder;

    private RouteContract(Builder builder) {
        this.schemaVersion = Objects.requireNonNull(builder.schemaVersion, "schemaVersion is required");
        this.product = Objects.requireNonNull(builder.product, "product is required");
        this.routes = Objects.requireNonNull(builder.routes, "routes is required");
        this.roleOrder = Objects.requireNonNull(builder.roleOrder, "roleOrder is required");
    }

    @JsonProperty("schemaVersion")
    public String schemaVersion() {
        return schemaVersion;
    }

    @JsonProperty("product")
    public String product() {
        return product;
    }

    @JsonProperty("routes")
    public List<Route> routes() {
        return routes;
    }

    @JsonProperty("roleOrder")
    public Map<String, Integer> roleOrder() {
        return roleOrder;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String schemaVersion;
        private String product;
        private List<Route> routes;
        private Map<String, Integer> roleOrder;

        @JsonProperty("schemaVersion")
        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        @JsonProperty("product")
        public Builder product(String product) {
            this.product = product;
            return this;
        }

        @JsonProperty("routes")
        public Builder routes(List<Route> routes) {
            this.routes = routes;
            return this;
        }

        @JsonProperty("roleOrder")
        public Builder roleOrder(Map<String, Integer> roleOrder) {
            this.roleOrder = roleOrder;
            return this;
        }

        public RouteContract build() {
            return new RouteContract(this);
        }
    }

    /**
     * Route definition within the contract.
     */
    @JsonDeserialize(builder = Route.Builder.class)
    public static class Route {

        private final String path;
        private final String label;
        private final String description;
        private final String group;
        private final String minimumRole;
        private final Set<String> personas;
        private final Set<String> tiers;
        private final Set<String> actions;
        private final Set<String> cards;
        private final Stability stability;
        private final String apiEndpoint;
        private final String policyId;
        private final String testId;

        private Route(Builder builder) {
            this.path = Objects.requireNonNull(builder.path, "path is required");
            this.label = Objects.requireNonNull(builder.label, "label is required");
            this.description = Objects.requireNonNull(builder.description, "description is required");
            this.group = Objects.requireNonNull(builder.group, "group is required");
            this.minimumRole = Objects.requireNonNull(builder.minimumRole, "minimumRole is required");
            this.personas = Objects.requireNonNull(builder.personas, "personas is required");
            this.tiers = Objects.requireNonNull(builder.tiers, "tiers is required");
            this.stability = Objects.requireNonNull(builder.stability, "stability is required");
            this.actions = builder.actions;
            this.cards = builder.cards;
            this.apiEndpoint = builder.apiEndpoint;
            this.policyId = builder.policyId;
            this.testId = builder.testId;
        }

        @JsonProperty("path")
        public String path() {
            return path;
        }

        @JsonProperty("label")
        public String label() {
            return label;
        }

        @JsonProperty("description")
        public String description() {
            return description;
        }

        @JsonProperty("group")
        public String group() {
            return group;
        }

        @JsonProperty("minimumRole")
        public String minimumRole() {
            return minimumRole;
        }

        @JsonProperty("personas")
        public Set<String> personas() {
            return personas;
        }

        @JsonProperty("tiers")
        public Set<String> tiers() {
            return tiers;
        }

        @JsonProperty("actions")
        public Set<String> actions() {
            return actions;
        }

        @JsonProperty("cards")
        public Set<String> cards() {
            return cards;
        }

        @JsonProperty("stability")
        public Stability stability() {
            return stability;
        }

        @JsonProperty("apiEndpoint")
        public String apiEndpoint() {
            return apiEndpoint;
        }

        @JsonProperty("policyId")
        public String policyId() {
            return policyId;
        }

        @JsonProperty("testId")
        public String testId() {
            return testId;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private String path;
            private String label;
            private String description;
            private String group;
            private String minimumRole;
            private Set<String> personas;
            private Set<String> tiers;
            private Set<String> actions;
            private Set<String> cards;
            private Stability stability;
            private String apiEndpoint;
            private String policyId;
            private String testId;

            @JsonProperty("path")
            public Builder path(String path) {
                this.path = path;
                return this;
            }

            @JsonProperty("label")
            public Builder label(String label) {
                this.label = label;
                return this;
            }

            @JsonProperty("description")
            public Builder description(String description) {
                this.description = description;
                return this;
            }

            @JsonProperty("group")
            public Builder group(String group) {
                this.group = group;
                return this;
            }

            @JsonProperty("minimumRole")
            public Builder minimumRole(String minimumRole) {
                this.minimumRole = minimumRole;
                return this;
            }

            @JsonProperty("personas")
            public Builder personas(Set<String> personas) {
                this.personas = personas;
                return this;
            }

            @JsonProperty("tiers")
            public Builder tiers(Set<String> tiers) {
                this.tiers = tiers;
                return this;
            }

            @JsonProperty("actions")
            public Builder actions(Set<String> actions) {
                this.actions = actions;
                return this;
            }

            @JsonProperty("cards")
            public Builder cards(Set<String> cards) {
                this.cards = cards;
                return this;
            }

            @JsonProperty("stability")
            public Builder stability(Stability stability) {
                this.stability = stability;
                return this;
            }

            @JsonProperty("apiEndpoint")
            public Builder apiEndpoint(String apiEndpoint) {
                this.apiEndpoint = apiEndpoint;
                return this;
            }

            @JsonProperty("policyId")
            public Builder policyId(String policyId) {
                this.policyId = policyId;
                return this;
            }

            @JsonProperty("testId")
            public Builder testId(String testId) {
                this.testId = testId;
                return this;
            }

            public Route build() {
                return new Route(this);
            }
        }
    }

    /**
     * Route stability level.
     */
    public enum Stability {
        STABLE("stable"),
        PREVIEW("preview"),
        BLOCKED("blocked"),
        HIDDEN("hidden");

        private final String value;

        Stability(String value) {
            this.value = value;
        }

        @JsonCreator
        public static Stability fromValue(String value) {
            for (Stability stability : values()) {
                if (stability.value.equals(value)) {
                    return stability;
                }
            }
            throw new IllegalArgumentException("Unknown stability: " + value);
        }

        public String getValue() {
            return value;
        }
    }
}
