/**
 * Physics Simulation Kernel
 * 
 * @doc.type class
 * @doc.purpose Execute physics simulations using a simplified Euler integration solver.
 * @doc.layer product
 * @doc.pattern Kernel
 * @note This implementation currently uses a custom Euler integration loop. 
 *       Future versions may upgrade to Rapier WASM for more advanced physics.
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
  PhysicsVectorEntity,
  SimulationRunRequest,
  SimulationRunResult
} from "@ghatana/tutorputor-contracts/v1/simulation";

/**
 * Integration method for physics stepping.
 */
export type IntegrationMethod = 'euler' | 'verlet' | 'rk4';

// ============================================================================
// Seeded PRNG (xoshiro128** — fast, deterministic, good distribution)
// ============================================================================

/**
 * Seeded pseudo-random number generator for deterministic simulation replay.
 * Uses the xoshiro128** algorithm for speed and statistical quality.
 */
export class SeededRandom {
  private s: Uint32Array;

  constructor(seed: number) {
    // SplitMix32 to initialize state from a single seed
    this.s = new Uint32Array(4);
    let z = (seed | 0) >>> 0;
    for (let i = 0; i < 4; i++) {
      z = (z + 0x9e3779b9) >>> 0;
      let t = z ^ (z >>> 16);
      t = Math.imul(t, 0x21f0aaad);
      t = t ^ (t >>> 15);
      t = Math.imul(t, 0x735a2d97);
      t = t ^ (t >>> 15);
      this.s[i] = t >>> 0;
    }
    // Ensure non-zero state
    if (this.s[0] === 0 && this.s[1] === 0 && this.s[2] === 0 && this.s[3] === 0) {
      this.s[0] = 1;
    }
  }

  /** Returns a float in [0, 1) */
  next(): number {
    const s = this.s;
    const result = Math.imul(this.rotl(Math.imul(s[1], 5), 7), 9) >>> 0;
    const t = (s[1] << 9) >>> 0;

    s[2] = (s[2] ^ s[0]) >>> 0;
    s[3] = (s[3] ^ s[1]) >>> 0;
    s[1] = (s[1] ^ s[2]) >>> 0;
    s[0] = (s[0] ^ s[3]) >>> 0;
    s[2] = (s[2] ^ t) >>> 0;
    s[3] = this.rotl(s[3], 11) >>> 0;

    return result / 0x100000000;
  }

  /** Returns an integer in [min, max] inclusive */
  nextInt(min: number, max: number): number {
    return Math.floor(this.next() * (max - min + 1)) + min;
  }

  /** Returns a float in [min, max) */
  nextFloat(min: number, max: number): number {
    return this.next() * (max - min) + min;
  }

  /** Returns a normally distributed value (Box-Muller) */
  nextGaussian(mean = 0, stddev = 1): number {
    const u1 = this.next();
    const u2 = this.next();
    const z = Math.sqrt(-2 * Math.log(u1 || 1e-10)) * Math.cos(2 * Math.PI * u2);
    return z * stddev + mean;
  }

  /** Serialize state for session persistence */
  serialize(): number[] {
    return [this.s[0], this.s[1], this.s[2], this.s[3]];
  }

  /** Restore state from serialized form */
  static deserialize(state: number[]): SeededRandom {
    const rng = new SeededRandom(0);
    rng.s[0] = state[0] >>> 0;
    rng.s[1] = state[1] >>> 0;
    rng.s[2] = state[2] >>> 0;
    rng.s[3] = state[3] >>> 0;
    return rng;
  }

  private rotl(x: number, k: number): number {
    return ((x << k) | (x >>> (32 - k))) >>> 0;
  }
}

/**
 * Physics world configuration.
 */
interface PhysicsWorldConfig {
  gravity: { x: number; y: number };
  timeStep: number;
  velocityIterations: number;
  positionIterations: number;
  integrationMethod: IntegrationMethod;
  maxSteps: number;
  maxRuntimeMs: number;
  seed: number;
}

