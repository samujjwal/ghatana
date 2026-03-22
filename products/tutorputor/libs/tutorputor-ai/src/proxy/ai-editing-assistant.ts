/**
 * AI-Assisted Content Enhancement service for Tutorputor.
 *
 * Wraps the platform LLM gateway (OpenAI / Ollama) to provide intelligent
 * suggestions for animation improvement, simulation optimisation, and example
 * clarity enhancement.  All methods follow the same JSON-structured-output
 * pattern used by TutorPutorAIProxyService so they can share the same model
 * configuration and API key.
 *
 * @doc.type class
 * @doc.purpose AI-powered content editing assistance (animations, simulations, examples)
 * @doc.layer product
 * @doc.pattern Service
 */

import OpenAI from "openai";

// ---------------------------------------------------------------------------
// Shared domain types (kept in this file to avoid circular imports; consumers
// that need them can re-export via their own barrel).
// ---------------------------------------------------------------------------

export interface Vec2 {
  x: number;
  y: number;
}

export interface Keyframe {
  id: string;
  timeMs: number;
  properties: Record<string, number | string>;
}

export type EasingFunction =
  | "linear"
  | "ease-in"
  | "ease-out"
  | "ease-in-out"
  | "bounce"
  | "elastic";

export interface AnimationLayer {
  id: string;
  name: string;
  type: "shape" | "text" | "image" | "group";
  visible: boolean;
  locked: boolean;
  keyframes: Keyframe[];
  color: string;
  easing?: EasingFunction;
}

export interface AnimationConfig {
  id: string;
  name: string;
  durationMs: number;
  fps: number;
  layers: AnimationLayer[];
}

export interface SimulationEntity {
  id: string;
  name: string;
  shape: "circle" | "rectangle" | "triangle";
  color: string;
  position: Vec2;
  velocity: Vec2;
  mass: number;
  radius: number;
  dynamic: boolean;
}

export interface PhysicsParameters {
  gravityY: number;
  friction: number;
  restitution: number;
  timeScale: number;
}

export interface SimulationGoal {
  id: string;
  description: string;
  entityId: string;
  type: "reach_position" | "avoid_zone" | "survive_duration" | "custom";
  targetPosition?: Vec2;
  durationMs?: number;
}

export interface SimulationManifest {
  id: string;
  name: string;
  description: string;
  entities: SimulationEntity[];
  physics: PhysicsParameters;
  goals: SimulationGoal[];
  canvasSize: { width: number; height: number };
  domain?: string;
}

export interface ContentExample {
  id: string;
  title: string;
  body: string;
  subject: string;
  context?: string;
  realWorldConnection?: string;
}

// ---------------------------------------------------------------------------
// Assistant configuration
// ---------------------------------------------------------------------------

export interface AIEditingAssistantConfig {
  openaiApiKey?: string;
  model?: string;
  useOllama?: boolean;
  ollamaBaseUrl?: string;
  ollamaModel?: string;
  timeoutMs?: number;
}

// ---------------------------------------------------------------------------
// Implementation
// ---------------------------------------------------------------------------

/**
 * AIEditingAssistant — enhances Tutorputor content using LLM inference.
 *
 * All public methods are idempotent: the original object is never mutated; a
 * new object is always returned.  When the AI backend is unavailable the
 * original content is returned unchanged so the UI can continue working
 * offline.
 */
export class AIEditingAssistant {
  private openai: OpenAI | null = null;
  private model: string;
  private useOllama: boolean;
  private ollamaBaseUrl: string;
  private ollamaModel: string;
  private timeoutMs: number;

