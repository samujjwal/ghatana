/**
 * Simulation Runtime Service - Core orchestration for simulation execution.
 *
 * @doc.type class
 * @doc.purpose Manages simulation sessions, state, and kernel coordination
 * @doc.layer product
 * @doc.pattern Service
 */

import { randomUUID } from 'crypto';
import Redis from 'ioredis';
import type {
  SimulationManifest,
  SimulationSessionId,
  SimKeyframe,
  SimRuntimeService,
  SimKernelService,
  SimEntityBase,
  SimulationStep,
  EasingFunction,
} from '@ghatana/tutorputor-contracts/v1/simulation';

import { KernelRegistry } from './kernel-registry';
import { getEasingFunction } from './easing';

/**
 * Session state data.
 */
interface SessionState {
  /** Session identifier */
  sessionId: SimulationSessionId;
  /** The simulation ID (from manifest) */
  simulationId: string;
  /** The simulation manifest */
  manifest: SimulationManifest;
  /** Serialized kernel state */
  kernelState: string;
  /** Current step index */
  currentStepIndex: number;
  /** Total number of steps */
  totalSteps: number;
  /** Current playback time (ms) */
  currentTime: number;
  /** Total duration (ms) */
  totalDuration: number;
  /** Whether simulation is playing */
  isPlaying: boolean;
  /** Playback speed multiplier */
  playbackSpeed: number;
  /** Current keyframe */
  currentKeyframe: SimKeyframe;
  /** Execution history for analytics */
  executionHistory: ExecutionHistoryEntry[];
  /** Session start time */
  startedAt: Date;
  /** Last interaction time */
  lastInteractionAt: Date;
}

/**
 * Execution history entry for analytics.
 */
interface ExecutionHistoryEntry {
  stepIndex: number;
  timestamp: number;
  action: 'forward' | 'backward' | 'seek' | 'play' | 'pause';
  duration: number;
}

/**
 * Session timeout (30 minutes of inactivity).
 */
const SESSION_TIMEOUT_MS = 30 * 60 * 1000;

/**
 * Simulation Runtime Service implementation.
 */
export class SimulationRuntimeService implements SimRuntimeService {
  private redis: Redis;

  constructor() {
    this.redis = new Redis(process.env.REDIS_URL || 'redis://localhost:6379');
  }

  /**
   * Create a new simulation session.
   *
   * @param manifest - The simulation manifest to run
   * @returns The new session ID
   */
  async createSession(manifest: SimulationManifest): Promise<SimulationSessionId> {
    // Generate session ID
    const sessionId = randomUUID() as SimulationSessionId;

    // Get appropriate kernel
    const kernel = await KernelRegistry.getKernel(manifest);
    kernel.initialize(manifest);

    // Calculate total duration
    const totalDuration = manifest.steps.reduce((sum, step) => sum + (step.duration ?? 1000), 0);

    // Create initial keyframe
    const initialKeyframe = this.createInitialKeyframe(manifest);

    // Create session state
    const sessionState: SessionState = {
      sessionId,
      simulationId: manifest.id,
      manifest,
      kernelState: kernel.serialize(),
      currentStepIndex: -1,
      totalSteps: manifest.steps.length,
      currentTime: 0,
      totalDuration,
      isPlaying: false,
      playbackSpeed: 1.0,
      currentKeyframe: initialKeyframe,
      executionHistory: [],
      startedAt: new Date(),
      lastInteractionAt: new Date(),
    };

    // Store session
    await this.saveSession(sessionState);

    return sessionId;
  }

  /**
   * Step forward to the next simulation step.
   *
   * @param sessionId - The session to advance
   * @returns The new keyframe state
   */
  async stepForward(sessionId: SimulationSessionId): Promise<SimKeyframe> {
    const session = await this.getSession(sessionId);
    if (!session) throw new Error('Session not found');

    if (session.currentStepIndex >= session.totalSteps - 1) {
      // Already at the end
      return session.currentKeyframe;
    }

    const startTime = Date.now();

    // Restore kernel
    const kernel = await this.restoreKernel(session);

    // Advance to next step
    session.currentStepIndex++;
    const step = session.manifest.steps[session.currentStepIndex];

    // Execute kernel step
    kernel.step();

    // Generate new keyframe
    session.currentKeyframe = this.generateKeyframeForStep(session, step, kernel);

    // Update time
    session.currentTime = this.calculateTimeForStep(session, session.currentStepIndex);

    // Record history
    session.executionHistory.push({
      stepIndex: session.currentStepIndex,
      timestamp: Date.now(),
      action: 'forward',
      duration: Date.now() - startTime,
    });

    session.lastInteractionAt = new Date();
    session.kernelState = kernel.serialize();

    await this.saveSession(session);

    return session.currentKeyframe;
  }