/**
 * Collision manifold from narrowphase detection.
 */
interface CollisionManifold {
  bodyA: SimEntityId;
  bodyB: SimEntityId;
  normal: { x: number; y: number };
  depth: number;
}

/**
 * Shape type for collision detection.
 */
type BodyShape = 'circle' | 'rect';

/**
 * AABB bounding box for broadphase.
 */
interface AABB {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

/**
 * Internal physics body representation.
 */
interface PhysicsBody {
  id: SimEntityId;
  x: number;
  y: number;
  prevX: number;
  prevY: number;
  vx: number;
  vy: number;
  ax: number;
  ay: number;
  mass: number;
  friction: number;
  restitution: number;
  fixed: boolean;
  shape: BodyShape;
  radius: number;
  halfWidth: number;
  halfHeight: number;
}

/**
 * Internal spring representation.
 */
interface Spring {
  id: SimEntityId;
  anchorId: SimEntityId;
  attachId: SimEntityId;
  stiffness: number;
  damping: number;
  restLength: number;
}

/**
 * Stateful Physics Kernel implementation.
 */
export class PhysicsKernel implements SimKernelService {
  private manifest: SimulationManifest | null = null;
  private bodies = new Map<SimEntityId, PhysicsBody>();
  private springs: Spring[] = [];
  private entities = new Map<SimEntityId, SimEntity>();
  private worldConfig: PhysicsWorldConfig;
  private currentStepIndex = 0;
  private currentTime = 0;
  /** Seeded PRNG for deterministic replay */
  private rng: SeededRandom;

  readonly domain = "PHYSICS" as const;

  constructor(config?: PhysicsConfig) {
    const seed = (config as any)?.seed ?? Date.now();
    this.worldConfig = {
      gravity: config?.gravity ?? { x: 0, y: 9.81 },
      timeStep: (config as any)?.timeStep ?? 1 / 60,
      velocityIterations: 8,
      positionIterations: 3,
      integrationMethod: (config as any)?.integrationMethod ?? 'euler',
      maxSteps: (config as any)?.maxSteps ?? 10000,
      maxRuntimeMs: (config as any)?.maxRuntimeMs ?? 30000,
      seed,
    };
    this.rng = new SeededRandom(seed);
  }

  /** Get the PRNG for use in stochastic simulation elements */
  getRng(): SeededRandom {
    return this.rng;
  }

  /** Serialize PRNG state for session persistence / resumption */
  serializeRngState(): number[] {
    return this.rng.serialize();
  }

  /** Restore PRNG state from a previous session */
  restoreRngState(state: number[]): void {
    this.rng = SeededRandom.deserialize(state);
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
        errors: [`Manifest domain is not ${this.domain}`]
      };
    }

    const startTime = Date.now();
    this.initialize(request.manifest);

    const keyframes: SimKeyframe[] = [];

    // Initial state keyframe
    keyframes.push({
      stepIndex: -1,
      timestamp: 0,
      entities: Array.from(this.entities.values()).map(e => ({ ...e }))
    });

    // Run simulation steps
    if (this.manifest) {
      for (let i = 0; i < this.manifest.steps.length; i++) {
        this.step();
        keyframes.push({
          stepIndex: i,
          timestamp: this.currentTime,
          entities: Array.from(this.entities.values()).map(e => ({ ...e }))
        });
      }
    }

