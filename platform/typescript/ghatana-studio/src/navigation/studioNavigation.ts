export type StudioRouteId =
  | "home"
  | "ideas"
  | "blueprints"
  | "canvas"
  | "develop"
  | "lifecycle"
  | "agents"
  | "artifacts"
  | "deployments"
  | "health"
  | "learn"
  | "design-system"
  | "settings";

export type StudioRouteOwnership =
  | "studio"
  | "yappc"
  | "kernel"
  | "data-cloud"
  | "shared";

export type StudioJourney =
  | "product-ideation-to-intent"
  | "direct-kernel-usage"
  | "agentic-development"
  | "digital-marketing-pilot"
  | "artifact-intelligence"
  | "lifecycle-operations"
  | "governance-compliance"
  | "observability-health"
  | "learning-adaptation";

export type StudioDimension =
  | "ideation"
  | "blueprinting"
  | "development"
  | "lifecycle"
  | "governance"
  | "observability"
  | "learning"
  | "settings";

export type StudioRouteStatus = "ready" | "empty" | "degraded" | "blocked";

export type RouteExposurePolicy = "visible" | "disabled" | "hidden" | "preview";

export type StudioRouteOwnerDomain =
  | "studio"
  | "yappc"
  | "product-development-kernel"
  | "data-cloud-aep"
  | "composed";

export type StudioLifecycleTruthSource =
  | "none"
  | "yappc-product-unit-intent"
  | "kernel-lifecycle"
  | "data-cloud-runtime-truth"
  | "composed-runtime-truth";

export type StudioPreviewTrustState =
  | "trusted"
  | "controlled"
  | "evidence-required"
  | "unavailable";

export type StudioSharedUxState =
  | "loading"
  | "empty"
  | "error"
  | "blocked"
  | "degraded"
  | "access-denied"
  | "pending-approval"
  | "requires-verification";

export type StudioStatusReasonCode =
  | "available"
  | "runtime-pending"
  | "lifecycle-pending"
  | "data-cloud-evidence-pending"
  | "pilot-only"
  | "not-applicable";

export interface StudioNavItem {
  readonly id: StudioRouteId;
  readonly path: string;
  readonly label: string;
  readonly labelKey: `studio.navigation.${StudioRouteId}`;
  readonly ownership: StudioRouteOwnership;
  readonly requiredCapability: string;
  readonly status: StudioRouteStatus;
  readonly statusReasonCode: StudioStatusReasonCode;
  readonly statusMessageKey: `studio.navigation.status.${StudioRouteId}`;
  readonly requiredNextAction: string;
  readonly evidenceRefs: readonly string[];
  readonly evidenceRequired: readonly string[];
  readonly journey: readonly StudioJourney[];
  readonly dimension: StudioDimension;
  readonly isCustomerVisible: boolean;
  readonly exposure: RouteExposurePolicy;
}

export interface StudioRouteOwnershipMetadata {
  readonly routeId: StudioRouteId;
  readonly path: string;
  readonly ownerDomain: StudioRouteOwnerDomain;
  readonly ownerProduct: string;
  readonly permissions: readonly string[];
  readonly featureFlags: readonly string[];
  readonly requiredProviders: readonly string[];
  readonly lifecycleTruthSource: StudioLifecycleTruthSource;
  readonly status: StudioRouteStatus;
  readonly supportedUxStates: readonly StudioSharedUxState[];
  readonly previewTrustState: StudioPreviewTrustState;
}

export interface StudioRouteCapabilityState {
  readonly runtimeConfigured: boolean;
  readonly lifecycleConfigured: boolean;
  readonly lifecycleExecutionAllowed: boolean;
  readonly dataCloudEvidenceReady: boolean;
}

export const STUDIO_SHARED_UX_STATES: readonly StudioSharedUxState[] = [
  "loading",
  "empty",
  "error",
  "blocked",
  "degraded",
  "access-denied",
  "pending-approval",
  "requires-verification",
] as const;

const COMMON_UX_STATES: readonly StudioSharedUxState[] = [
  "loading",
  "empty",
  "error",
  "blocked",
  "degraded",
  "access-denied",
];

export const STUDIO_ROUTE_OWNERSHIP_METADATA: Readonly<
  Record<StudioRouteId, StudioRouteOwnershipMetadata>