  /**
   * Step backward to the previous simulation step.
   *
   * @param sessionId - The session to rewind
   * @returns The new keyframe state
   */
  async stepBackward(sessionId: SimulationSessionId): Promise<SimKeyframe> {
    const session = await this.getSession(sessionId);
    if (!session) throw new Error('Session not found');

    // console.log('stepBackward: currentStepIndex', session.currentStepIndex);

    if (session.currentStepIndex <= -1) {
      // Already at the beginning
      return session.currentKeyframe;
    }

    const startTime = Date.now();

    // Restore kernel
    const kernel = await this.restoreKernel(session);

    // Go back to previous step
    session.currentStepIndex--;

    if (session.currentStepIndex === -1) {
      // Back to initial state
      session.currentKeyframe = this.createInitialKeyframe(session.manifest);
      session.currentTime = 0;
      // Reset kernel
      kernel.reset();
      kernel.initialize(session.manifest);
    } else {
      // Reset kernel and replay to current step
      kernel.reset();
      kernel.initialize(session.manifest);

      for (let i = 0; i <= session.currentStepIndex; i++) {
        kernel.step();
      }

      // Generate keyframe
      const step = session.manifest.steps[session.currentStepIndex];
      session.currentKeyframe = this.generateKeyframeForStep(session, step, kernel);

      // Update time
      session.currentTime = this.calculateTimeForStep(session, session.currentStepIndex);
    }

    // Record history
    session.executionHistory.push({
      stepIndex: session.currentStepIndex,
      timestamp: Date.now(),
      action: 'backward',
      duration: Date.now() - startTime,
    });

    session.lastInteractionAt = new Date();
    session.kernelState = kernel.serialize();

    await this.saveSession(session);

    return session.currentKeyframe;
  }

  /**
   * Seek to a specific time in the simulation.
   *
   * @param sessionId - The session to seek
   * @param timeMs - The target time in milliseconds
   * @returns The keyframe at the target time
   */
  async seekTo(sessionId: SimulationSessionId, timeMs: number): Promise<SimKeyframe> {
    const session = await this.getSession(sessionId);
    if (!session) throw new Error('Session not found');

    if (timeMs < 0) {
      return this.reset(sessionId);
    }

    const startTime = Date.now();

    // Clamp time to valid range
    const clampedTime = Math.max(0, Math.min(timeMs, session.totalDuration));

    // Find the step at this time
    const { stepIndex, progressInStep } = this.findStepAtTime(session, clampedTime);

    // Restore kernel
    const kernel = await this.restoreKernel(session);

    // Reset and replay to target step
    kernel.reset();
    kernel.initialize(session.manifest);

    for (let i = 0; i <= stepIndex; i++) {
      kernel.step();
    }

    // Interpolate within step if needed
    session.currentStepIndex = stepIndex;
    session.currentTime = clampedTime;

    // Generate interpolated keyframe
    const step = session.manifest.steps[stepIndex];
    const baseKeyframe = this.generateKeyframeForStep(session, step, kernel);

    // Apply interpolation
    session.currentKeyframe = this.interpolateKeyframe(
      session,
      baseKeyframe,
      progressInStep,
      (step.easing ?? 'linear') as EasingFunction
    );

    // Record history
    session.executionHistory.push({
      stepIndex: session.currentStepIndex,
      timestamp: Date.now(),
      action: 'seek',
      duration: Date.now() - startTime,
    });

    session.lastInteractionAt = new Date();
    session.kernelState = kernel.serialize();

    await this.saveSession(session);

    return session.currentKeyframe;
  }