    return {
      simulationId: request.manifest.id,
      keyframes,
      totalSteps: this.currentStepIndex,
      executionTimeMs: Date.now() - startTime
    };
  }

  async runDeterministic(request: SimulationRunRequest, seed: number): Promise<SimulationRunResult> {
    // For now, just ignore the seed as our simple physics engine is deterministic by default
    return this.run(request);
  }

  configureWorld(config: Partial<PhysicsWorldConfig>): void {
    if (config.gravity) {
      this.worldConfig.gravity = config.gravity;
    }
    if (config.timeStep) {
      this.worldConfig.timeStep = config.timeStep;
    }
    if (config.velocityIterations) {
      this.worldConfig.velocityIterations = config.velocityIterations;
    }
    if (config.positionIterations) {
      this.worldConfig.positionIterations = config.positionIterations;
    }
  }


  initialize(manifest: SimulationManifest): void {
    this.manifest = manifest;
    this.reset();
  }

  reset(): void {
    if (!this.manifest) return;

    this.bodies.clear();
    this.springs = [];
    this.entities.clear();
    this.currentStepIndex = 0;
    this.currentTime = 0;

    // Extract physics metadata
    if (this.manifest.domainMetadata && "physics" in this.manifest.domainMetadata) {
      const physics = this.manifest.domainMetadata.physics as any;
      if (physics.gravity) {
        this.worldConfig.gravity = physics.gravity;
      }
      if (physics.timeScale) {
        this.worldConfig.timeStep = (1 / 60) * physics.timeScale;
      }
    }

    // Initialize entities
    for (const entity of this.manifest.initialEntities) {
      this.entities.set(entity.id, { ...entity });

      if (entity.type === "rigidBody") {
        const body = entity as PhysicsBodyEntity;
        this.bodies.set(entity.id, createPhysicsBodyFromEntity(body));
      } else if (entity.type === "spring") {
        const spring = entity as PhysicsSpringEntity;
        this.springs.push({
          id: entity.id,
          anchorId: spring.anchorId,
          attachId: spring.attachId,
          stiffness: spring.stiffness,
          damping: spring.damping,
          restLength: spring.restLength
        });
      }
    }
  }

  step(): void {
    if (!this.manifest) return;
    if (this.currentStepIndex >= this.manifest.steps.length) return;

    const step = this.manifest.steps[this.currentStepIndex];

    // Reset forces/acceleration for this step
    for (const body of this.bodies.values()) {
      body.ax = 0;
      body.ay = 0;
    }

    // Apply step actions
    for (const action of step.actions) {
      applyPhysicsAction(action, this.bodies, this.springs, this.entities, this.worldConfig);
    }

    // Simulate physics for this step
    const stepDuration = step.duration ?? 1000; // ms
    const frameCount = Math.ceil(stepDuration / (this.worldConfig.timeStep * 1000));

    const integrate = this.worldConfig.integrationMethod === 'verlet'
      ? integrateVerlet
      : integrateEuler;

    for (let frame = 0; frame < frameCount; frame++) {
      // Apply forces from springs
      applySpringForces(this.bodies, this.springs);

      // Apply gravity and integrate using selected method
      for (const [id, body] of this.bodies) {
        if (body.fixed) continue;

        // Calculate total acceleration (external + gravity)
        const totalAx = body.ax + this.worldConfig.gravity.x;
        const totalAy = body.ay + this.worldConfig.gravity.y;

        integrate(body, totalAx, totalAy, this.worldConfig.timeStep);

        // Ground plane collision (y >= 500)
        if (body.y + body.halfHeight >= 500) {
          body.y = 500 - body.halfHeight;
          body.vy = -body.vy * body.restitution;
          body.vx *= (1 - body.friction);
          // Keep prevX/prevY consistent so Verlet doesn't re-accelerate
          body.prevX = body.x - body.vx * this.worldConfig.timeStep;
          body.prevY = body.y - body.vy * this.worldConfig.timeStep;
        }

        // Update entity position
        const entity = this.entities.get(id);
        if (entity) {
          entity.x = body.x;
          entity.y = body.y;
          if (entity.type === "rigidBody") {
            (entity as PhysicsBodyEntity).velocityX = body.vx;
            (entity as PhysicsBodyEntity).velocityY = body.vy;
          }
        }
      }

      // Body-to-body collision detection and resolution
      resolveCollisions(this.bodies);

      // Update vector entities
      updateVectorEntities(this.bodies, this.entities);
    }

    this.currentTime += stepDuration;
    this.currentStepIndex++;
  }

  interpolate(t: number): Partial<SimKeyframe> {
    // Simple interpolation not implemented for now, returning current state
    return {
      entities: Array.from(this.entities.values()).map(e => ({ ...e }))
    };
  }

  serialize(): string {
    return JSON.stringify({
      bodies: Array.from(this.bodies.entries()),
      springs: this.springs,
      entities: Array.from(this.entities.entries()),
      currentStepIndex: this.currentStepIndex,
      currentTime: this.currentTime,
      worldConfig: this.worldConfig
    });
  }

  deserialize(state: string): void {
    const data = JSON.parse(state);
    this.bodies = new Map(data.bodies);
    this.springs = data.springs;
    this.entities = new Map(data.entities);
    this.currentStepIndex = data.currentStepIndex;
    this.currentTime = data.currentTime;
    this.worldConfig = data.worldConfig;
  }

  getAnalytics(): Record<string, unknown> {
    return {
      bodiesCount: this.bodies.size,
      springsCount: this.springs.length,
      entitiesCount: this.entities.size
    };
  }

  async checkHealth(): Promise<boolean> {
    return true;
  }
}