> = {
  home: {
    routeId: "home",
    path: "/",
    ownerDomain: "studio",
    ownerProduct: "Ghatana Studio",
    permissions: ["studio.shell.view"],
    featureFlags: [],
    requiredProviders: [],
    lifecycleTruthSource: "none",
    status: "ready",
    supportedUxStates: COMMON_UX_STATES,
    previewTrustState: "trusted",
  },
  ideas: {
    routeId: "ideas",
    path: "/ideas",
    ownerDomain: "yappc",
    ownerProduct: "YAPPC",
    permissions: ["yappc.ideas.view"],
    featureFlags: ["studio.yappc.intent-handoff"],
    requiredProviders: ["kernel-product-intent"],
    lifecycleTruthSource: "yappc-product-unit-intent",
    status: "degraded",
    supportedUxStates: [...COMMON_UX_STATES, "requires-verification"],
    previewTrustState: "evidence-required",
  },
  blueprints: {
    routeId: "blueprints",
    path: "/blueprints",
    ownerDomain: "yappc",
    ownerProduct: "YAPPC",
    permissions: ["yappc.blueprints.view"],
    featureFlags: ["studio.yappc.blueprints"],
    requiredProviders: ["kernel-product-intent"],
    lifecycleTruthSource: "yappc-product-unit-intent",
    status: "ready",
    supportedUxStates: COMMON_UX_STATES,
    previewTrustState: "controlled",
  },
  canvas: {
    routeId: "canvas",
    path: "/canvas",
    ownerDomain: "yappc",
    ownerProduct: "YAPPC",
    permissions: ["yappc.canvas.view"],
    featureFlags: ["studio.yappc.canvas"],
    requiredProviders: ["kernel-product-intent", "data-cloud-provenance"],
    lifecycleTruthSource: "yappc-product-unit-intent",
    status: "ready",
    supportedUxStates: COMMON_UX_STATES,
    previewTrustState: "controlled",
  },
  develop: {
    routeId: "develop",
    path: "/develop",
    ownerDomain: "product-development-kernel",
    ownerProduct: "Product Development Kernel",
    permissions: ["kernel.development.view"],
    featureFlags: ["studio.kernel.develop"],
    requiredProviders: ["kernel-lifecycle"],
    lifecycleTruthSource: "kernel-lifecycle",
    status: "ready",
    supportedUxStates: COMMON_UX_STATES,
    previewTrustState: "controlled",
  },
  lifecycle: {
    routeId: "lifecycle",
    path: "/lifecycle",
    ownerDomain: "product-development-kernel",
    ownerProduct: "Product Development Kernel",
    permissions: ["kernel.lifecycle.view"],
    featureFlags: ["studio.kernel.lifecycle"],
    requiredProviders: ["kernel-lifecycle", "kernel-release"],
    lifecycleTruthSource: "kernel-lifecycle",
    status: "degraded",
    supportedUxStates: [
      ...COMMON_UX_STATES,
      "pending-approval",
      "requires-verification",
    ],
    previewTrustState: "evidence-required",
  },
  agents: {
    routeId: "agents",
    path: "/agents",
    ownerDomain: "data-cloud-aep",
    ownerProduct: "Data Cloud + AEP",
    permissions: ["data-cloud.agent-evidence.view"],
    featureFlags: ["studio.data-cloud.agent-evidence"],
    requiredProviders: ["data-cloud-runtime-truth", "aep-agent-evidence"],
    lifecycleTruthSource: "data-cloud-runtime-truth",
    status: "degraded",
    supportedUxStates: [
      ...COMMON_UX_STATES,
      "pending-approval",
      "requires-verification",
    ],
    previewTrustState: "evidence-required",
  },
  artifacts: {
    routeId: "artifacts",
    path: "/artifacts",
    ownerDomain: "product-development-kernel",
    ownerProduct: "Product Development Kernel",
    permissions: ["kernel.artifacts.view"],
    featureFlags: ["studio.kernel.artifacts"],
    requiredProviders: ["kernel-artifacts", "data-cloud-provenance"],
    lifecycleTruthSource: "kernel-lifecycle",
    status: "empty",
    supportedUxStates: COMMON_UX_STATES,
    previewTrustState: "evidence-required",
  },
  deployments: {
    routeId: "deployments",
    path: "/deployments",
    ownerDomain: "product-development-kernel",
    ownerProduct: "Product Development Kernel",
    permissions: ["kernel.deployments.view"],
    featureFlags: ["studio.kernel.deployments.preview"],
    requiredProviders: ["kernel-deployment", "kernel-release", "kernel-health"],
    lifecycleTruthSource: "kernel-lifecycle",
    status: "blocked",
    supportedUxStates: [
      ...COMMON_UX_STATES,
      "pending-approval",
      "requires-verification",
    ],
    previewTrustState: "evidence-required",
  },
  health: {
    routeId: "health",
    path: "/health",
    ownerDomain: "composed",
    ownerProduct: "Composed Runtime Truth",
    permissions: ["data-cloud.runtime-truth.view"],
    featureFlags: ["studio.health.composed-truth"],
    requiredProviders: [
      "kernel-lifecycle",
      "data-cloud-runtime-truth",
      "aep-agent-evidence",
    ],
    lifecycleTruthSource: "composed-runtime-truth",
    status: "degraded",
    supportedUxStates: COMMON_UX_STATES,
    previewTrustState: "evidence-required",
  },
  learn: {
    routeId: "learn",
    path: "/learn",
    ownerDomain: "yappc",
    ownerProduct: "YAPPC",
    permissions: ["yappc.learning.view"],
    featureFlags: ["studio.yappc.learn"],
    requiredProviders: ["data-cloud-provenance"],
    lifecycleTruthSource: "yappc-product-unit-intent",
    status: "ready",
    supportedUxStates: COMMON_UX_STATES,
    previewTrustState: "controlled",
  },
  "design-system": {
    routeId: "design-system",
    path: "/design-system",
    ownerDomain: "studio",
    ownerProduct: "Ghatana Studio",
    permissions: ["studio.design-system.view"],
    featureFlags: ["studio.design-system"],
    requiredProviders: [],
    lifecycleTruthSource: "none",
    status: "ready",
    supportedUxStates: COMMON_UX_STATES,
    previewTrustState: "trusted",
  },
  settings: {
    routeId: "settings",
    path: "/settings",
    ownerDomain: "studio",
    ownerProduct: "Ghatana Studio",
    permissions: ["studio.settings.view"],
    featureFlags: [],
    requiredProviders: [],
    lifecycleTruthSource: "none",
    status: "ready",
    supportedUxStates: COMMON_UX_STATES,
    previewTrustState: "trusted",
  },
} as const;

