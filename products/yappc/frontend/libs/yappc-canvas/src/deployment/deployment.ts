/**
 * Deployment Utilities for Blue/Green Deployments
 * 
 * Provides utilities for managing blue/green deployments, health checks,
 * traffic routing, and rollback procedures.
 * 
 * Features:
 * - Environment slot management (blue/green)
 * - Health check validation
 * - Traffic routing configuration
 * - Automated rollback on failure
 * - Deployment status tracking
 * 
 * @module deployment
 */

// ============================================================================
// Types
// ============================================================================

/**
 * Deployment slot (blue or green)
 */
export type DeploymentSlot = 'blue' | 'green';

/**
 * Deployment status
 */
export type DeploymentStatus =
  | 'idle'
  | 'deploying'
  | 'health_check'
  | 'routing'
  | 'active'
  | 'failed'
  | 'rolling_back';

/**
 * Health check result
 */
export interface HealthCheckResult {
  healthy: boolean;
  checks: {
    name: string;
    passed: boolean;
    message: string;
    duration: number; // milliseconds
  }[];
  timestamp: Date;
}

/**
 * Traffic routing configuration
 */
export interface TrafficRouting {
  blue: number; // percentage 0-100
  green: number; // percentage 0-100
}

/**
 * Deployment metadata
 */
export interface DeploymentMetadata {
  version: string;
  commitHash: string;
  timestamp: Date;
  deployedBy: string;
}

/**
 * Deployment slot state
 */
export interface SlotState {
  slot: DeploymentSlot;
  status: DeploymentStatus;
  metadata?: DeploymentMetadata;
  healthCheck?: HealthCheckResult;
  lastUpdated: Date;
}

/**
 * Deployment event
 */
export interface DeploymentEvent {
  type:
    | 'deployment_started'
    | 'health_check_passed'
    | 'health_check_failed'
    | 'traffic_routed'
    | 'deployment_complete'
    | 'rollback_started'
    | 'rollback_complete';
  slot: DeploymentSlot;
  timestamp: Date;
  metadata?: Record<string, unknown>;
}

/**
 * Health check function
 */
export type HealthCheckFn = () => Promise<{
  name: string;
  passed: boolean;
  message: string;
}>;

/**
 * Deployment configuration
 */
export interface DeploymentConfig {
  /** Health check timeout (ms) */
  healthCheckTimeout: number;
  /** Health check retry attempts */
  healthCheckRetries: number;
  /** Health check interval between retries (ms) */
  healthCheckRetryInterval: number;
  /** Traffic routing delay (ms) */
  trafficRoutingDelay: number;
  /** Auto-rollback on failure */
  autoRollback: boolean;
}

/**
 * Deployment manager state
 */
export interface DeploymentState {
  config: DeploymentConfig;
  slots: {
    blue: SlotState;
    green: SlotState;
  };
  routing: TrafficRouting;
  activeSlot: DeploymentSlot;
  healthChecks: HealthCheckFn[];
  eventListeners: Array<(event: DeploymentEvent) => void>;
}

// ============================================================================
// Manager
// ============================================================================

/**
 * Create deployment manager
 */
export function createDeploymentManager(
  config: Partial<DeploymentConfig> = {}
): DeploymentState {
  const defaultConfig: DeploymentConfig = {
    healthCheckTimeout: 30000, // 30 seconds
    healthCheckRetries: 3,
    healthCheckRetryInterval: 5000, // 5 seconds
    trafficRoutingDelay: 10000, // 10 seconds
    autoRollback: true,
  };

  const now = new Date();

  return {
    config: { ...defaultConfig, ...config },
    slots: {
      blue: {
        slot: 'blue',
        status: 'active',
        lastUpdated: now,
      },
      green: {
        slot: 'green',
        status: 'idle',
        lastUpdated: now,
      },
    },
    routing: {
      blue: 100,
      green: 0,
    },
    activeSlot: 'blue',
    healthChecks: [],
    eventListeners: [],
  };
}

// ============================================================================
// Health Checks
// ============================================================================

/**
 * Register health check
 */
export function registerHealthCheck(
  state: DeploymentState,
  check: HealthCheckFn
): void {
  state.healthChecks.push(check);
}

/**
 * Run all health checks
 */