export function createPhysicsKernel(config?: PhysicsConfig): SimKernelService {
  return new PhysicsKernel(config);
}

/**
 * Euler integration: first-order, simple but energy-non-conserving.
 * x(t+dt) = x(t) + v(t)*dt
 * v(t+dt) = v(t) + a(t)*dt
 */
function integrateEuler(body: PhysicsBody, ax: number, ay: number, dt: number): void {
  body.prevX = body.x;
  body.prevY = body.y;
  body.vx += ax * dt;
  body.vy += ay * dt;
  body.x += body.vx * dt;
  body.y += body.vy * dt;
}

/**
 * Velocity Verlet integration: second-order, better energy conservation than Euler.
 * x(t+dt) = x(t) + v(t)*dt + 0.5*a(t)*dt^2
 * v(t+dt) = v(t) + a(t)*dt  (simplified: uses same acceleration for both half-steps)
 *
 * For spring-mass systems and orbital mechanics this provides significantly
 * more stable trajectories than standard Euler at the same step size.
 */
function integrateVerlet(body: PhysicsBody, ax: number, ay: number, dt: number): void {
  const newX = body.x + body.vx * dt + 0.5 * ax * dt * dt;
  const newY = body.y + body.vy * dt + 0.5 * ay * dt * dt;
  // Velocity update using average acceleration (half-step at t, half-step at t+dt)
  // Since we don't recompute forces here, use same acceleration for both halves
  body.vx += ax * dt;
  body.vy += ay * dt;
  body.prevX = body.x;
  body.prevY = body.y;
  body.x = newX;
  body.y = newY;
}

/**
 * Apply a physics action to the simulation state.
 */
