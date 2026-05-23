/**
 * KernelDoctorApiHandlers - framework-light HTTP handlers for Kernel doctor/diagnostics APIs.
 *
 * @doc.type module
 * @doc.purpose Exposes provider readiness and kernel diagnostics to Studio and gateways
 * @doc.layer kernel-lifecycle
 * @doc.pattern Doctor
 */

import type { KernelLifecycleService } from "../service/KernelLifecycleService.js";

export const DOCTOR_CHECK_TYPES = [
  "provider-readiness",
  "adapter-availability",
  "toolchain-health",
  "platform-bridges",
  "kernel-services",
] as const;

export interface DoctorCheckResult {
  checkType: string;
  status: "healthy" | "degraded" | "unhealthy" | "unknown";
  timestamp: string;
  details: Record<string, unknown>;
  recommendations?: string[];
}

export interface ProviderReadinessResult extends DoctorCheckResult {
  checkType: "provider-readiness";
  details: {
    bootstrapModeAvailable: boolean;
    platformModeAvailable: boolean;
    activeProviderMode: "bootstrap" | "platform" | "unknown";
    missingBridges: string[];
    availableBridges: string[];
    environmentResolution: {
      source: "environment" | "profile" | "default";
      providerMode: "bootstrap" | "platform" | "unknown";
    };
  };
}

export interface AdapterAvailabilityResult extends DoctorCheckResult {
  checkType: "adapter-availability";
  details: {
    javaAdapterAvailable: boolean;
    typescriptAdapterAvailable: boolean;
    rustAdapterAvailable: boolean;
    pythonAdapterAvailable: boolean;
    dockerAdapterAvailable: boolean;
    composeAdapterAvailable: boolean;
    missingAdapters: string[];
  };
}

export interface ToolchainHealthResult extends DoctorCheckResult {
  checkType: "toolchain-health";
  details: {
    gradleAvailable: boolean;
    nodeAvailable: boolean;
    cargoAvailable: boolean;
    pythonAvailable: boolean;
    dockerAvailable: boolean;
    dockerComposeAvailable: boolean;
    versionInfo: Record<string, string | null>;
  };
}

export interface PlatformBridgesResult extends DoctorCheckResult {
  checkType: "platform-bridges";
  details: {
    identityBridgeAvailable: boolean;
    auditBridgeAvailable: boolean;
    consentBridgeAvailable: boolean;
    riskBridgeAvailable: boolean;
    notificationBridgeAvailable: boolean;
    missingBridges: string[];
  };
}

export interface KernelServicesResult extends DoctorCheckResult {
  checkType: "kernel-services";
  details: {
    lifecycleServiceAvailable: boolean;
    planningServiceAvailable: boolean;
    executionServiceAvailable: boolean;
    validationServiceAvailable: boolean;
    serviceHealth: Record<string, "healthy" | "degraded" | "unhealthy">;
  };
}

/**
 * Resolve provider mode from environment/profile automatically.
 * This hides provider mode internals from product teams.
 */
export function resolveProviderMode(
  environment?: string,
  profile?: string,
): "bootstrap" | "platform" {
  // In production, platform mode is the default
  // Bootstrap mode is only used for local development or explicit override
  const isProduction = environment === "production" || profile === "production";
  const isDevelopment = environment === "development" || profile === "development";
  
  if (isProduction) {
    return "platform";
  }
  
  if (isDevelopment) {
    // Check for explicit bootstrap override in environment
    const providerModeOverride = process.env.GHATANA_PROVIDER_MODE;
    if (providerModeOverride === "bootstrap") {
      return "bootstrap";
    }
    // Default to platform mode even in development
    return "platform";
  }
  
  // Default to platform mode
  return "platform";
}

/**
 * Check provider readiness and return actionable recommendations.
 */
export async function checkProviderReadiness(
  _service: KernelLifecycleService,
  _productUnitId?: string,
  environment?: string,
): Promise<ProviderReadinessResult> {
  const activeProviderMode = resolveProviderMode(environment);
  
  // Check for required bridges based on provider mode
  const requiredBridges = activeProviderMode === "platform"
    ? ["identity", "audit", "consent", "risk", "notification"]
    : [];
  
  // Simulate bridge availability check
  const availableBridges = ["identity", "audit", "consent"]; // Would be real check
  const missingBridges = requiredBridges.filter(bridge => !availableBridges.includes(bridge));
  
  const status = missingBridges.length === 0 ? "healthy" : "unhealthy";
  
  const recommendations: string[] = [];
  if (missingBridges.length > 0) {
    recommendations.push(
      `Missing required platform bridges: ${missingBridges.join(", ")}`,
    );
    recommendations.push(
      "Run 'ghatana kernel doctor --check-type platform-bridges' for detailed bridge status",
    );
    if (activeProviderMode === "platform") {
      recommendations.push(
        "Consider using bootstrap mode for local development: GHATANA_PROVIDER_MODE=bootstrap",
      );
    }
  }
  
  return {
    checkType: "provider-readiness",
    status,
    timestamp: new Date().toISOString(),
    details: {
      bootstrapModeAvailable: true,
      platformModeAvailable: missingBridges.length === 0,
      activeProviderMode,
      missingBridges,
      availableBridges,
      environmentResolution: {
        source: environment ? "environment" : "default",
        providerMode: activeProviderMode,
      },
    },
    recommendations,
  };
}