  constructor(config: AIEditingAssistantConfig = {}) {
    this.useOllama = config.useOllama ?? process.env["USE_OLLAMA"] === "true";
    this.ollamaBaseUrl =
      config.ollamaBaseUrl ||
      process.env["OLLAMA_BASE_URL"] ||
      "http://localhost:11434";
    this.ollamaModel =
      config.ollamaModel || process.env["OLLAMA_MODEL"] || "mistral";
    this.model = config.model || "gpt-4o-mini";
    this.timeoutMs = config.timeoutMs ?? 30_000;

    const apiKey = config.openaiApiKey || process.env["OPENAI_API_KEY"];
    if (!this.useOllama && apiKey) {
      this.openai = new OpenAI({ apiKey });
    }
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Analyse an animation and suggest improved keyframe timing and easing curves.
   *
   * The LLM receives a JSON summary of layers + keyframe positions and returns
   * a patch object with per-layer suggestions that are merged back into the
   * config.
   */
  async improveAnimation(animation: AnimationConfig): Promise<AnimationConfig> {
    const summary = this.buildAnimationSummary(animation);
    const prompt = `You are an expert educational animation designer.
Analyse the following animation configuration and suggest improvements to keyframe
timing, easing functions, and layer ordering to make the animation clearer for
learners. Return a valid JSON object matching this schema:
{
  "layers": [
    {
      "id": "<layer id>",
      "easing": "<linear|ease-in|ease-out|ease-in-out|bounce|elastic>",
      "keyframes": [
        { "id": "<keyframe id>", "timeMs": <number>, "properties": { "<key>": <value> } }
      ]
    }
  ],
  "durationMs": <number>
}
Only include layers/keyframes you want to change.

Animation:
${summary}`;

    const raw = await this.complete(prompt, "animation improvement");
    return this.mergeAnimationPatch(animation, raw);
  }

  /**
   * Suggest better parameter ranges, additional entities, or goal definitions
   * for a physics simulation to make it more educationally effective.
   */
  async optimizeSimulation(
    simulation: SimulationManifest,
  ): Promise<SimulationManifest> {
    const summary = JSON.stringify(
      {
        name: simulation.name,
        description: simulation.description,
        domain: simulation.domain,
        physics: simulation.physics,
        entityCount: simulation.entities.length,
        entities: simulation.entities.map((e) => ({
          id: e.id,
          name: e.name,
          mass: e.mass,
          radius: e.radius,
          dynamic: e.dynamic,
          velocity: e.velocity,
        })),
        goals: simulation.goals.map((g) => ({
          id: g.id,
          type: g.type,
          description: g.description,
        })),
      },
      null,
      2,
    );

    const prompt = `You are an expert educational simulation designer.
Analyse the following simulation and suggest improvements that make it a better
learning tool. You may suggest updated physics parameters, modified entity masses
or velocities, and improved goal descriptions. Return a valid JSON patch matching:
{
  "physics": { "gravityY": <number>, "friction": <number>, "restitution": <number>, "timeScale": <number> },
  "entities": [{ "id": "<id>", "mass": <number>, "radius": <number>, "velocity": { "x": <number>, "y": <number> } }],
  "goals": [{ "id": "<id>", "description": "<improved text>" }]
}
Only include fields you want to change. Omit fields that are already optimal.

Simulation:
${summary}`;

    const raw = await this.complete(prompt, "simulation optimization");
    return this.mergeSimulationPatch(simulation, raw);
  }

  /**
   * Enhance a list of content examples with clearer explanations and stronger
   * real-world connections.
   */
  async enhanceExamples(
    examples: ContentExample[],
  ): Promise<ContentExample[]> {
    // Process concurrently (max 3 in parallel to respect rate limits)
    const chunks = this.chunkArray(examples, 3);
    const results: ContentExample[] = [];
    for (const chunk of chunks) {
      const enhanced = await Promise.all(
        chunk.map((ex) => this.enhanceExample(ex)),
      );
      results.push(...enhanced);
    }
    return results;
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private async enhanceExample(example: ContentExample): Promise<ContentExample> {
    const prompt = `You are an expert curriculum designer.
Improve the following educational example to be clearer, more engaging, and to
highlight real-world applications. Return a JSON object with these fields:
{
  "id": "${example.id}",
  "title": "<improved title — be specific>",
  "body": "<improved body — use concrete language, avoid jargon>",
  "realWorldConnection": "<one-sentence real-world link>"
}
Keep subject, context unchanged.

Example:
${JSON.stringify(example, null, 2)}`;

    const raw = await this.complete(prompt, "example enhancement");
    try {
      const patch = JSON.parse(raw) as Partial<ContentExample>;
      return {
        ...example,
        title: patch.title || example.title,
        body: patch.body || example.body,
        realWorldConnection:
          patch.realWorldConnection || example.realWorldConnection,
      };
    } catch {
      return example; // Graceful degradation — return original on parse error
    }
  }

  private buildAnimationSummary(animation: AnimationConfig): string {
    return JSON.stringify(
      {
        name: animation.name,
        durationMs: animation.durationMs,
        fps: animation.fps,
        layers: animation.layers.map((l) => ({
          id: l.id,
          name: l.name,
          type: l.type,
          easing: l.easing,
          keyframes: l.keyframes.map((k) => ({
            id: k.id,
            timeMs: k.timeMs,
            properties: k.properties,
          })),
        })),
      },
      null,
      2,
    );
  }

  private mergeAnimationPatch(
    original: AnimationConfig,
    raw: string,
  ): AnimationConfig {
    try {
      const patch = JSON.parse(raw) as {
        durationMs?: number;
        layers?: Array<{
          id: string;
          easing?: EasingFunction;
          keyframes?: Keyframe[];
        }>;
      };

      const patchByLayerId = new Map(
        (patch.layers ?? []).map((l) => [l.id, l]),
      );

      return {
        ...original,
        durationMs: patch.durationMs ?? original.durationMs,
        layers: original.layers.map((layer) => {
          const p = patchByLayerId.get(layer.id);
          if (!p) return layer;

          const patchKeyframes = new Map(
            (p.keyframes ?? []).map((k) => [k.id, k]),
          );

          return {
            ...layer,
            easing: p.easing ?? layer.easing,
            keyframes: layer.keyframes.map((kf) => {
              const kfp = patchKeyframes.get(kf.id);
              return kfp ? { ...kf, ...kfp } : kf;
            }),
          };
        }),
      };
    } catch {
      return original;
    }
  }

  private mergeSimulationPatch(
    original: SimulationManifest,
    raw: string,
  ): SimulationManifest {
    try {
      const patch = JSON.parse(raw) as {
        physics?: Partial<PhysicsParameters>;
        entities?: Array<Partial<SimulationEntity> & { id: string }>;
        goals?: Array<Partial<SimulationGoal> & { id: string }>;
      };

      const entityPatchById = new Map(
        (patch.entities ?? []).map((e) => [e.id, e]),
      );
      const goalPatchById = new Map(
        (patch.goals ?? []).map((g) => [g.id, g]),
      );

      return {
        ...original,
        physics: { ...original.physics, ...(patch.physics ?? {}) },
        entities: original.entities.map((e) => {
          const ep = entityPatchById.get(e.id);
          return ep ? { ...e, ...ep } : e;
        }),
        goals: original.goals.map((g) => {
          const gp = goalPatchById.get(g.id);
          return gp ? { ...g, ...gp } : g;
        }),
      };
    } catch {
      return original;
    }
  }

  /** Call the configured LLM; returns raw response text. */
  private async complete(prompt: string, taskName: string): Promise<string> {
    if (this.useOllama) {
      return this.completeWithOllama(prompt, taskName);
    }
    if (this.openai) {
      return this.completeWithOpenAI(prompt, taskName);
    }
    // No backend configured — return empty JSON object for safe degradation
    console.warn(
      `[AIEditingAssistant] No LLM backend configured for task "${taskName}". ` +
      "Set OPENAI_API_KEY or USE_OLLAMA=true.",
    );
    return "{}";
  }

  private async completeWithOpenAI(
    prompt: string,
    taskName: string,
  ): Promise<string> {
    try {
      const completion = await this.openai!.chat.completions.create(
        {
          model: this.model,
          messages: [
            {
              role: "system",
              content:
                "You are an expert educational content designer. Always respond " +
                "with valid JSON matching the schema provided.",
            },
            { role: "user", content: prompt },
          ],
          temperature: 0.4,
          max_tokens: 2000,
          response_format: { type: "json_object" },
        },
      );
      return completion.choices[0]?.message?.content ?? "{}";
    } catch (error) {
      console.error(
        `[AIEditingAssistant] OpenAI call failed for "${taskName}":`,
        error,
      );
      return "{}";
    }
  }

  private async completeWithOllama(
    prompt: string,
    taskName: string,
  ): Promise<string> {
    const controller = new AbortController();
    const id = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await fetch(
        `${this.ollamaBaseUrl}/api/generate`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          signal: controller.signal,
          body: JSON.stringify({
            model: this.ollamaModel,
            prompt,
            stream: false,
            format: "json",
          }),
        },
      );
      if (!response.ok) {
        throw new Error(`Ollama HTTP ${response.status}`);
      }
      const data = (await response.json()) as { response?: string };
      return data.response ?? "{}";
    } catch (error) {
      console.error(
        `[AIEditingAssistant] Ollama call failed for "${taskName}":`,
        error,
      );
      return "{}";
    } finally {
      clearTimeout(id);
    }
  }

  private chunkArray<T>(arr: T[], size: number): T[][] {
    const chunks: T[][] = [];
    for (let i = 0; i < arr.length; i += size) {
      chunks.push(arr.slice(i, i + size));
    }
    return chunks;
  }
}

/**
 * Factory function — creates a singleton-friendly instance from env vars.
 */
export function createAIEditingAssistant(
  config?: AIEditingAssistantConfig,
): AIEditingAssistant {
  return new AIEditingAssistant(config);
}