export async function runHealthChecks(
  state: DeploymentState,
  slot: DeploymentSlot
): Promise<HealthCheckResult> {
  const results: HealthCheckResult['checks'] = [];

  for (const check of state.healthChecks) {
    const startTime = Date.now();

    try {
      const timeoutPromise = new Promise<never>((_, reject) =>
        setTimeout(
          () => reject(new Error('Health check timeout')),
          state.config.healthCheckTimeout
        )
      );

      const result = await Promise.race([check(), timeoutPromise]);

      results.push({
        name: result.name,
        passed: result.passed,
        message: result.message,
        duration: Date.now() - startTime,
      });
    } catch (error) {
      results.push({
        name: 'unknown',
        passed: false,
        message: error instanceof Error ? error.message : 'Unknown error',
        duration: Date.now() - startTime,
      });
    }
  }

  const allPassed = results.every((r) => r.passed);

  return {
    healthy: allPassed,
    checks: results,
    timestamp: new Date(),
  };
}

/**
 * Run health checks with retries
 */
export async function runHealthChecksWithRetries(
  state: DeploymentState,
  slot: DeploymentSlot
): Promise<HealthCheckResult> {
  let lastResult: HealthCheckResult | null = null;

  for (let attempt = 1; attempt <= state.config.healthCheckRetries; attempt++) {
    lastResult = await runHealthChecks(state, slot);

    if (lastResult.healthy) {
      return lastResult;
    }

    // Wait before retry (except on last attempt)
    if (attempt < state.config.healthCheckRetries) {
      await new Promise((resolve) =>
        setTimeout(resolve, state.config.healthCheckRetryInterval)
      );
    }
  }

  return lastResult!;
}

// ============================================================================
// Deployment Flow
// ============================================================================

/**
 * Get inactive slot
 */
export function getInactiveSlot(
  state: DeploymentState
): DeploymentSlot {
  return state.activeSlot === 'blue' ? 'green' : 'blue';
}

/**
 * Start deployment to inactive slot
 */
export async function startDeployment(
  state: DeploymentState,
  metadata: DeploymentMetadata
): Promise<{ success: boolean; slot: DeploymentSlot; error?: string }> {
  const targetSlot = getInactiveSlot(state);
  const slotState = state.slots[targetSlot];

  // Check if slot is available
  if (slotState.status !== 'idle') {
    return {
      success: false,
      slot: targetSlot,
      error: `Slot ${targetSlot} is not idle (status: ${slotState.status})`,
    };
  }

  // Update slot status
  slotState.status = 'deploying';
  slotState.metadata = metadata;
  slotState.lastUpdated = new Date();

  // Emit event
  emitEvent(state, {
    type: 'deployment_started',
    slot: targetSlot,
    timestamp: new Date(),
    metadata: { version: metadata.version },
  });

  return { success: true, slot: targetSlot };
}

/**
 * Validate deployment with health checks
 */
export async function validateDeployment(
  state: DeploymentState,
  slot: DeploymentSlot
): Promise<{ success: boolean; healthCheck: HealthCheckResult }> {
  const slotState = state.slots[slot];

  // Update status
  slotState.status = 'health_check';
  slotState.lastUpdated = new Date();

  // Run health checks
  const healthCheck = await runHealthChecksWithRetries(state, slot);
  slotState.healthCheck = healthCheck;
  slotState.lastUpdated = new Date();

  if (healthCheck.healthy) {
    emitEvent(state, {
      type: 'health_check_passed',
      slot,
      timestamp: new Date(),
      metadata: { checks: healthCheck.checks.length },
    });

    return { success: true, healthCheck };
  } else {
    slotState.status = 'failed';

    emitEvent(state, {
      type: 'health_check_failed',
      slot,
      timestamp: new Date(),
      metadata: {
        failedChecks: healthCheck.checks.filter((c) => !c.passed).length,
      },
    });

    // Auto-rollback if enabled
    if (state.config.autoRollback) {
      await rollback(state, slot);
    }

    return { success: false, healthCheck };
  }
}

/**
 * Route traffic to new slot (gradual or instant)
 */
export async function routeTraffic(
  state: DeploymentState,
  targetSlot: DeploymentSlot,
  percentage: number = 100,
  gradual: boolean = false
): Promise<void> {
  const slotState = state.slots[targetSlot];

  slotState.status = 'routing';
  slotState.lastUpdated = new Date();

  if (gradual) {
    // Gradual rollout: 10% -> 50% -> 100%
    const steps = [10, 50, 100];

    for (const step of steps) {
      if (step > percentage) break;

      await updateTrafficRouting(state, targetSlot, step);
      await new Promise((resolve) =>
        setTimeout(resolve, state.config.trafficRoutingDelay)
      );
    }
  } else {
    // Instant routing
    await updateTrafficRouting(state, targetSlot, percentage);
  }

  // Mark slot as active if 100%
  if (percentage === 100) {
    slotState.status = 'active';
    state.activeSlot = targetSlot;

    // Mark old slot as idle
    const oldSlot = targetSlot === 'blue' ? 'green' : 'blue';
    state.slots[oldSlot].status = 'idle';
    state.slots[oldSlot].lastUpdated = new Date();

    emitEvent(state, {
      type: 'deployment_complete',
      slot: targetSlot,
      timestamp: new Date(),
      metadata: { version: slotState.metadata?.version },
    });
  }

  slotState.lastUpdated = new Date();
}

