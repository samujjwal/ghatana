import type { UnsupportedSurfaceState } from "./UnsupportedSurfaceBoundary";

export interface UnsupportedSurfaceDefinition {
  title: string;
  summary: string;
  details: string[];
  state: UnsupportedSurfaceState;
}

export const dataFabricMetricsBoundary = {
  title: "Fabric Metrics Preview",
  summary:
    "Live data-fabric metrics are not exposed by the current Data Cloud launcher API. This page shows a preview topology only.",
  details: [
    "Topology layout is available for orientation and design review.",
    "Live throughput, latency, and queue depth are preview values until launcher metrics endpoints exist.",
    "This route should remain operator-facing and out of primary discovery until runtime metrics are real.",
  ],
  state: "preview",
} satisfies UnsupportedSurfaceDefinition;

export const alertsSurfaceBoundary = {
  title: "Alerts",
  summary:
    "Alerts are live and launcher-backed. This page provides alert triage, grouping, suggestions, and rule management.",
  details: [
    "Backend routes for alert list, acknowledge, resolve, grouping, suggestions, rules, and streaming are live.",
    "This surface is operator-facing and hidden from primary-user discovery per route truth matrix.",
    "Default navigation and search do not promote alerts as a primary workflow.",
  ],
  state: "operator-only",
} satisfies UnsupportedSurfaceDefinition;

export const smartWorkflowGenerationBoundary = {
  title: "Pipeline builder unavailable",
  summary:
    "Natural-language pipeline generation is not exposed by the current Data Cloud launcher API. Use this page to capture intent, then continue in the manual pipeline editor.",
  details: [
    "Use this page to capture intent and then continue in the runtime-backed manual pipeline editor.",
    "Generated pipeline drafts, confidence scoring, and provenance logging still require a backend contract.",
  ],
  state: "temporarily-unavailable",
} satisfies UnsupportedSurfaceDefinition;

export const pluginDependencyBoundary = {
  title: "Dependency graph unavailable",
  summary:
    "The bundled launcher API exposes plugin runtime facts, but it does not publish a plugin-to-plugin dependency graph.",
  details: [
    "This page no longer renders a fabricated dependency topology.",
    "Capability and health sections above reflect the live plugin payload returned by the launcher.",
    "Use release notes or source-level bundle definitions when dependency review is required.",
  ],
  state: "not-in-deployment",
} satisfies UnsupportedSurfaceDefinition;

export type SettingsBoundaryKey =
  | "profile"
  | "preferences"
  | "notifications"
  | "api";

export const settingsSurfaceBoundaries: Record<
  SettingsBoundaryKey,
  UnsupportedSurfaceDefinition
> = {
  profile: {
    title: "Profile Settings",
    summary:
      "User profile management is not exposed by the current Data Cloud UI backend, so this page no longer fabricates operator identity fields.",
    details: [
      "Profile data must come from the authenticated identity provider or a dedicated user-profile API.",
      "Role and tenant membership are runtime concerns and should be surfaced from auth or session state, not hard-coded defaults.",
      "No save action is shown until a real write endpoint exists.",
    ],
    state: "not-in-deployment",
  },
  preferences: {
    title: "Preferences",
    summary:
      "Preference persistence is not wired to a user settings API in this deployment.",
    details: [
      "Theme, locale, timezone, and date formatting should be backed by a real user-preference store.",
      "Showing static defaults would imply persistence that does not exist.",
      "The shell still exposes the section so the missing capability is explicit instead of being faked.",
    ],
    state: "not-in-deployment",
  },
  notifications: {
    title: "Notification Settings",
    summary:
      "Notification channel preferences are not backed by a delivery or user-preference service here.",
    details: [
      "Email, Slack, workflow, and quality-alert subscriptions require a real notification backend.",
      "Hard-coded checked states looked live but were not connected to delivery behavior.",
      "Operators should configure notifications through the owning service until this surface is implemented.",
    ],
    state: "not-in-deployment",
  },
  api: {
    title: "API Keys",
    summary:
      "API key inventory and rotation are enforced at launcher bootstrap, but the current UI does not expose key-management endpoints.",
    details: [
      "No API key list is rendered because there is no safe read endpoint for secret material or key metadata in this UI surface.",
      "No Generate, Regenerate, or Revoke action is shown until a dedicated management API exists.",
      "For non-local profiles, runtime enforcement is driven by DATACLOUD_API_KEYS and launcher validation.",
    ],
    state: "not-in-deployment",
  },
};