function applyPhysicsAction(
  action: any,
  bodies: Map<SimEntityId, PhysicsBody>,
  springs: Spring[],
  entities: Map<SimEntityId, SimEntity>,
  worldConfig: PhysicsWorldConfig
): void {
  switch (action.action) {
    case "SET_INITIAL_VELOCITY": {
      const targetId = action.targetId as SimEntityId;
      const body = bodies.get(targetId);
      if (body) {
        body.vx = action.vx as number;
        body.vy = action.vy as number;
      }
      break;
    }

    case "APPLY_FORCE": {
      const targetId = action.targetId as SimEntityId;
      const body = bodies.get(targetId);
      if (body) {
        const fx = action.fx as number;
        const fy = action.fy as number;
        if (action.impulse) {
          // Impulse: immediate velocity change
          body.vx += fx / body.mass;
          body.vy += fy / body.mass;
        } else {
          // Force: add to acceleration
          body.ax += fx / body.mass;
          body.ay += fy / body.mass;
        }
      }
      break;
    }

    case "CONNECT_SPRING": {
      springs.push({
        id: `spring_${Date.now()}` as SimEntityId,
        anchorId: action.body1Id as SimEntityId,
        attachId: action.body2Id as SimEntityId,
        stiffness: action.stiffness as number,
        damping: action.damping as number,
        restLength: (action.restLength as number) || 0
      });
      break;
    }

    case "RELEASE": {
      const targetId = action.targetId as SimEntityId;
      const body = bodies.get(targetId);
      if (body) {
        body.fixed = false;
      }
      break;
    }

    case "SET_GRAVITY": {
      worldConfig.gravity = {
        x: action.gx as number,
        y: action.gy as number
      };
      break;
    }

    case "CREATE_ENTITY": {
      const entity = action.entity as SimEntity;
      entities.set(entity.id, { ...entity });
      if (entity.type === "rigidBody") {
        const body = entity as PhysicsBodyEntity;
        bodies.set(entity.id, createPhysicsBodyFromEntity(body));
      }
      break;
    }

    case "REMOVE_ENTITY": {
      const targetId = action.targetId as SimEntityId;
      entities.delete(targetId);
      bodies.delete(targetId);
      break;
    }
  }
}

/**
 * Apply spring forces to connected bodies.
 */
function applySpringForces(
  bodies: Map<SimEntityId, PhysicsBody>,
  springs: Spring[]
): void {
  for (const spring of springs) {
    const anchor = bodies.get(spring.anchorId);
    const attach = bodies.get(spring.attachId);

    if (!anchor || !attach) continue;

    // Calculate displacement
    const dx = attach.x - anchor.x;
    const dy = attach.y - anchor.y;
    const distance = Math.sqrt(dx * dx + dy * dy);

    if (distance === 0) continue;

    // Spring force (Hooke's law)
    const stretch = distance - spring.restLength;
    const forceMagnitude = spring.stiffness * stretch;

    // Damping force
    const relVx = attach.vx - anchor.vx;
    const relVy = attach.vy - anchor.vy;
    const dampingForce = spring.damping * (relVx * dx + relVy * dy) / distance;

    // Apply force
    const fx = (forceMagnitude + dampingForce) * (dx / distance);
    const fy = (forceMagnitude + dampingForce) * (dy / distance);

    if (!attach.fixed) {
      attach.ax -= fx / attach.mass;
      attach.ay -= fy / attach.mass;
    }
    if (!anchor.fixed) {
      anchor.ax += fx / anchor.mass;
      anchor.ay += fy / anchor.mass;
    }
  }
}

/**
 * Update vector entities to reflect current body state.
 */
function updateVectorEntities(
  bodies: Map<SimEntityId, PhysicsBody>,
  entities: Map<SimEntityId, SimEntity>
): void {
  for (const [id, entity] of entities) {
    if (entity.type === "vector") {
      const vector = entity as PhysicsVectorEntity;
      if (vector.attachId) {
        const body = bodies.get(vector.attachId);
        if (body) {
          // Update vector position to match body
          entity.x = body.x;
          entity.y = body.y;

          // Update magnitude and angle based on vector type
          switch (vector.vectorType) {
            case "velocity":
              (entity as PhysicsVectorEntity).magnitude = Math.sqrt(body.vx * body.vx + body.vy * body.vy);
              (entity as PhysicsVectorEntity).angle = Math.atan2(body.vy, body.vx) * (180 / Math.PI);
              break;
            case "acceleration":
              (entity as PhysicsVectorEntity).magnitude = Math.sqrt(body.ax * body.ax + body.ay * body.ay);
              (entity as PhysicsVectorEntity).angle = Math.atan2(body.ay, body.ax) * (180 / Math.PI);
              break;
          }
        }
      }
    }
  }
}

/**
 * Create an internal PhysicsBody from a contract PhysicsBodyEntity.
 */