/**
 * Update traffic routing percentages
 */
function updateTrafficRouting(
  state: DeploymentState,
  targetSlot: DeploymentSlot,
  percentage: number
): void {
  if (targetSlot === 'blue') {
    state.routing.blue = percentage;
    state.routing.green = 100 - percentage;
  } else {
    state.routing.green = percentage;
    state.routing.blue = 100 - percentage;
  }

  emitEvent(state, {
    type: 'traffic_routed',
    slot: targetSlot,
    timestamp: new Date(),
    metadata: { percentage, routing: { ...state.routing } },
  });
}

/**
 * Complete deployment (start -> validate -> route)
 */
export async function deploy(
  state: DeploymentState,
  metadata: DeploymentMetadata,
  gradual: boolean = false
): Promise<{
  success: boolean;
  slot: DeploymentSlot;
  error?: string;
  healthCheck?: HealthCheckResult;
}> {
  // Start deployment
  const startResult = await startDeployment(state, metadata);
  if (!startResult.success) {
    return startResult;
  }

  const { slot } = startResult;

  // Validate with health checks
  const validationResult = await validateDeployment(state, slot);
  if (!validationResult.success) {
    return {
      success: false,
      slot,
      error: 'Health checks failed',
      healthCheck: validationResult.healthCheck,
    };
  }

  // Route traffic
  await routeTraffic(state, slot, 100, gradual);

  return {
    success: true,
    slot,
    healthCheck: validationResult.healthCheck,
  };
}

// ============================================================================
// Rollback
// ============================================================================

/**
 * Rollback to previous slot
 */
export async function rollback(
  state: DeploymentState,
  failedSlot: DeploymentSlot
): Promise<void> {
  const slotState = state.slots[failedSlot];

  slotState.status = 'rolling_back';
  slotState.lastUpdated = new Date();

  emitEvent(state, {
    type: 'rollback_started',
    slot: failedSlot,
    timestamp: new Date(),
    metadata: { version: slotState.metadata?.version },
  });

  // Route all traffic back to active slot
  const activeSlot = state.activeSlot;
  await updateTrafficRouting(state, activeSlot, 100);

  // Mark failed slot as idle
  slotState.status = 'idle';
  slotState.lastUpdated = new Date();

  emitEvent(state, {
    type: 'rollback_complete',
    slot: failedSlot,
    timestamp: new Date(),
    metadata: { activeSlot },
  });
}

// ============================================================================
// Slot Management
// ============================================================================

/**
 * Get slot state
 */
export function getSlotState(
  state: DeploymentState,
  slot: DeploymentSlot
): SlotState {
  return { ...state.slots[slot] };
}

/**
 * Get all slots state
 */
export function getAllSlotsState(state: DeploymentState): {
  blue: SlotState;
  green: SlotState;
} {
  return {
    blue: { ...state.slots.blue },
    green: { ...state.slots.green },
  };
}

/**
 * Get traffic routing
 */
export function getTrafficRouting(state: DeploymentState): TrafficRouting {
  return { ...state.routing };
}

/**
 * Get active slot
 */
export function getActiveSlot(state: DeploymentState): DeploymentSlot {
  return state.activeSlot;
}

// ============================================================================
// Events
// ============================================================================

/**
 * Subscribe to deployment events
 */
export function subscribeToDeploymentEvents(
  state: DeploymentState,
  listener: (event: DeploymentEvent) => void
): () => void {
  state.eventListeners.push(listener);

  return () => {
    const index = state.eventListeners.indexOf(listener);
    if (index !== -1) {
      state.eventListeners.splice(index, 1);
    }
  };
}

/**
 * Emit deployment event
 */
function emitEvent(state: DeploymentState, event: DeploymentEvent): void {
  for (const listener of state.eventListeners) {
    try {
      listener(event);
    } catch (error) {
      console.error('Deployment event listener error:', error);
    }
  }
}

// ============================================================================
// Configuration
// ============================================================================

/**
 * Get configuration
 */
export function getConfig(state: DeploymentState): DeploymentConfig {
  return { ...state.config };
}

/**
 * Update configuration
 */
export function updateConfig(
  state: DeploymentState,
  updates: Partial<DeploymentConfig>
): DeploymentConfig {
  state.config = { ...state.config, ...updates };
  return { ...state.config };
}
