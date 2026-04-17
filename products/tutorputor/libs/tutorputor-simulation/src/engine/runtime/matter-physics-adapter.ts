/**
 * Matter.js Physics Adapter
 *
 * Production-grade physics simulation using Matter.js engine.
 * Replaces custom Euler integration with robust 2D physics.
 *
 * @doc.type class
 * @doc.purpose Matter.js physics integration for simulations
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type {
  SimKernelService,
  PhysicsConfig,
  SimulationManifest,
  SimKeyframe,
  SimEntity,
  SimEntityId,
  PhysicsBodyEntity,
  PhysicsSpringEntity,
  SimulationRunRequest,
  SimulationRunResult,
} from "@tutorputor/contracts/v1/simulation";

// Matter.js types (will be imported dynamically)
type MatterEngine = {
  world: MatterWorld;
  timing: { timestamp: number; timeScale: number };
  enabled: boolean;
};

type MatterWorld = {
  gravity: { x: number; y: number; scale: number };
  bodies: unknown[];
  constraints: unknown[];
};

type MatterBody = {
  id: number;
  position: { x: number; y: number };
  velocity: { x: number; y: number };
  angle: number;
  angularVelocity: number;
  mass: number;
  density: number;
  friction: number;
  frictionAir: number;
  restitution: number;
  isStatic: boolean;
  plugin?: { entityId?: string };
};

type MatterConstraint = {
  id: number;
  bodyA?: MatterBody;
  bodyB?: MatterBody;
  pointA: { x: number; y: number };
  pointB: { x: number; y: number };
  stiffness: number;
  damping: number;
  length: number;
};

type MatterVector = {
  x: number;
  y: number;
};

/**
 * Physics world configuration
 */
interface MatterWorldConfig {
  gravity: { x: number; y: number };
  timeStep: number;
  velocityIterations: number;
  positionIterations: number;
  constraintIterations: number;
  maxSteps: number;
  maxRuntimeMs: number;
}

/**
 * Matter.js Physics Adapter
 *
 * Wraps Matter.js for simulation kernel integration
 */
export class MatterPhysicsAdapter implements SimKernelService {
  private manifest: SimulationManifest | null = null;
  private engine: MatterEngine | null = null;
  private bodies = new Map<SimEntityId, MatterBody>();
  private constraints = new Map<SimEntityId, MatterConstraint>();
  private entities = new Map<SimEntityId, SimEntity>();
  private worldConfig: MatterWorldConfig;
  private Matter: {
    Engine: { create: (options: unknown) => MatterEngine; update: (engine: MatterEngine, delta: number) => void };
    World: { add: (world: MatterWorld, bodies: unknown[]) => void; remove: (world: MatterWorld, bodies: unknown[]) => void };
    Bodies: {
      circle: (x: number, y: number, radius: number, options: unknown) => MatterBody;
      rectangle: (x: number, y: number, width: number, height: number, options: unknown) => MatterBody;
    };
    Constraint: { create: (options: unknown) => MatterConstraint };
    Body: { setPosition: (body: MatterBody, position: MatterVector) => void; setVelocity: (body: MatterBody, velocity: MatterVector) => void; applyForce: (body: MatterBody, position: MatterVector, force: MatterVector) => void };
    Composite: { allBodies: (engine: MatterEngine) => MatterBody[] };
    Vector: { create: (x: number, y: number) => MatterVector };
  } | null = null;

  readonly domain = "PHYSICS" as const;

  constructor(config?: PhysicsConfig) {
    const rawConfig = config as
      | (Record<string, unknown> & {
          gravity?: { x: number; y: number };
        })
      | undefined;

    this.worldConfig = {
      gravity: config?.gravity ?? { x: 0, y: 1 }, // Matter.js uses positive Y for down
      timeStep: this.toNumber(rawConfig?.timeStep, 1000 / 60), // ms
      velocityIterations: 8,
      positionIterations: 8,
      constraintIterations: 3,
      maxSteps: this.toNumber(rawConfig?.maxSteps, 10000),
      maxRuntimeMs: this.toNumber(rawConfig?.maxRuntimeMs, 30000),
    };
  }

  private toNumber(value: unknown, fallback: number): number {
    return typeof value === "number" && Number.isFinite(value) ? value : fallback;
  }