function createPhysicsBodyFromEntity(entity: PhysicsBodyEntity): PhysicsBody {
  const shape: BodyShape = entity.shape === 'rect' ? 'rect' : 'circle';
  const w = entity.width ?? 20;
  const h = entity.height ?? 20;
  const radius = shape === 'circle' ? Math.max(w, h) / 2 : 0;

  return {
    id: entity.id,
    x: entity.x,
    y: entity.y,
    prevX: entity.x,
    prevY: entity.y,
    vx: entity.velocityX || 0,
    vy: entity.velocityY || 0,
    ax: entity.accelerationX || 0,
    ay: entity.accelerationY || 0,
    mass: entity.mass,
    friction: entity.friction || 0.3,
    restitution: entity.restitution || 0.5,
    fixed: entity.fixed || false,
    shape,
    radius,
    halfWidth: w / 2,
    halfHeight: h / 2,
  };
}

/**
 * Compute AABB for broadphase collision detection.
 */
function getAABB(body: PhysicsBody): AABB {
  if (body.shape === 'circle') {
    return {
      minX: body.x - body.radius,
      minY: body.y - body.radius,
      maxX: body.x + body.radius,
      maxY: body.y + body.radius,
    };
  }
  return {
    minX: body.x - body.halfWidth,
    minY: body.y - body.halfHeight,
    maxX: body.x + body.halfWidth,
    maxY: body.y + body.halfHeight,
  };
}

/**
 * Broadphase: AABB overlap test.
 */
function aabbOverlap(a: AABB, b: AABB): boolean {
  return a.minX <= b.maxX && a.maxX >= b.minX &&
         a.minY <= b.maxY && a.maxY >= b.minY;
}

/**
 * Narrowphase: circle vs circle collision detection.
 */
function circleVsCircle(a: PhysicsBody, b: PhysicsBody): CollisionManifold | null {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const distSq = dx * dx + dy * dy;
  const sumR = a.radius + b.radius;

  if (distSq >= sumR * sumR) return null;

  const dist = Math.sqrt(distSq);
  if (dist === 0) {
    return { bodyA: a.id, bodyB: b.id, normal: { x: 1, y: 0 }, depth: sumR };
  }

  return {
    bodyA: a.id,
    bodyB: b.id,
    normal: { x: dx / dist, y: dy / dist },
    depth: sumR - dist,
  };
}

/**
 * Narrowphase: AABB vs AABB collision detection.
 */
function aabbVsAABB(a: PhysicsBody, b: PhysicsBody): CollisionManifold | null {
  const overlapX = (a.halfWidth + b.halfWidth) - Math.abs(b.x - a.x);
  const overlapY = (a.halfHeight + b.halfHeight) - Math.abs(b.y - a.y);

  if (overlapX <= 0 || overlapY <= 0) return null;

  if (overlapX < overlapY) {
    const sign = b.x > a.x ? 1 : -1;
    return { bodyA: a.id, bodyB: b.id, normal: { x: sign, y: 0 }, depth: overlapX };
  } else {
    const sign = b.y > a.y ? 1 : -1;
    return { bodyA: a.id, bodyB: b.id, normal: { x: 0, y: sign }, depth: overlapY };
  }
}

/**
 * Narrowphase: circle vs AABB collision detection.
 */
function circleVsAABB(circle: PhysicsBody, rect: PhysicsBody): CollisionManifold | null {
  // Find closest point on AABB to circle center
  const closestX = Math.max(rect.x - rect.halfWidth, Math.min(circle.x, rect.x + rect.halfWidth));
  const closestY = Math.max(rect.y - rect.halfHeight, Math.min(circle.y, rect.y + rect.halfHeight));

  const dx = circle.x - closestX;
  const dy = circle.y - closestY;
  const distSq = dx * dx + dy * dy;

  if (distSq >= circle.radius * circle.radius) return null;

  const dist = Math.sqrt(distSq);
  if (dist === 0) {
    return { bodyA: circle.id, bodyB: rect.id, normal: { x: 0, y: -1 }, depth: circle.radius };
  }

  return {
    bodyA: circle.id,
    bodyB: rect.id,
    normal: { x: dx / dist, y: dy / dist },
    depth: circle.radius - dist,
  };
}