export const STUDIO_NAV_ITEMS = [
  {
    id: "home",
    path: "/",
    label: "Home",
    labelKey: "studio.navigation.home",
    ownership: "studio",
    requiredCapability: "studio.shell.view",
    status: "ready",
    statusReasonCode: "available",
    statusMessageKey: "studio.navigation.status.home",
    requiredNextAction: "None",
    evidenceRefs: [
      "docs/implementation/GHATANA_WORLD_CLASS_IMPLEMENTATION_TRACKER.md",
    ],
    evidenceRequired: [],
    journey: ["direct-kernel-usage", "lifecycle-operations"],
    dimension: "settings",
    isCustomerVisible: true,
    exposure: "visible",
  },
  {
    id: "ideas",
    path: "/ideas",
    label: "Ideas",
    labelKey: "studio.navigation.ideas",
    ownership: "yappc",
    requiredCapability: "yappc.ideas.view",
    status: "degraded",
    statusReasonCode: "runtime-pending",
    statusMessageKey: "studio.navigation.status.ideas",
    requiredNextAction:
      "Configure runtime context and ProductUnitIntent handoff.",
    evidenceRefs: [
      "platform/kernel-todo.md",
      "docs/implementation/GHATANA_WORLD_CLASS_IMPLEMENTATION_TRACKER.md",
    ],
    evidenceRequired: [
      "platform/typescript/kernel-product-contracts/src",
      "platform/typescript/kernel-lifecycle/src",
    ],
    journey: ["product-ideation-to-intent"],
    dimension: "ideation",
    isCustomerVisible: true,
    exposure: "disabled",
  },
  {
    id: "blueprints",
    path: "/blueprints",
    label: "Blueprints",
    labelKey: "studio.navigation.blueprints",
    ownership: "yappc",
    requiredCapability: "yappc.blueprints.view",
    status: "ready",
    statusReasonCode: "available",
    statusMessageKey: "studio.navigation.status.blueprints",
    requiredNextAction: "None",
    evidenceRefs: [
      "docs/implementation/GHATANA_WORLD_CLASS_IMPLEMENTATION_TRACKER.md",
    ],
    evidenceRequired: ["platform/typescript/kernel-product-contracts/src"],
    journey: ["product-ideation-to-intent"],
    dimension: "blueprinting",
    isCustomerVisible: true,
    exposure: "visible",
  },
  {
    id: "canvas",
    path: "/canvas",
    label: "Canvas",
    labelKey: "studio.navigation.canvas",
    ownership: "yappc",
    requiredCapability: "yappc.canvas.view",
    status: "ready",
    statusReasonCode: "available",
    statusMessageKey: "studio.navigation.status.canvas",
    requiredNextAction: "None",
    evidenceRefs: [
      "docs/implementation/GHATANA_WORLD_CLASS_IMPLEMENTATION_TRACKER.md",
    ],
    evidenceRequired: ["platform/typescript/kernel-product-contracts/src"],
    journey: ["product-ideation-to-intent"],
    dimension: "blueprinting",
    isCustomerVisible: true,
    exposure: "visible",
  },
  {
    id: "develop",
    path: "/develop",
    label: "Develop",
    labelKey: "studio.navigation.develop",
    ownership: "kernel",
    requiredCapability: "kernel.development.view",
    status: "ready",
    statusReasonCode: "available",
    statusMessageKey: "studio.navigation.status.develop",
    requiredNextAction: "None",
    evidenceRefs: [
      "platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts",
    ],
    evidenceRequired: ["platform/typescript/kernel-lifecycle/src"],
    journey: ["direct-kernel-usage"],
    dimension: "development",
    isCustomerVisible: true,
    exposure: "visible",
  },
  {
    id: "lifecycle",
    path: "/lifecycle",
    label: "Lifecycle",
    labelKey: "studio.navigation.lifecycle",
    ownership: "kernel",
    requiredCapability: "kernel.lifecycle.view",
    status: "degraded",
    statusReasonCode: "lifecycle-pending",
    statusMessageKey: "studio.navigation.status.lifecycle",
    requiredNextAction:
      "Enable lifecycle runtime configuration for selected product units.",
    evidenceRefs: [
      "platform/kernel-todo.md",
      "products/digital-marketing/kernel-product.yaml",
    ],
    evidenceRequired: [
      "platform/typescript/kernel-lifecycle/src",
      "platform/typescript/kernel-release/src",
    ],
    journey: [
      "direct-kernel-usage",
      "digital-marketing-pilot",
      "lifecycle-operations",
    ],
    dimension: "lifecycle",
    isCustomerVisible: true,
    exposure: "disabled",
  },
  {
    id: "agents",
    path: "/agents",
    label: "Agents",
    labelKey: "studio.navigation.agents",
    ownership: "data-cloud",
    requiredCapability: "data-cloud.agent-evidence.view",
    status: "degraded",
    statusReasonCode: "data-cloud-evidence-pending",
    statusMessageKey: "studio.navigation.status.agents",
    requiredNextAction:
      "Provide Data Cloud-backed agent evidence providers and traces.",
    evidenceRefs: [
      "platform/typescript/kernel-product-contracts/src/agentic",
      "platform/kernel-todo.md",
    ],
    evidenceRequired: [
      "platform/java/agent-core",
      "scripts/check-data-cloud-platform-providers.mjs",
    ],
    journey: ["agentic-development", "governance-compliance"],
    dimension: "governance",
    isCustomerVisible: true,
    exposure: "disabled",
  },
  {
    id: "artifacts",
    path: "/artifacts",
    label: "Artifacts",
    labelKey: "studio.navigation.artifacts",
    ownership: "kernel",
    requiredCapability: "kernel.artifacts.view",
    status: "empty",
    statusReasonCode: "data-cloud-evidence-pending",
    statusMessageKey: "studio.navigation.status.artifacts",
    requiredNextAction:
      "Generate artifact manifests from lifecycle runs and surface evidence.",
    evidenceRefs: [
      "platform/typescript/kernel-artifacts",
      "platform/kernel-todo.md",
    ],
    evidenceRequired: [
      "platform/typescript/kernel-artifacts",
      "platform/typescript/kernel-providers/src/provenance",
    ],
    journey: ["artifact-intelligence", "lifecycle-operations"],
    dimension: "development",
    isCustomerVisible: true,
    exposure: "disabled",
  },
  {
    id: "deployments",
    path: "/deployments",
    label: "Deployments",
    labelKey: "studio.navigation.deployments",
    ownership: "kernel",
    requiredCapability: "kernel.deployments.view",
    status: "blocked",
    statusReasonCode: "pilot-only",
    statusMessageKey: "studio.navigation.status.deployments",
    requiredNextAction:
      "Keep deployment execution limited to the Digital Marketing and PHR opening pilots.",
    evidenceRefs: [
      "config/canonical-product-registry.json",
      "scripts/check-opening-lifecycle-pilots.mjs",
      "scripts/check-digital-marketing-lifecycle-pilot.mjs",
      "scripts/check-phr-lifecycle-pilot.mjs",
    ],
    evidenceRequired: [
      "platform/typescript/kernel-deployment",
      "platform/typescript/kernel-release",
    ],
    journey: ["digital-marketing-pilot", "lifecycle-operations"],
    dimension: "lifecycle",
    isCustomerVisible: true,
    exposure: "hidden",
  },
  {
    id: "health",
    path: "/health",
    label: "Health",
    labelKey: "studio.navigation.health",
    ownership: "data-cloud",
    requiredCapability: "data-cloud.runtime-truth.view",
    status: "degraded",
    statusReasonCode: "data-cloud-evidence-pending",
    statusMessageKey: "studio.navigation.status.health",
    requiredNextAction: "Publish runtime truth and provider health snapshots.",
    evidenceRefs: [
      "platform/typescript/kernel-providers",
      "platform/kernel-todo.md",
    ],
    evidenceRequired: [
      "platform/typescript/kernel-providers",
      "scripts/check-data-cloud-platform-providers.mjs",
    ],
    journey: ["observability-health"],
    dimension: "observability",
    isCustomerVisible: true,
    exposure: "disabled",
  },
  {
    id: "learn",
    path: "/learn",
    label: "Learn",
    labelKey: "studio.navigation.learn",
    ownership: "yappc",
    requiredCapability: "yappc.learning.view",
    status: "ready",
    statusReasonCode: "available",
    statusMessageKey: "studio.navigation.status.learn",
    requiredNextAction: "None",
    evidenceRefs: [
      "docs/implementation/GHATANA_WORLD_CLASS_IMPLEMENTATION_TRACKER.md",
    ],
    evidenceRequired: [],
    journey: ["learning-adaptation"],
    dimension: "learning",
    isCustomerVisible: true,
    exposure: "visible",
  },
  {
    id: "design-system",
    path: "/design-system",
    label: "Design System",
    labelKey: "studio.navigation.design-system",
    ownership: "studio",
    requiredCapability: "studio.design-system.view",
    status: "ready",
    statusReasonCode: "available",
    statusMessageKey: "studio.navigation.status.design-system",
    requiredNextAction: "None",
    evidenceRefs: [
      "platform/typescript/ds-generator/src",
    ],
    evidenceRequired: [],
    journey: ["direct-kernel-usage"],
    dimension: "development",
    isCustomerVisible: true,
    exposure: "visible",
  },
  {
    id: "settings",
    path: "/settings",
    label: "Settings",
    labelKey: "studio.navigation.settings",
    ownership: "shared",
    requiredCapability: "studio.settings.view",
    status: "ready",
    statusReasonCode: "available",
    statusMessageKey: "studio.navigation.status.settings",
    requiredNextAction: "None",
    evidenceRefs: [
      "platform/typescript/ghatana-studio/src/config/studioRuntimeContext.ts",
    ],
    evidenceRequired: [],
    journey: ["direct-kernel-usage"],
    dimension: "settings",
    isCustomerVisible: true,
    exposure: "visible",
  },
] as const satisfies readonly StudioNavItem[];

