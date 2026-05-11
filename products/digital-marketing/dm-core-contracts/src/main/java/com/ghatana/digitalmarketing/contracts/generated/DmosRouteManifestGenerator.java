package com.ghatana.digitalmarketing.contracts.generated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Code generator for DMOS route/action/capability artifacts.
 * 
 * <p>This generator reads the canonical YAML manifest and produces:
 * <ul>
 *   <li>Java enum for capabilities</li>
 *   <li>Java registry for action-to-role mappings</li>
 *   <li>TypeScript route manifest</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Code generator for canonical route/action/capability artifacts
 * @doc.layer product
 * @doc.pattern Code Generator
 */
public final class DmosRouteManifestGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DmosRouteManifestGenerator.class);

    // Paths relative to the dm-core-contracts module directory
    // Paths relative to the dm-core-contracts module directory
    private static final String MANIFEST_PATH = "src/main/resources/dmos-route-manifest.yaml";
    private static final String JAVA_OUTPUT_DIR = "src/main/java/com/ghatana/digitalmarketing/contracts/generated";
    private static final String SECURITY_OUTPUT_DIR = "../dm-api/src/main/java/com/ghatana/digitalmarketing/api/security";
    private static final String TS_OUTPUT_DIR = "../ui/src/generated";

    public static void main(String[] args) throws IOException {
        LOG.info("Starting DMOS route manifest code generation");
        
        String basePath = System.getProperty("user.dir");
        Path manifestPath = Paths.get(basePath, MANIFEST_PATH);
        
        if (!Files.exists(manifestPath)) {
            throw new IOException("Manifest file not found: " + manifestPath);
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Manifest manifest = mapper.readValue(manifestPath.toFile(), Manifest.class);

        // Generate Java artifacts
        generateCapabilityRegistry(manifest, basePath);
        generateActionPermissionRegistry(manifest, basePath);
        
        // Generate TypeScript artifacts
        generateTypeScriptRouteManifest(manifest, basePath);

        LOG.info("DMOS route manifest code generation completed successfully");
    }

    private static void generateCapabilityRegistry(Manifest manifest, String basePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.ghatana.digitalmarketing.application.capabilities;\n\n");
        sb.append("/**\n");
        sb.append(" * Canonical DMOS capability registry.\n");
        sb.append(" *\n");
        sb.append(" * <p>This enum is generated from the canonical route manifest.\n");
        sb.append(" * Do not edit manually - regenerate from dmos-route-manifest.yaml.</p>\n");
        sb.append(" *\n");
        sb.append(" * @doc.type enum\n");
        sb.append(" * @doc.purpose Canonical DMOS capability definitions\n");
        sb.append(" * @doc.layer product\n");
        sb.append(" */\n");
        sb.append("public enum DmosCapability {\n");

        List<String> capabilityKeys = new ArrayList<>(manifest.capabilities.keySet());
        Collections.sort(capabilityKeys);

        for (String key : capabilityKeys) {
            Capability cap = manifest.capabilities.get(key);
            String enumName = key.toUpperCase().replace('.', '_').replace('-', '_');
            sb.append("    /**\n");
            sb.append("     * ").append(cap.description).append("\n");
            sb.append("     * Tier: ").append(cap.tier).append("\n");
            if (cap.lifecycle != null) {
                sb.append("     * Lifecycle: ").append(cap.lifecycle).append("\n");
            }
            sb.append("     */\n");
            sb.append("    ").append(enumName).append("(\"").append(key).append("\"),\n\n");
        }

        sb.append("    /**\n");
        sb.append("     * Null capability for routes that don't require specific capability checks.\n");
        sb.append("     */\n");
        sb.append("    NONE(null);\n\n");

        sb.append("    private final String key;\n\n");
        sb.append("    DmosCapability(String key) {\n");
        sb.append("        this.key = key;\n");
        sb.append("    }\n\n");
        sb.append("    public String getKey() {\n");
        sb.append("        return key;\n");
        sb.append("    }\n\n");
        sb.append("    public static boolean isDefined(String key) {\n");
        sb.append("        if (key == null || key.isBlank()) {\n");
        sb.append("            return false;\n");
        sb.append("        }\n");
        sb.append("        for (DmosCapability cap : values()) {\n");
        sb.append("            if (cap.key != null && cap.key.equals(key)) {\n");
        sb.append("                return true;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return false;\n");
        sb.append("    }\n");
        sb.append("}\n");

        Path outputPath = Paths.get(basePath, JAVA_OUTPUT_DIR, "DmosCapability.java");
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, sb.toString());
        LOG.info("Generated DmosCapability.java");
    }

    private static void generateActionPermissionRegistry(Manifest manifest, String basePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.ghatana.digitalmarketing.api.security;\n\n");
        sb.append("import java.util.Locale;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.Objects;\n");
        sb.append("import java.util.Set;\n\n");
        sb.append("/**\n");
        sb.append(" * Canonical DMOS backend action-to-role permission registry.\n");
        sb.append(" *\n");
        sb.append(" * <p>This class is generated from the canonical route manifest.\n");
        sb.append(" * Do not edit manually - regenerate from dmos-route-manifest.yaml.</p>\n");
        sb.append(" *\n");
        sb.append(" * @doc.type class\n");
        sb.append(" * @doc.purpose Canonical backend action-level role authorization for DMOS APIs\n");
        sb.append(" * @doc.layer product\n");
        sb.append(" * @doc.pattern Policy, Registry\n");
        sb.append(" */\n");
        sb.append("public final class DmosActionPermissionRegistry {\n\n");

        // Build role order map
        sb.append("    private static final Map<String, Integer> ROLE_ORDER = Map.of(\n");
        List<String> roleNames = new ArrayList<>(manifest.roles.keySet());
        Collections.sort(roleNames);
        for (int i = 0; i < roleNames.size(); i++) {
            String role = roleNames.get(i);
            Role roleDef = manifest.roles.get(role);
            sb.append("        \"").append(role).append("\", ").append(roleDef.level);
            sb.append(i == roleNames.size() - 1 ? "\n" : ",\n");
        }
        sb.append("    );\n\n");

        // Build action to minimum role map
        sb.append("    private static final Map<String, String> ACTION_MINIMUM_ROLES = Map.ofEntries(\n");
        
        Map<String, String> actionToRole = new HashMap<>();
        for (Route route : manifest.routes) {
            for (String action : route.actions) {
                // Use the highest minimum role for this action
                String existingRole = actionToRole.get(action);
                String newRole = route.minimumRole;
                if (existingRole == null || manifest.roles.get(newRole).level > manifest.roles.get(existingRole).level) {
                    actionToRole.put(action, newRole);
                }
            }
        }

        List<String> actions = new ArrayList<>(actionToRole.keySet());
        Collections.sort(actions);
        for (int i = 0; i < actions.size(); i++) {
            String action = actions.get(i);
            sb.append("        Map.entry(\"").append(action).append("\", \"").append(actionToRole.get(action)).append("\")");
            sb.append(i == actions.size() - 1 ? "\n" : ",\n");
        }
        sb.append("    );\n\n");

        // Methods
        sb.append("    private DmosActionPermissionRegistry() {\n");
        sb.append("    }\n\n");
        sb.append("    public static boolean isActionAllowed(Set<String> roles, String action) {\n");
        sb.append("        Objects.requireNonNull(action, \"action must not be null\");\n\n");
        sb.append("        String normalizedAction = action.trim().toLowerCase(Locale.ROOT);\n");
        sb.append("        String minimumRole = ACTION_MINIMUM_ROLES.get(normalizedAction);\n");
        sb.append("        if (minimumRole == null) {\n");
        sb.append("            throw new IllegalArgumentException(\"Unknown DMOS action: \" + action);\n");
        sb.append("        }\n\n");
        sb.append("        int requiredOrder = ROLE_ORDER.getOrDefault(minimumRole, Integer.MAX_VALUE);\n");
        sb.append("        int highestRoleOrder = roles == null\n");
        sb.append("            ? Integer.MIN_VALUE\n");
        sb.append("            : roles.stream()\n");
        sb.append("                .map(DmosActionPermissionRegistry::normalizeRole)\n");
        sb.append("                .filter(ROLE_ORDER::containsKey)\n");
        sb.append("                .mapToInt(ROLE_ORDER::get)\n");
        sb.append("                .max()\n");
        sb.append("                .orElse(Integer.MIN_VALUE);\n\n");
        sb.append("        return highestRoleOrder >= requiredOrder;\n");
        sb.append("    }\n\n");
        sb.append("    private static String normalizeRole(String role) {\n");
        sb.append("        if (role == null) {\n");
        sb.append("            return \"\";\n");
        sb.append("        }\n\n");
        sb.append("        return role.trim()\n");
        sb.append("            .toLowerCase(Locale.ROOT)\n");
        sb.append("            .replace('_', '-')\n");
        sb.append("            .replace(' ', '-');\n");
        sb.append("    }\n");
        sb.append("}\n");

        Path outputPath = Paths.get(basePath, SECURITY_OUTPUT_DIR, "DmosActionPermissionRegistry.java");
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, sb.toString());
        LOG.info("Generated DmosActionPermissionRegistry.java");
    }

    private static void generateTypeScriptRouteManifest(Manifest manifest, String basePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("// GENERATED CODE - Do not edit manually\n");
        sb.append("// Regenerate from dmos-route-manifest.yaml\n");
        sb.append("// Generated at: ").append(new Date()).append("\n\n");
        sb.append("import React from 'react';\n");
        sb.append("import type { ProductRouteCapability } from '@ghatana/product-shell';\n");
        sb.append("import { VALID_ROLES, type ValidRole } from '@/lib/role-utils';\n\n");

        sb.append("function lazyNamedPage<T>(loader: () => Promise<T>, exportName: keyof T): React.LazyExoticComponent<React.ComponentType> {\n");
        sb.append("  return React.lazy(async () => {\n");
        sb.append("    const module = await loader();\n");
        sb.append("    return { default: module[exportName] as React.ComponentType };\n");
        sb.append("  });\n");
        sb.append("}\n\n");

        // Page imports (placeholder - needs to be aligned with actual pages)
        sb.append("// Page imports\n");
        sb.append("const DashboardPage = lazyNamedPage(() => import('@/pages/DashboardPage'), 'DashboardPage');\n");
        sb.append("const ApprovalQueuePage = lazyNamedPage(() => import('@/pages/ApprovalQueuePage'), 'ApprovalQueuePage');\n");
        sb.append("const ApprovalDetailPage = lazyNamedPage(() => import('@/pages/ApprovalDetailPage'), 'ApprovalDetailPage');\n");
        sb.append("const AiActionLogPage = lazyNamedPage(() => import('@/pages/AiActionLogPage'), 'AiActionLogPage');\n");
        sb.append("const CampaignsPage = lazyNamedPage(() => import('@/pages/CampaignsPage'), 'CampaignsPage');\n");
        sb.append("const StrategyPage = lazyNamedPage(() => import('@/pages/StrategyPage'), 'StrategyPage');\n");
        sb.append("const BudgetPage = lazyNamedPage(() => import('@/pages/BudgetPage'), 'BudgetPage');\n");
        sb.append("const FunnelAnalyticsPage = lazyNamedPage(() => import('@/pages/FunnelAnalyticsPage'), 'FunnelAnalyticsPage');\n");
        sb.append("const AttributionPage = lazyNamedPage(() => import('@/pages/AttributionPage'), 'AttributionPage');\n");
        sb.append("const RoiRoasPage = lazyNamedPage(() => import('@/pages/RoiRoasPage'), 'RoiRoasPage');\n");
        sb.append("const SelfMarketingFunnelPage = lazyNamedPage(() => import('@/pages/SelfMarketingFunnelPage'), 'SelfMarketingFunnelPage');\n");
        sb.append("const MarketResearchPage = lazyNamedPage(() => import('@/pages/MarketResearchPage'), 'MarketResearchPage');\n");
        sb.append("const AdvancedChannelsPage = lazyNamedPage(() => import('@/pages/AdvancedChannelsPage'), 'AdvancedChannelsPage');\n");
        sb.append("const LocalizationPage = lazyNamedPage(() => import('@/pages/LocalizationPage'), 'LocalizationPage');\n");
        sb.append("const AgencyOperationsPage = lazyNamedPage(() => import('@/pages/AgencyOperationsPage'), 'AgencyOperationsPage');\n");
        sb.append("const AiOptimizationPage = lazyNamedPage(() => import('@/pages/AiOptimizationPage'), 'AiOptimizationPage');\n\n");

        // Type definitions
        sb.append("export interface DmosRouteManifestEntry extends ProductRouteCapability {\n");
        sb.append("  readonly element: React.ReactElement;\n");
        sb.append("  readonly capabilityKey?: string;\n");
        sb.append("}\n\n");

        // Build role order map
        List<String> roleNames = new ArrayList<>(manifest.roles.keySet());
        Collections.sort(roleNames);

        // Role order
        sb.append("export const DMOS_ROLE_ORDER: Readonly<Record<ValidRole, number>> = {\n");
        for (String role : roleNames) {
            Role roleDef = manifest.roles.get(role);
            sb.append("  '").append(role).append("': ").append(roleDef.level).append(",\n");
        }
        sb.append("} as const;\n\n");

        // Route manifest
        sb.append("export const dmosRouteManifest: readonly DmosRouteManifestEntry[] = [\n");
        for (Route route : manifest.routes) {
            String pageName = getPageNameFromPath(route.path);
            sb.append("  {\n");
            sb.append("    path: '").append(route.path).append("',\n");
            sb.append("    label: '").append(getLabelFromPath(route.path)).append("',\n");
            sb.append("    description: '").append(getDescriptionFromPath(route.path)).append("',\n");
            sb.append("    group: '").append(getGroupFromPath(route.path)).append("',\n");
            sb.append("    minimumRole: '").append(route.minimumRole).append("' as ValidRole,\n");
            sb.append("    actions: [").append(String.join(", ", route.actions.stream().map(a -> "'" + a + "'").toList())).append("],\n");
            sb.append("    iconName: '").append(getIconFromPath(route.path)).append("',\n");
            sb.append("    lifecycle: '").append(route.lifecycle).append("',\n");
            if (route.capability != null) {
                sb.append("    capabilityKey: '").append(route.capability).append("',\n");
            }
            if (!route.path.contains("/:requestId") && !route.path.contains("/:id")) {
                sb.append("    element: React.createElement(").append(pageName).append("),\n");
            } else {
                sb.append("    discoverable: false,\n");
                sb.append("    element: React.createElement(").append(pageName).append("),\n");
            }
            sb.append("  },\n");
        }
        sb.append("];\n");

        Path outputPath = Paths.get(basePath, TS_OUTPUT_DIR, "routeManifest.generated.ts");
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, sb.toString());
        LOG.info("Generated routeManifest.generated.ts");
    }

    private static String getPageNameFromPath(String path) {
        if (path.contains("/dashboard")) return "DashboardPage";
        if (path.contains("/approvals/:requestId")) return "ApprovalDetailPage";
        if (path.contains("/approvals")) return "ApprovalQueuePage";
        if (path.contains("/ai-actions")) return "AiActionLogPage";
        if (path.contains("/campaigns")) return "CampaignsPage";
        if (path.contains("/strategy")) return "StrategyPage";
        if (path.contains("/budget")) return "BudgetPage";
        if (path.contains("/funnel-analytics")) return "FunnelAnalyticsPage";
        if (path.contains("/attribution")) return "AttributionPage";
        if (path.contains("/roi-roas")) return "RoiRoasPage";
        if (path.contains("/self-marketing-funnel")) return "SelfMarketingFunnelPage";
        if (path.contains("/market-research")) return "MarketResearchPage";
        if (path.contains("/advanced-channels")) return "AdvancedChannelsPage";
        if (path.contains("/localization")) return "LocalizationPage";
        if (path.contains("/agency")) return "AgencyOperationsPage";
        if (path.contains("/ai-optimization")) return "AiOptimizationPage";
        return "DashboardPage";
    }

    private static String getLabelFromPath(String path) {
        if (path.contains("/dashboard")) return "Dashboard";
        if (path.contains("/approvals/:requestId")) return "Approval Detail";
        if (path.contains("/approvals")) return "Approvals";
        if (path.contains("/ai-actions/:actionId")) return "AI Action Detail";
        if (path.contains("/ai-actions")) return "AI Action Log";
        if (path.contains("/campaigns")) return "Campaigns";
        if (path.contains("/strategy")) return "Strategy";
        if (path.contains("/budget")) return "Budget";
        if (path.contains("/funnel-analytics")) return "Funnel Analytics";
        if (path.contains("/attribution")) return "Attribution";
        if (path.contains("/roi-roas")) return "ROI & ROAS";
        if (path.contains("/self-marketing-funnel")) return "Self-Marketing Funnel";
        if (path.contains("/market-research")) return "Market Research";
        if (path.contains("/advanced-channels")) return "Advanced Channels";
        if (path.contains("/localization")) return "Localization";
        if (path.contains("/agency")) return "Agency Operations";
        if (path.contains("/ai-optimization")) return "AI Optimization";
        return "Unknown";
    }

    private static String getDescriptionFromPath(String path) {
        if (path.contains("/dashboard")) return "Workspace status, approvals, and launch readiness.";
        if (path.contains("/approvals/:requestId")) return "Request detail, snapshot review, and decision flow.";
        if (path.contains("/approvals")) return "Pending approvals and decision workflow queue.";
        if (path.contains("/ai-actions")) return "Traceable AI decision and recommendation history.";
        if (path.contains("/campaigns")) return "Campaign planning and orchestration.";
        if (path.contains("/strategy")) return "Strategy generation, review, and approvals.";
        if (path.contains("/budget")) return "Budget recommendations and approval decisions.";
        if (path.contains("/funnel-analytics")) return "Full-funnel conversion analytics and stage drop-off reporting.";
        if (path.contains("/attribution")) return "Multi-touch attribution models and channel credit distribution.";
        if (path.contains("/roi-roas")) return "Return on investment and return on ad spend dashboards.";
        if (path.contains("/self-marketing-funnel")) return "Product-led growth funnel management and trial onboarding flows.";
        if (path.contains("/market-research")) return "Trend analysis, buyer persona generation, and competitive intelligence.";
        if (path.contains("/advanced-channels")) return "Programmatic advertising, Connected TV, and influencer management.";
        if (path.contains("/localization")) return "Multi-language campaign support and region-specific compliance controls.";
        if (path.contains("/agency")) return "Client onboarding, white-label reports, and multi-client workspace management.";
        if (path.contains("/ai-optimization")) return "AI-driven next-best-action recommendations, anomaly detection, and budget optimization.";
        return "Route description";
    }

    private static String getGroupFromPath(String path) {
        if (path.contains("/dashboard")) return "Overview";
        if (path.contains("/approvals")) return "Governance";
        if (path.contains("/ai-actions")) return "Governance";
        if (path.contains("/campaigns")) return "Execution";
        if (path.contains("/strategy")) return "Execution";
        if (path.contains("/budget")) return "Execution";
        if (path.contains("/funnel-analytics")) return "Reporting";
        if (path.contains("/attribution")) return "Reporting";
        if (path.contains("/roi-roas")) return "Reporting";
        if (path.contains("/self-marketing-funnel")) return "Growth";
        if (path.contains("/market-research")) return "Intelligence";
        if (path.contains("/ai-optimization")) return "Intelligence";
        if (path.contains("/advanced-channels")) return "Execution";
        if (path.contains("/localization")) return "Execution";
        if (path.contains("/agency")) return "Agency";
        return "Other";
    }

    private static String getIconFromPath(String path) {
        if (path.contains("/dashboard")) return "layout-dashboard";
        if (path.contains("/approvals/:requestId")) return "file-search";
        if (path.contains("/approvals")) return "shield-check";
        if (path.contains("/ai-actions")) return "sparkles";
        if (path.contains("/campaigns")) return "megaphone";
        if (path.contains("/strategy")) return "target";
        if (path.contains("/budget")) return "wallet";
        if (path.contains("/funnel-analytics")) return "chart-bar";
        if (path.contains("/attribution")) return "share-nodes";
        if (path.contains("/roi-roas")) return "trending-up";
        if (path.contains("/self-marketing-funnel")) return "funnel";
        if (path.contains("/market-research")) return "search";
        if (path.contains("/advanced-channels")) return "broadcast";
        if (path.contains("/localization")) return "globe";
        if (path.contains("/agency")) return "briefcase";
        if (path.contains("/ai-optimization")) return "sparkles";
        return "circle";
    }

    // Manifest data classes
    static class Manifest {
        public String version;
        public String product;
        public String generatedAt;
        public Map<String, Capability> capabilities;
        public Map<String, Role> roles;
        public List<Route> routes;
    }

    static class Capability {
        public String description;
        public String tier;
        public String lifecycle;
    }

    static class Role {
        public int level;
        public String description;
    }

    static class Route {
        public String path;
        public String method;
        public String capability;
        public List<String> actions;
        public String minimumRole;
        public String lifecycle;
        public String servlet;
    }
}
