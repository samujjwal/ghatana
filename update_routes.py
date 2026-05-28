#!/usr/bin/env python3
"""Update featureGatedRoute calls to include surfaceName and surfaceDescription parameters."""

import re

file_path = r"d:\samuj\Developments\ghatana\products\data-cloud\delivery\ui\src\routes.tsx"

# Read the file
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# First, update the function signature
content = re.sub(
    r'function featureGatedRoute\(\s*enabled: boolean,\s*element: React\.ReactElement,\s*\): React\.ReactElement \{\s*return enabled \? element : withSuspense\(NotFound\);\s*\}',
    '''/**
 * Runtime-truth-driven route gating.
 * 
 * Instead of returning a generic 404 when disabled, this now renders
 * DisabledSurfacePage with meaningful context about why the surface
 * is unavailable. This provides progressive disclosure based on runtime
 * truth rather than silent failures.
 * 
 * DC-P1-004: Replaced NotFound with DisabledSurfacePage for better UX.
 */
function featureGatedRoute(
  enabled: boolean,
  element: React.ReactElement,
  surfaceName: string,
  surfaceDescription: string,
): React.ReactElement {
  if (!enabled) {
    return withSuspense(() => (
      <DisabledSurfacePage
        surfaceName={surfaceName}
        surfaceDescription={surfaceDescription}
        status="DISABLED"
        nextAction="This surface is currently disabled in your deployment profile. Contact your administrator to enable it."
      />
    ));
  }
  return element;
}''',
    content,
    flags=re.DOTALL
)