export function resolveStudioRouteCapabilityState(input: {
  readonly runtimeConfigured: boolean;
  readonly lifecycleStatus: "unconfigured" | "loading" | "ready" | "degraded";
  readonly productUnit?: {
    readonly lifecycleExecutionAllowed?: boolean;
    readonly metadata?: {
      readonly lifecycleExecutionAllowed?: boolean;
    };
  };
}): StudioRouteCapabilityState {
  const lifecycleConfigured = input.lifecycleStatus !== "unconfigured";
  const lifecycleExecutionAllowed =
    input.productUnit?.lifecycleExecutionAllowed ??
    input.productUnit?.metadata?.lifecycleExecutionAllowed ??
    false;

  return {
    runtimeConfigured: input.runtimeConfigured,
    lifecycleConfigured,
    lifecycleExecutionAllowed,
    dataCloudEvidenceReady: lifecycleConfigured && lifecycleExecutionAllowed,
  };
}

function resolveRouteExposure(
  route: StudioNavItem,
  capabilityState: StudioRouteCapabilityState,
): RouteExposurePolicy {
  if (
    route.id === "home" ||
    route.id === "blueprints" ||
    route.id === "canvas" ||
    route.id === "learn" ||
    route.id === "design-system" ||
    route.id === "settings"
  ) {
    return "visible";
  }

  if (!capabilityState.runtimeConfigured) {
    if (route.id === "deployments") {
      return "hidden";
    }
    return "disabled";
  }

  if (route.id === "develop") {
    return capabilityState.lifecycleConfigured ? "visible" : "disabled";
  }

  if (route.id === "lifecycle") {
    return capabilityState.lifecycleConfigured ? "visible" : "disabled";
  }

  if (
    route.id === "agents" ||
    route.id === "artifacts" ||
    route.id === "health"
  ) {
    return capabilityState.dataCloudEvidenceReady ? "visible" : "disabled";
  }

  if (route.id === "deployments") {
    return capabilityState.lifecycleExecutionAllowed ? "preview" : "hidden";
  }

  return route.exposure;
}