  /**
   * Get the current simulation state.
   *
   * @param sessionId - The session to query
   * @returns The current simulation state
   */
  async getState(sessionId: SimulationSessionId): Promise<Record<string, unknown>> {
    const session = await this.getSession(sessionId);
    if (!session) throw new Error('Session not found');

    const kernel = await this.restoreKernel(session);

    return {
      sessionId: session.sessionId,
      manifestId: session.manifest.id,
      stepIndex: session.currentStepIndex,
      currentStepIndex: session.currentStepIndex,
      totalSteps: session.totalSteps,
      currentTime: session.currentTime,
      totalDuration: session.totalDuration,
      isPlaying: session.isPlaying,
      playbackSpeed: session.playbackSpeed,
      currentKeyframe: session.currentKeyframe,
      entities: session.currentKeyframe?.entities || [],
      annotations: session.currentKeyframe?.annotations || [],
      analytics: kernel.getAnalytics(),
    };
  }

  /**
   * Seek to a specific step index.
   *
   * @param sessionId - The session to seek
   * @param stepIndex - The target step index
   * @returns The keyframe at the target step
   */
  async seekToStep(sessionId: SimulationSessionId, stepIndex: number): Promise<SimKeyframe> {
    const session = await this.getSession(sessionId);
    if (!session) throw new Error('Session not found');

    // Clamp to valid range
    const targetIndex = Math.max(-1, Math.min(stepIndex, session.totalSteps - 1));

    const startTime = Date.now();

    // Restore kernel
    const kernel = await this.restoreKernel(session);

    // Reset and replay to target step
    kernel.reset();
    kernel.initialize(session.manifest);

    if (targetIndex === -1) {
      // Back to initial state
      session.currentKeyframe = this.createInitialKeyframe(session.manifest);
      session.currentTime = 0;
    } else {
      for (let i = 0; i <= targetIndex; i++) {
        kernel.step();
      }

      // Generate keyframe
      const step = session.manifest.steps[targetIndex];
      session.currentKeyframe = this.generateKeyframeForStep(session, step, kernel);
      session.currentTime = this.calculateTimeForStep(session, targetIndex);
    }

    session.currentStepIndex = targetIndex;

    // Record history
    session.executionHistory.push({
      stepIndex: targetIndex,
      timestamp: Date.now(),
      action: 'seek',
      duration: Date.now() - startTime,
    });

    session.lastInteractionAt = new Date();
    session.kernelState = kernel.serialize();

    await this.saveSession(session);

    return session.currentKeyframe;
  }

  /**
   * Terminate a simulation session.
   *
   * @param sessionId - The session to terminate
   */
  async terminateSession(sessionId: SimulationSessionId): Promise<void> {
    const session = await this.getSession(sessionId);
    if (!session) return;

    // Release kernel (if needed, though we just drop the state now)
    KernelRegistry.releaseKernel(session.manifest.id, session.manifest.version);

    // Delete session from Redis
    await this.redis.del(`session:${sessionId}`);
  }

  /**
   * Get session state.
   *
   * @param sessionId - The session to query
   * @returns The session state
   */
  async getSessionState(sessionId: SimulationSessionId): Promise<any> {
    return this.getState(sessionId);
  }

  /**
   * End a simulation session.
   *
   * @param sessionId - The session to end
   */
  async endSession(sessionId: SimulationSessionId): Promise<void> {
    const session = await this.getSession(sessionId);
    if (!session) return;

    // Release kernel (if needed, though we just drop the state now)
    KernelRegistry.releaseKernel(session.manifest.id, session.manifest.version);

    // Remove session
    await this.redis.del(`sim:session:${sessionId}`);
  }

  /**
   * Set playback speed.
   *
   * @param sessionId - The session to modify
   * @param speed - Playback speed multiplier (0.25 to 4.0)
   */
  async setPlaybackSpeed(sessionId: SimulationSessionId, speed: number): Promise<void> {
    const session = await this.getSession(sessionId);
    if (!session) return;

    session.playbackSpeed = Math.max(0.25, Math.min(4.0, speed));
    session.lastInteractionAt = new Date();
    await this.saveSession(session);
  }

  /**
   * Start playback.
   *
   * @param sessionId - The session to play
   */
  async play(sessionId: SimulationSessionId): Promise<void> {
    const session = await this.getSession(sessionId);
    if (!session) return;

    session.isPlaying = true;
    session.lastInteractionAt = new Date();
    await this.saveSession(session);
  }