/**
 * Narrowphase dispatcher: detect collision between two bodies.
 */
function narrowphase(a: PhysicsBody, b: PhysicsBody): CollisionManifold | null {
  if (a.shape === 'circle' && b.shape === 'circle') {
    return circleVsCircle(a, b);
  }
  if (a.shape === 'rect' && b.shape === 'rect') {
    return aabbVsAABB(a, b);
  }
  if (a.shape === 'circle' && b.shape === 'rect') {
    return circleVsAABB(a, b);
  }
  // rect vs circle: flip and negate normal
  const m = circleVsAABB(b, a);
  if (m) {
    return { bodyA: a.id, bodyB: b.id, normal: { x: -m.normal.x, y: -m.normal.y }, depth: m.depth };
  }
  return null;
}

/**
 * Detect and resolve all body-to-body collisions using impulse-based response.
 */
function resolveCollisions(bodies: Map<SimEntityId, PhysicsBody>): void {
  const bodyList = Array.from(bodies.values());
  const len = bodyList.length;

  for (let i = 0; i < len; i++) {
    for (let j = i + 1; j < len; j++) {
      const a = bodyList[i];
      const b = bodyList[j];

      // Skip if both fixed
      if (a.fixed && b.fixed) continue;

      // Broadphase AABB check
      if (!aabbOverlap(getAABB(a), getAABB(b))) continue;

      // Narrowphase
      const manifold = narrowphase(a, b);
      if (!manifold) continue;

      const { normal, depth } = manifold;

      // Positional correction (prevent sinking)
      const totalInvMass = (a.fixed ? 0 : 1 / a.mass) + (b.fixed ? 0 : 1 / b.mass);
      if (totalInvMass === 0) continue;

      const correction = depth / totalInvMass;
      if (!a.fixed) {
        a.x -= normal.x * correction / a.mass;
        a.y -= normal.y * correction / a.mass;
      }
      if (!b.fixed) {
        b.x += normal.x * correction / b.mass;
        b.y += normal.y * correction / b.mass;
      }

      // Impulse-based velocity resolution
      const relVx = b.vx - a.vx;
      const relVy = b.vy - a.vy;
      const relVelAlongNormal = relVx * normal.x + relVy * normal.y;

      // Only resolve if bodies are approaching
      if (relVelAlongNormal > 0) continue;

      const restitution = Math.min(a.restitution, b.restitution);
      const impulseMag = -(1 + restitution) * relVelAlongNormal / totalInvMass;

      if (!a.fixed) {
        a.vx -= impulseMag * normal.x / a.mass;
        a.vy -= impulseMag * normal.y / a.mass;
      }
      if (!b.fixed) {
        b.vx += impulseMag * normal.x / b.mass;
        b.vy += impulseMag * normal.y / b.mass;
      }

      // Friction impulse (tangent direction)
      const tangentX = relVx - relVelAlongNormal * normal.x;
      const tangentY = relVy - relVelAlongNormal * normal.y;
      const tangentLen = Math.sqrt(tangentX * tangentX + tangentY * tangentY);

      if (tangentLen > 1e-6) {
        const tx = tangentX / tangentLen;
        const ty = tangentY / tangentLen;
        const frictionCoeff = Math.sqrt(a.friction * b.friction);
        const frictionImpulse = Math.min(
          frictionCoeff * Math.abs(impulseMag),
          tangentLen / totalInvMass
        );

        if (!a.fixed) {
          a.vx += frictionImpulse * tx / a.mass;
          a.vy += frictionImpulse * ty / a.mass;
        }
        if (!b.fixed) {
          b.vx -= frictionImpulse * tx / b.mass;
          b.vy -= frictionImpulse * ty / b.mass;
        }
      }
    }
  }
}