export function resolveStudioNavItems(
  capabilityState: StudioRouteCapabilityState,
): readonly StudioNavItem[] {
  return STUDIO_NAV_ITEMS.map((route: StudioNavItem) => ({
    ...route,
    exposure: resolveRouteExposure(route, capabilityState),
  }));
}

export function getStudioRouteOwnershipMetadata(
  routeId: StudioRouteId,
): StudioRouteOwnershipMetadata {
  return STUDIO_ROUTE_OWNERSHIP_METADATA[routeId];
}

export function validateStudioRouteOwnershipMetadata(
  items: readonly StudioNavItem[] = STUDIO_NAV_ITEMS,
): readonly string[] {
  const violations: string[] = [];
  const uxStates = new Set(STUDIO_SHARED_UX_STATES);

  for (const item of items) {
    const metadata = STUDIO_ROUTE_OWNERSHIP_METADATA[item.id];
    if (metadata.routeId !== item.id) {
      violations.push(`${item.id}: routeId mismatch`);
    }
    if (metadata.path !== item.path) {
      violations.push(`${item.id}: path mismatch`);
    }
    if (metadata.status !== item.status) {
      violations.push(`${item.id}: status mismatch`);
    }
    if (!metadata.permissions.includes(item.requiredCapability)) {
      violations.push(
        `${item.id}: required capability missing from permissions`,
      );
    }
    if (metadata.ownerProduct.trim().length === 0) {
      violations.push(`${item.id}: owner product missing`);
    }
    if (!metadata.supportedUxStates.every((state) => uxStates.has(state))) {
      violations.push(`${item.id}: unsupported UX state`);
    }
  }

  return violations;
}

export function findStudioNavItem(pathname: string): StudioNavItem | undefined {
  return STUDIO_NAV_ITEMS.find((item: StudioNavItem) => item.path === pathname);
}

export function findStudioNavItemFromItems(
  pathname: string,
  items: readonly StudioNavItem[],
): StudioNavItem | undefined {
  return items.find((item: StudioNavItem) => item.path === pathname);
}