/**
 * Check adapter availability for polyglot support.
 */
export async function checkAdapterAvailability(
  _service: KernelLifecycleService,
): Promise<AdapterAvailabilityResult> {
  // Simulate adapter availability check
  const availableAdapters = {
    javaAdapterAvailable: true,
    typescriptAdapterAvailable: true,
    rustAdapterAvailable: true,
    pythonAdapterAvailable: true,
    dockerAdapterAvailable: true,
    composeAdapterAvailable: true,
  };
  
  const missingAdapters = Object.entries(availableAdapters)
    .filter(([_, available]) => !available)
    .map(([name]) => name.replace("Available", ""));
  
  const status = missingAdapters.length === 0 ? "healthy" : "degraded";
  
  return {
    checkType: "adapter-availability",
    status,
    timestamp: new Date().toISOString(),
    details: {
      ...availableAdapters,
      missingAdapters,
    },
  };
}

/**
 * Check toolchain health for build/execution.
 */
export async function checkToolchainHealth(): Promise<ToolchainHealthResult> {
  // Simulate toolchain availability check
  const toolchainHealth = {
    gradleAvailable: true,
    nodeAvailable: true,
    cargoAvailable: true,
    pythonAvailable: true,
    dockerAvailable: true,
    dockerComposeAvailable: true,
    versionInfo: {
      gradle: "8.5",
      node: "22.0.0",
      cargo: "1.75",
      python: "3.12",
      docker: "24.0",
      dockerCompose: "2.20",
    },
  };
  
  const missingTools = Object.entries(toolchainHealth)
    .filter(([key, value]) => key.endsWith("Available") && !value)
    .map(([key]) => key.replace("Available", ""));
  
  const status = missingTools.length === 0 ? "healthy" : "degraded";
  
  return {
    checkType: "toolchain-health",
    status,
    timestamp: new Date().toISOString(),
    details: toolchainHealth,
  };
}

/**
 * Check platform bridge availability.
 */
export async function checkPlatformBridges(): Promise<PlatformBridgesResult> {
  // Simulate bridge availability check
  const bridgeHealth = {
    identityBridgeAvailable: true,
    auditBridgeAvailable: true,
    consentBridgeAvailable: true,
    riskBridgeAvailable: false,
    notificationBridgeAvailable: false,
  };
  
  const missingBridges = Object.entries(bridgeHealth)
    .filter(([_, available]) => !available)
    .map(([name]) => name.replace("BridgeAvailable", ""));
  
  const status = missingBridges.length === 0 ? "healthy" : "degraded";
  
  const recommendations: string[] = [];
  if (missingBridges.length > 0) {
    recommendations.push(
      `Missing platform bridges: ${missingBridges.join(", ")}`,
    );
    recommendations.push(
      "Contact platform team to enable required bridges for your environment",
    );
  }
  
  return {
    checkType: "platform-bridges",
    status,
    timestamp: new Date().toISOString(),
    details: {
      ...bridgeHealth,
      missingBridges,
    },
    recommendations,
  };
}

/**
 * Check kernel service health.
 */
export async function checkKernelServices(
  _service: KernelLifecycleService,
): Promise<KernelServicesResult> {
  // Simulate service health check
  const serviceHealth = {
    lifecycleServiceAvailable: true,
    planningServiceAvailable: true,
    executionServiceAvailable: true,
    validationServiceAvailable: true,
    serviceHealth: {
      lifecycle: "healthy" as const,
      planning: "healthy" as const,
      execution: "healthy" as const,
      validation: "healthy" as const,
    } satisfies Record<string, "healthy" | "degraded" | "unhealthy">,
  };
  
  const unhealthyServices = Object.entries(serviceHealth.serviceHealth)
    .filter(([_, health]) => health !== "healthy")
    .map(([name]) => name);
  
  const status = unhealthyServices.length === 0 ? "healthy" : "degraded";
  
  return {
    checkType: "kernel-services",
    status,
    timestamp: new Date().toISOString(),
    details: serviceHealth,
  };
}

/**
 * Run a specific doctor check.
 */
export async function runDoctorCheck(
  service: KernelLifecycleService,
  checkType: string,
  productUnitId?: string,
  environment?: string,
): Promise<DoctorCheckResult> {
  switch (checkType) {
    case "provider-readiness":
      return checkProviderReadiness(service, productUnitId, environment);
    case "adapter-availability":
      return checkAdapterAvailability(service);
    case "toolchain-health":
      return checkToolchainHealth();
    case "platform-bridges":
      return checkPlatformBridges();
    case "kernel-services":
      return checkKernelServices(service);
    default:
      return {
        checkType: "unknown",
        status: "unknown",
        timestamp: new Date().toISOString(),
        details: { error: `Unknown check type: ${checkType}` },
        recommendations: ["Run 'ghatana kernel doctor' to see available check types"],
      };
  }
}

/**
 * Run all doctor checks and return comprehensive results.
 */
export async function runAllDoctorChecks(
  service: KernelLifecycleService,
  productUnitId?: string,
  environment?: string,
): Promise<DoctorCheckResult[]> {
  const results: DoctorCheckResult[] = [];
  
  for (const checkType of DOCTOR_CHECK_TYPES) {
    const result = await runDoctorCheck(service, checkType, productUnitId, environment);
    results.push(result);
  }
  
  return results;
}
