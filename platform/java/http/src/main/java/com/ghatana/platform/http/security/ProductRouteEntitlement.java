package com.ghatana.platform.http.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Kernel-owned typed contract for product route entitlement payloads.
 * <p>
 * Replaces Map<String, Object> with strongly-typed DTOs for type safety
 * and contract consistency across products.
 * </p>
 *
 * @doc.type class
 * @doc.purpose Typed contract for route entitlement responses
 * @doc.layer kernel
 * @doc.pattern DTO
 */
public final class ProductRouteEntitlement {

    private final String product;
    private final String principalId;
    private final String tenantId;
    private final String role;
    private final String persona;
    private final String tier;
    private final String correlationId;
    private final List<RouteEntitlement> routes;
    private final List<ActionEntitlement> actions;
    private final List<CardEntitlement> cards;

    public ProductRouteEntitlement(
            String product,
            String principalId,
            String tenantId,
            String role,
            String persona,
            String tier,
            String correlationId,
            List<RouteEntitlement> routes,
            List<ActionEntitlement> actions,
            List<CardEntitlement> cards) {
        this.product = Objects.requireNonNull(product, "product must not be null");
        this.principalId = Objects.requireNonNull(principalId, "principalId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.persona = persona;
        this.tier = tier;
        this.correlationId = correlationId;
        this.routes = List.copyOf(Objects.requireNonNull(routes, "routes must not be null"));
        this.actions = List.copyOf(Objects.requireNonNull(actions, "actions must not be null"));
        this.cards = List.copyOf(Objects.requireNonNull(cards, "cards must not be null"));
    }

    public String product() {
        return product;
    }

    public String principalId() {
        return principalId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String role() {
        return role;
    }

    public Optional<String> persona() {
        return Optional.ofNullable(persona);
    }

    public Optional<String> tier() {
        return Optional.ofNullable(tier);
    }

    public Optional<String> correlationId() {
        return Optional.ofNullable(correlationId);
    }

    public List<RouteEntitlement> routes() {
        return routes;
    }

    public List<ActionEntitlement> actions() {
        return actions;
    }

    public List<CardEntitlement> cards() {
        return cards;
    }

    public Map<String, Object> toMap() {
        return Map.of(
            "product", product,
            "principalId", principalId,
            "tenantId", tenantId,
            "role", role,
            "persona", persona,
            "tier", tier,
            "correlationId", correlationId,
            "routes", routes.stream().map(RouteEntitlement::toMap).toList(),
            "actions", actions.stream().map(ActionEntitlement::toMap).toList(),
            "cards", cards.stream().map(CardEntitlement::toMap).toList()
        );
    }

    public static final class RouteEntitlement {
        private final String path;
        private final String label;
        private final String minimumRole;
        private final List<String> personas;
        private final List<String> tiers;
        private final List<String> actions;
        private final List<String> cards;
        private final String capabilityKey;

        public RouteEntitlement(
                String path,
                String label,
                String minimumRole,
                List<String> personas,
                List<String> tiers,
                List<String> actions,
                List<String> cards,
                String capabilityKey) {
            this.path = Objects.requireNonNull(path, "path must not be null");
            this.label = Objects.requireNonNull(label, "label must not be null");
            this.minimumRole = Objects.requireNonNull(minimumRole, "minimumRole must not be null");
            this.personas = List.copyOf(Objects.requireNonNull(personas, "personas must not be null"));
            this.tiers = List.copyOf(Objects.requireNonNull(tiers, "tiers must not be null"));
            this.actions = List.copyOf(Objects.requireNonNull(actions, "actions must not be null"));
            this.cards = List.copyOf(Objects.requireNonNull(cards, "cards must not be null"));
            this.capabilityKey = capabilityKey;
        }

        public String path() {
            return path;
        }

        public String label() {
            return label;
        }

        public String minimumRole() {
            return minimumRole;
        }

        public List<String> personas() {
            return personas;
        }

        public List<String> tiers() {
            return tiers;
        }

        public List<String> actions() {
            return actions;
        }

        public List<String> cards() {
            return cards;
        }

        public Optional<String> capabilityKey() {
            return Optional.ofNullable(capabilityKey);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = Map.of(
                "path", path,
                "label", label,
                "minimumRole", minimumRole,
                "personas", personas,
                "tiers", tiers,
                "actions", actions,
                "cards", cards
            );
            if (capabilityKey != null) {
                Map<String, Object> withCapability = new java.util.HashMap<>(map);
                withCapability.put("capabilityKey", capabilityKey);
                return withCapability;
            }
            return map;
        }
    }

    public static final class ActionEntitlement {
        private final String id;
        private final String label;
        private final String routePath;

        public ActionEntitlement(String id, String label, String routePath) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            this.label = Objects.requireNonNull(label, "label must not be null");
            this.routePath = Objects.requireNonNull(routePath, "routePath must not be null");
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public String routePath() {
            return routePath;
        }

        public Map<String, Object> toMap() {
            return Map.of("id", id, "label", label, "routePath", routePath);
        }
    }

    public static final class CardEntitlement {
        private final String id;
        private final String title;
        private final String routePath;
        private final String surface;

        public CardEntitlement(String id, String title, String routePath, String surface) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            this.title = Objects.requireNonNull(title, "title must not be null");
            this.routePath = Objects.requireNonNull(routePath, "routePath must not be null");
            this.surface = Objects.requireNonNull(surface, "surface must not be null");
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        public String routePath() {
            return routePath;
        }

        public String surface() {
            return surface;
        }

        public Map<String, Object> toMap() {
            return Map.of("id", id, "title", title, "routePath", routePath, "surface", surface);
        }
    }
}