  /**
   * Initialize Matter.js engine
   */
  private async initializeMatter(): Promise<void> {
    if (this.Matter) return;

    try {
      // Dynamic import for server-side rendering compatibility
      const matterModule = await import("matter-js");
      this.Matter = matterModule.default ?? matterModule;
    } catch (error) {
      throw new Error(
        `Matter.js not available. Install with: npm install matter-js. Error: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }

  canExecute(manifest: SimulationManifest): boolean {
    return manifest.domain === "PHYSICS";
  }

  async run(request: SimulationRunRequest): Promise<SimulationRunResult> {
    if (request.manifest.domain !== this.domain) {
      return {
        simulationId: request.manifest.id,
        keyframes: [],
        totalSteps: 0,
        executionTimeMs: 0,
        errors: [`Manifest domain is not ${this.domain}`],
      };
    }

    await this.initializeMatter();

    const startTime = Date.now();
    this.initialize(request.manifest);

    const keyframes: SimKeyframe[] = [];

    // Initial state keyframe
    keyframes.push({
      stepIndex: -1,
      timestamp: 0,
      entities: this.captureEntityState(),
      annotations: [],
    });

    // Run simulation steps
    if (this.manifest && this.engine && this.Matter) {
      for (let i = 0; i < this.manifest.steps.length; i++) {
        this.step(i);

        // Capture keyframe
        keyframes.push({
          stepIndex: i,
          timestamp: this.engine.timing.timestamp,
          entities: this.captureEntityState(),
          annotations: [],
        });

        // Check runtime limit
        if (Date.now() - startTime > this.worldConfig.maxRuntimeMs) {
          return {
            simulationId: request.manifest.id,
            keyframes,
            totalSteps: i + 1,
            executionTimeMs: Date.now() - startTime,
            errors: ["Simulation exceeded maximum runtime"],
          };
        }
      }
    }

    return {
      simulationId: request.manifest.id,
      keyframes,
      totalSteps: keyframes.length - 1,
      executionTimeMs: Date.now() - startTime,
    };
  }

  async runDeterministic(
    request: SimulationRunRequest,
    _seed: number,
  ): Promise<SimulationRunResult> {
    // Matter.js is deterministic given the same initial conditions
    // The seed could be used for random variation in the future
    return this.run(request);
  }

  initialize(manifest: SimulationManifest): void {
    this.manifest = manifest;
    this.reset();
  }

  reset(): void {
    if (!this.Matter) return;
    if (!this.manifest) return;

    // Clear existing state
    this.bodies.clear();
    this.constraints.clear();
    this.entities.clear();

    // Create new engine
    const rawConfig = this.manifest.domainMetadata as
      | { physics?: { gravity?: { value?: number[] }; timeScale?: number } }
      | undefined;

    const gravityValue = rawConfig?.physics?.gravity?.value;
    const gravity = gravityValue
      ? { x: gravityValue[0] ?? 0, y: (gravityValue[1] ?? -9.81) * -1 } // Flip Y for Matter.js
      : this.worldConfig.gravity;

    this.engine = this.Matter.Engine.create({
      gravity: { x: gravity.x, y: gravity.y, scale: 0.001 },
      timing: {
        timeScale: rawConfig?.physics?.timeScale ?? 1,
      },
      enableSleeping: true,
    });

    // Initialize entities
    for (const entity of this.manifest.initialEntities) {
      this.entities.set(entity.id, { ...entity });

      if (entity.type === "rigidBody") {
        this.createBody(entity as PhysicsBodyEntity);
      } else if (entity.type === "spring") {
        this.createConstraint(entity as PhysicsSpringEntity);
      }
    }
  }

  /**
   * Create a Matter.js body from entity
   */
  private createBody(entity: PhysicsBodyEntity): void {
    if (!this.Matter || !this.engine) return;

    const shape = entity.shape;
    const x = entity.position?.x ?? entity.x ?? 0;
    const y = entity.position?.y ?? entity.y ?? 0;

    let body: MatterBody;

    const commonOptions = {
      restitution: entity.restitution ?? 0.3,
      friction: entity.friction ?? 0.1,
      frictionAir: entity.frictionAir ?? entity.airResistance ?? 0.01,
      density: entity.density ?? entity.mass ?? 0.001,
      isStatic: entity.isStatic ?? entity.fixed ?? false,
      plugin: { entityId: entity.id },
    };

    if (shape === "circle") {
      const radius =
        entity.radius ??
        (entity as unknown as { circleRadius?: number }).circleRadius ??
        20;
      body = this.Matter.Bodies.circle(x, y, radius, commonOptions);
    } else {
      // Rectangle or default
      const width =
        entity.width ??
        (entity as unknown as { boxWidth?: number }).boxWidth ??
        40;
      const height =
        entity.height ??
        (entity as unknown as { boxHeight?: number }).boxHeight ??
        40;
      body = this.Matter.Bodies.rectangle(x, y, width, height, commonOptions);
    }

    // Set initial velocity if provided
    if (entity.velocityX !== undefined || entity.velocityY !== undefined) {
      this.Matter.Body.setVelocity(body, {
        x: entity.velocityX ?? 0,
        y: (entity.velocityY ?? 0) * -1, // Flip Y for Matter.js
      });
    }

    // Add to world
    this.Matter.World.add(this.engine.world, [body]);
    this.bodies.set(entity.id, body);
  }

  /**
   * Create a Matter.js constraint (spring)
   */
  private createConstraint(entity: PhysicsSpringEntity): void {
    if (!this.Matter || !this.engine) return;

    const bodyA = this.bodies.get(entity.anchorId ?? entity.body1Id ?? "");
    const bodyB = this.bodies.get(entity.attachId ?? entity.body2Id ?? "");

    if (!bodyA || !bodyB) {
      console.warn(`Cannot create constraint ${entity.id}: missing bodies`);
      return;
    }

    const constraint = this.Matter.Constraint.create({
      bodyA,
      bodyB,
      stiffness: entity.stiffness ?? 0.1,
      damping: entity.damping ?? 0,
      length: entity.restLength ?? entity.length ?? 50,
    });

    this.Matter.World.add(this.engine.world, [constraint]);
    this.constraints.set(entity.id, constraint);
  }

  step(stepIndex: number): void {
    if (!this.engine || !this.Matter || !this.manifest) return;
    if (stepIndex >= this.manifest.steps.length) return;

    const step = this.manifest.steps[stepIndex];
    if (!step) return;

    // Apply step actions
    for (const action of step.actions) {
      this.applyAction(action);
    }

    // Step the physics engine
    const stepDuration = step.duration ?? 1000; // ms
    const fixedDelta = this.worldConfig.timeStep;
    const steps = Math.ceil(stepDuration / fixedDelta);

    for (let i = 0; i < steps; i++) {
      this.Matter.Engine.update(this.engine, fixedDelta);
    }

    // Update entity positions from physics bodies
    this.syncEntitiesToBodies();
  }

  /**
   * Apply simulation action
   */
  private applyAction(action: {
    type: string;
    targetId?: string;
    payload?: Record<string, unknown>;
  }): void {
    if (!this.Matter) return;

    const body = action.targetId ? this.bodies.get(action.targetId) : undefined;

    switch (action.type) {
      case "applyForce": {
        if (body && action.payload) {
          const forceX = (action.payload.forceX as number) ?? 0;
          const forceY = ((action.payload.forceY as number) ?? 0) * -1; // Flip Y
          this.Matter.Body.applyForce(
            body,
            body.position,
            this.Matter.Vector.create(forceX, forceY),
          );
        }
        break;
      }

      case "setVelocity": {
        if (body && action.payload) {
          const vx = (action.payload.velocityX as number) ?? 0;
          const vy = ((action.payload.velocityY as number) ?? 0) * -1; // Flip Y
          this.Matter.Body.setVelocity(body, this.Matter.Vector.create(vx, vy));
        }
        break;
      }

      case "setPosition": {
        if (body && action.payload) {
          const x = (action.payload.x as number) ?? body.position.x;
          const y = (action.payload.y as number) ?? body.position.y;
          this.Matter.Body.setPosition(
            body,
            this.Matter.Vector.create(x, y * -1),
          ); // Flip Y
        }
        break;
      }

      case "setGravity": {
        if (this.engine && action.payload) {
          const gravityX = (action.payload.x as number) ?? 0;
          const gravityY = ((action.payload.y as number) ?? -9.81) * -1;
          this.engine.world.gravity.x = gravityX;
          this.engine.world.gravity.y = gravityY;
        }
        break;
      }
    }
  }

  /**
   * Sync entity states from physics bodies
   */
  private syncEntitiesToBodies(): void {
    for (const [id, entity] of this.entities) {
      const body = this.bodies.get(id);
      if (body && entity.type === "rigidBody") {
        entity.x = body.position.x;
        entity.y = body.position.y * -1; // Flip Y back
        (entity as PhysicsBodyEntity).velocityX = body.velocity.x;
        (entity as PhysicsBodyEntity).velocityY = body.velocity.y * -1; // Flip Y back
        (entity as PhysicsBodyEntity).angle = body.angle;
      }
    }
  }

  /**
   * Capture current entity states
   */
  private captureEntityState(): SimEntity[] {
    return Array.from(this.entities.values()).map((e) => ({ ...e }));
  }

  interpolate(_t: number): Partial<SimKeyframe> {
    return {
      entities: this.captureEntityState(),
    };
  }

  serialize(): string {
    return JSON.stringify({
      entities: Array.from(this.entities.entries()),
      worldConfig: this.worldConfig,
    });
  }

  deserialize(state: string): void {
    const data = JSON.parse(state);
    this.entities = new Map(data.entities);
    this.worldConfig = data.worldConfig;
  }

  getAnalytics(): Record<string, unknown> {
    return {
      bodiesCount: this.bodies.size,
      constraintsCount: this.constraints.size,
      entitiesCount: this.entities.size,
      engineEnabled: this.engine?.enabled ?? false,
    };
  }

  async checkHealth(): Promise<boolean> {
    return this.Matter !== null;
  }
}

/**
 * Factory function
 */
export function createMatterPhysicsAdapter(
  config?: PhysicsConfig,
): MatterPhysicsAdapter {
  return new MatterPhysicsAdapter(config);
}