# Define the replacements for each route
replacements = [
    # Alerts
    (
        r'element: featureGatedRoute\(\s*isAlertsSurfaceEnabled\(\),\s*<RoleProtectedRoute routePath="/alerts">\s*<RuntimeCapabilityRouteGate\s*aliases={\["alert-triage", "monitoring", "alerts"\]}\s*fallback={withSuspense\(\(\) => \(\s*<DisabledSurfacePage\s*surfaceName="Alerts"\s*surfaceDescription="The Alerts surface provides real-time alert triage and monitoring for your Data Cloud deployment\."\s*/>\s*\)\)}\s*>\s*{withSuspense\(AlertsPage\)}\s*</RuntimeCapabilityRouteGate>\s*</RoleProtectedRoute>,\s*\),',
        'element: featureGatedRoute(\n          isAlertsSurfaceEnabled(),\n          <RoleProtectedRoute routePath="/alerts">\n            <RuntimeCapabilityRouteGate\n              aliases={["alert-triage", "monitoring", "alerts"]}\n              fallback={withSuspense(() => (\n                <DisabledSurfacePage\n                  surfaceName="Alerts"\n                  surfaceDescription="The Alerts surface provides real-time alert triage and monitoring for your Data Cloud deployment."\n                />\n              ))}\n            >\n              {withSuspense(AlertsPage)}\n            </RuntimeCapabilityRouteGate>\n          </RoleProtectedRoute>,\n          "Alerts",\n          "The Alerts surface provides real-time alert triage and monitoring for your Data Cloud deployment.",\n        ),'
    ),
    # Memory
    (
        r'element: featureGatedRoute\(\s*isMemorySurfaceEnabled\(\),\s*<RoleProtectedRoute routePath="/memory">\s*<RuntimeCapabilityRouteGate\s*aliases={\["memory-plane", "memory"\]}\s*fallback={withSuspense\(\(\) => \(\s*<DisabledSurfacePage\s*surfaceName="Memory Plane"\s*surfaceDescription="The Memory Plane surface provides persistent memory and context management for AI agent workloads\."\s*/>\s*\)\)}\s*>\s*{withSuspense\(MemoryPlaneViewerPage\)}\s*</RuntimeCapabilityRouteGate>\s*</RoleProtectedRoute>,\s*\),',
        'element: featureGatedRoute(\n          isMemorySurfaceEnabled(),\n          <RoleProtectedRoute routePath="/memory">\n            <RuntimeCapabilityRouteGate\n              aliases={["memory-plane", "memory"]}\n              fallback={withSuspense(() => (\n                <DisabledSurfacePage\n                  surfaceName="Memory Plane"\n                  surfaceDescription="The Memory Plane surface provides persistent memory and context management for AI agent workloads."\n                />\n              ))}\n            >\n              {withSuspense(MemoryPlaneViewerPage)}\n            </RuntimeCapabilityRouteGate>\n          </RoleProtectedRoute>,\n          "Memory Plane",\n          "The Memory Plane surface provides persistent memory and context management for AI agent workloads.",\n        ),'
    ),
    # Entities
    (
        r'element: featureGatedRoute\(\s*isEntityBrowserSurfaceEnabled\(\),\s*<RoleProtectedRoute routePath="/entities">\s*<RuntimeCapabilityRouteGate\s*aliases={\["entity-browser", "entities"\]}\s*fallback={withSuspense\(\(\) => \(\s*<DisabledSurfacePage\s*surfaceName="Entity Browser"\s*surfaceDescription="The Entity Browser surface provides structured entity management and inspection for your data domains\."\s*/>\s*\)\)}\s*>\s*{withSuspense\(EntityBrowserPage\)}\s*</RuntimeCapabilityRouteGate>\s*</RoleProtectedRoute>,\s*\),',
        'element: featureGatedRoute(\n          isEntityBrowserSurfaceEnabled(),\n          <RoleProtectedRoute routePath="/entities">\n            <RuntimeCapabilityRouteGate\n              aliases={["entity-browser", "entities"]}\n              fallback={withSuspense(() => (\n                <DisabledSurfacePage\n                  surfaceName="Entity Browser"\n                  surfaceDescription="The Entity Browser surface provides structured entity management and inspection for your data domains."\n                />\n              ))}\n            >\n              {withSuspense(EntityBrowserPage)}\n            </RuntimeCapabilityRouteGate>\n          </RoleProtectedRoute>,\n          "Entity Browser",\n          "The Entity Browser surface provides structured entity management and inspection for your data domains.",\n        ),'
    ),
    # Context
    (
        r'element: featureGatedRoute\(\s*isContextSurfaceEnabled\(\),\s*<RoleProtectedRoute routePath="/context">\s*<RuntimeCapabilityRouteGate\s*aliases={\["context-explorer", "context"\]}\s*fallback={withSuspense\(\(\) => \(\s*<DisabledSurfacePage\s*surfaceName="Context Explorer"\s*surfaceDescription="The Context Explorer surface provides contextual insight and lineage tracing across your data assets\."\s*/>\s*\)\)}\s*>\s*{withSuspense\(ContextExplorerPage\)}\s*</RuntimeCapabilityRouteGate>\s*</RoleProtectedRoute>,\s*\),',
        'element: featureGatedRoute(\n          isContextSurfaceEnabled(),\n          <RoleProtectedRoute routePath="/context">\n            <RuntimeCapabilityRouteGate\n              aliases={["context-explorer", "context"]}\n              fallback={withSuspense(() => (\n                <DisabledSurfacePage\n                  surfaceName="Context Explorer"\n                  surfaceDescription="The Context Explorer surface provides contextual insight and lineage tracing across your data assets."\n                />\n              ))}\n            >\n              {withSuspense(ContextExplorerPage)}\n            </RuntimeCapabilityRouteGate>\n          </RoleProtectedRoute>,\n          "Context Explorer",\n          "The Context Explorer surface provides contextual insight and lineage tracing across your data assets.",\n        ),'
    ),
    # Fabric
    (
        r'element: featureGatedRoute\(\s*isFabricSurfaceEnabled\(\),\s*<RoleProtectedRoute routePath="/fabric">\s*<RuntimeCapabilityRouteGate\s*aliases={\["data-fabric", "fabric"\]}\s*fallback={withSuspense\(\(\) => \(\s*<DisabledSurfacePage\s*surfaceName="Data Fabric"\s*surfaceDescription="The Data Fabric surface provides unified data connectivity, storage profiling, and connector management\."\s*/>\s*\)\)}\s*>\s*{withSuspense\(DataFabricPage\)}\s*</RuntimeCapabilityRouteGate>\s*</RoleProtectedRoute>,\s*\),',
        'element: featureGatedRoute(\n          isFabricSurfaceEnabled(),\n          <RoleProtectedRoute routePath="/fabric">\n            <RuntimeCapabilityRouteGate\n              aliases={["data-fabric", "fabric"]}\n              fallback={withSuspense(() => (\n                <DisabledSurfacePage\n                  surfaceName="Data Fabric"\n                  surfaceDescription="The Data Fabric surface provides unified data connectivity, storage profiling, and connector management."\n                />\n              ))}\n            >\n              {withSuspense(DataFabricPage)}\n            </RuntimeCapabilityRouteGate>\n          </RoleProtectedRoute>,\n          "Data Fabric",\n          "The Data Fabric surface provides unified data connectivity, storage profiling, and connector management.",\n        ),'
    ),
    # Agents
    (
        r'element: featureGatedRoute\(\s*isAgentCatalogSurfaceEnabled\(\),\s*<RoleProtectedRoute routePath="/agents">\s*<RuntimeCapabilityRouteGate\s*aliases={\["agent-catalog", "agents"\]}\s*fallback={withSuspense\(\(\) => \(\s*<DisabledSurfacePage\s*surfaceName="Agent Catalog"\s*surfaceDescription="The Agent Catalog surface provides discovery, registration, and management of AI agents in your deployment\."\s*/>\s*\)\)}\s*>\s*{withSuspense\(AgentPluginManagerPage\)}\s*</RuntimeCapabilityRouteGate>\s*</RoleProtectedRoute>,\s*\),',
        'element: featureGatedRoute(\n          isAgentCatalogSurfaceEnabled(),\n          <RoleProtectedRoute routePath="/agents">\n            <RuntimeCapabilityRouteGate\n              aliases={["agent-catalog", "agents"]}\n              fallback={withSuspense(() => (\n                <DisabledSurfacePage\n                  surfaceName="Agent Catalog"\n                  surfaceDescription="The Agent Catalog surface provides discovery, registration, and management of AI agents in your deployment."\n                />\n              ))}\n            >\n              {withSuspense(AgentPluginManagerPage)}\n            </RuntimeCapabilityRouteGate>\n          </RoleProtectedRoute>,\n          "Agent Catalog",\n          "The Agent Catalog surface provides discovery, registration, and management of AI agents in your deployment.",\n        ),'
    ),
    # Settings
    (
        r'element: featureGatedRoute\(\s*isSettingsSurfaceEnabled\(\),\s*<RoleProtectedRoute routePath="/settings">\s*<RuntimeCapabilityRouteGate\s*aliases={\["settings", "config"\]}\s*fallback={withSuspense\(\(\) => \(\s*<DisabledSurfacePage\s*surfaceName="Settings"\s*surfaceDescription="The Settings surface provides configuration management for your Data Cloud tenant\."\s*/>\s*\)\)}\s*>\s*{withSuspense\(SettingsPage\)}\s*</RuntimeCapabilityRouteGate>\s*</RoleProtectedRoute>,\s*\),',
        'element: featureGatedRoute(\n          isSettingsSurfaceEnabled(),\n          <RoleProtectedRoute routePath="/settings">\n            <RuntimeCapabilityRouteGate\n              aliases={["settings", "config"]}\n              fallback={withSuspense(() => (\n                <DisabledSurfacePage\n                  surfaceName="Settings"\n                  surfaceDescription="The Settings surface provides configuration management for your Data Cloud tenant."\n                />\n              ))}\n            >\n              {withSuspense(SettingsPage)}\n            </RuntimeCapabilityRouteGate>\n          </RoleProtectedRoute>,\n          "Settings",\n          "The Settings surface provides configuration management for your Data Cloud tenant.",\n        ),'
    ),
]

# Apply replacements
for pattern, replacement in replacements:
    content = re.sub(pattern, replacement, content, flags=re.DOTALL)

# Write the file back
with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated featureGatedRoute calls successfully")