  /**
   * Pause playback.
   *
   * @param sessionId - The session to pause
   */
  async pause(sessionId: SimulationSessionId): Promise<void> {
    const session = await this.getSession(sessionId);
    if (!session) return;

    session.isPlaying = false;
    session.lastInteractionAt = new Date();
    await this.saveSession(session);
  }

  /**
   * Reset simulation to beginning.
   *
   * @param sessionId - The session to reset
   * @returns The initial keyframe
   */
  async reset(sessionId: SimulationSessionId): Promise<SimKeyframe> {
    const session = await this.getSession(sessionId);
    if (!session) throw new Error('Session not found');

    const kernel = await this.restoreKernel(session);
    kernel.reset();
    kernel.initialize(session.manifest);

    session.currentStepIndex = -1;
    session.currentTime = 0;
    session.isPlaying = false;
    session.currentKeyframe = this.createInitialKeyframe(session.manifest);
    session.lastInteractionAt = new Date();
    session.kernelState = kernel.serialize();

    await this.saveSession(session);

    return session.currentKeyframe;
  }

  /**
   * Get session analytics.
   *
   * @param sessionId - The session to analyze
   * @returns Session analytics
   */
  async getSessionAnalytics(sessionId: SimulationSessionId): Promise<SessionAnalytics> {
    const session = await this.getSession(sessionId);
    if (!session) throw new Error('Session not found');

    const kernel = await this.restoreKernel(session);

    const totalInteractions = session.executionHistory.length;
    const avgStepDuration =
      totalInteractions > 0
        ? session.executionHistory.reduce((sum, e) => sum + e.duration, 0) / totalInteractions
        : 0;

    const forwardSteps = session.executionHistory.filter((e) => e.action === 'forward').length;
    const backwardSteps = session.executionHistory.filter((e) => e.action === 'backward').length;
    const seeks = session.executionHistory.filter((e) => e.action === 'seek').length;

    return {
      sessionId,
      manifestId: session.manifest.id,
      startedAt: session.startedAt,
      duration: Date.now() - new Date(session.startedAt).getTime(),
      totalInteractions,
      forwardSteps,
      backwardSteps,
      seeks,
      avgStepDuration,
      completionPercentage: (session.currentStepIndex / session.totalSteps) * 100,
      kernelAnalytics: kernel.getAnalytics(),
    };
  }

  /**
   * List all active sessions.
   */
  async listSessions(): Promise<SessionState[]> {
    const keys = await this.redis.keys('sim:session:*');
    const sessions: SessionState[] = [];
    for (const key of keys) {
      const data = await this.redis.get(key);
      if (data) {
        sessions.push(JSON.parse(data));
      }
    }
    return sessions;
  }

  /**
   * Interpolate state at a specific progress.
   */
  async interpolate(sessionId: SimulationSessionId, progress: number): Promise<SimKeyframe> {
    const session = await this.getSession(sessionId);
    if (!session) throw new Error('Session not found');

    return this.interpolateKeyframe(
      session,
      session.currentKeyframe,
      Math.max(0, Math.min(1, progress)),
      'linear'
    );
  }

  // === Private Helpers ===

  public async getSession(sessionId: string): Promise<SessionState | null> {
    const data = await this.redis.get(`sim:session:${sessionId}`);
    if (!data) return null;
    const session = JSON.parse(data);
    return session;
  }

  private async saveSession(session: SessionState): Promise<void> {
    await this.redis.set(
      `sim:session:${session.sessionId}`,
      JSON.stringify(session),
      'EX',
      30 * 60 // 30 minutes
    );
  }

  private async restoreKernel(session: SessionState): Promise<SimKernelService> {
    const kernel = await KernelRegistry.getKernel(session.manifest);
    kernel.deserialize(session.kernelState);
    return kernel;
  }

  /**
   * Create initial keyframe from manifest.
   */
  private createInitialKeyframe(manifest: SimulationManifest): SimKeyframe {
    return {
      timestamp: 0,
      stepIndex: -1,
      entities: manifest.initialEntities.map((entity) => ({
        ...entity,
        // Ensure default visual properties
        color: entity.color ?? '#4A90D9',
        opacity: entity.opacity ?? 1,
      })),
      annotations: [],
      camera: {
        x: 0,
        y: 0,
        zoom: 1,
      },
    };
  }

  /**
   * Generate keyframe for a specific step.
   */
  private generateKeyframeForStep(
    session: SessionState,
    step: SimulationStep,
    kernel: SimKernelService
  ): SimKeyframe {
    // Get interpolated state from kernel
    const interpolatedState = kernel.interpolate(1.0);

    // Map kernel entities to keyframe entities
    const entities: SimEntityBase[] = (interpolatedState.entities || []).map((kernelEntity) => {
      const originalEntity = session.manifest.initialEntities.find((e) => e.id === kernelEntity.id);
      return {
        ...originalEntity,
        ...kernelEntity,
        id: kernelEntity.id,
        label: kernelEntity.label ?? originalEntity?.label ?? '',
        type: kernelEntity.type ?? originalEntity?.type ?? 'generic',
        // Flattened visual properties
        color: kernelEntity.color ?? originalEntity?.color,
        opacity: kernelEntity.opacity ?? originalEntity?.opacity,
        x: kernelEntity.x ?? originalEntity?.x ?? 0,
        y: kernelEntity.y ?? originalEntity?.y ?? 0,
      };
    });

    return {
      timestamp: session.currentTime,
      stepIndex: session.currentStepIndex,
      entities: entities as any, // Cast to SimEntity[]
      annotations: step.annotations ?? [],
      audio: step.audio,
      camera: step.camera,
    };
  }

  /**
   * Calculate cumulative time for a step index.
   */
  private calculateTimeForStep(session: SessionState, stepIndex: number): number {
    let time = 0;
    for (let i = 0; i < stepIndex; i++) {
      time += session.manifest.steps[i].duration ?? 1000;
    }
    return time;
  }

  /**
   * Find step at a given time.
   */
  private findStepAtTime(
    session: SessionState,
    timeMs: number
  ): { stepIndex: number; progressInStep: number } {
    let accumulatedTime = 0;

    for (let i = 0; i < session.manifest.steps.length; i++) {
      const stepDuration = session.manifest.steps[i].duration ?? 1000;
      const stepEndTime = accumulatedTime + stepDuration;

      if (timeMs < stepEndTime) {
        const progressInStep = (timeMs - accumulatedTime) / stepDuration;
        return { stepIndex: i, progressInStep };
      }

      accumulatedTime = stepEndTime;
    }

    // Past the end, return last step at 100%
    return {
      stepIndex: session.manifest.steps.length - 1,
      progressInStep: 1.0,
    };
  }

  /**
   * Interpolate keyframe with easing.
   */
  private interpolateKeyframe(
    session: SessionState,
    baseKeyframe: SimKeyframe,
    progress: number,
    easing: EasingFunction
  ): SimKeyframe {
    const easingFn = getEasingFunction(easing);
    const easedProgress = easingFn(progress);

    // Apply eased interpolation to entities
    const interpolatedEntities = baseKeyframe.entities.map((entity) => {
      return {
        ...entity,
        opacity: this.lerp(entity.opacity ?? 1, 1, easedProgress),
      };
    });

    return {
      ...baseKeyframe,
      entities: interpolatedEntities,
    };
  }

  /**
   * Linear interpolation.
   */
  private lerp(a: number, b: number, t: number): number {
    return a + (b - a) * t;
  }

  /**
   * Schedule session cleanup after timeout.
   */
  private scheduleSessionCleanup(sessionId: SimulationSessionId): void {
    // Redis handles cleanup via TTL
  }
}

/**
 * Session analytics interface.
 */
export interface SessionAnalytics {
  sessionId: SimulationSessionId;
  manifestId: string;
  startedAt: Date;
  duration: number;
  totalInteractions: number;
  forwardSteps: number;
  backwardSteps: number;
  seeks: number;
  avgStepDuration: number;
  completionPercentage: number;
  kernelAnalytics: Record<string, unknown>;
}

/**
 * Create a new runtime service instance.
 */
export function createRuntimeService(): SimRuntimeService {
  return new SimulationRuntimeService();
}

export { SimulationRuntimeService as SimRuntimeServiceImpl };
