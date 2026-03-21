/**
 * Content Template system for Tutorputor Content Explorer.
 *
 * Templates are versioned blueprints that let authors quickly scaffold new
 * content items for a given domain, grade level, and content type.  They are
 * stored in the browser's localStorage with server-side persistence via the
 * tutorputor API.
 */

import type {
  ContentType,
  DifficultyLevel,
  GenerationRequest,
} from "./content";

// ---------------------------------------------------------------------------
// Core domain types
// ---------------------------------------------------------------------------

export interface AnimationTemplate {
  durationMs: number;
  fps: number;
  layerCount: number;
  style: "minimal" | "rich" | "diagram" | "narrative";
  notes?: string;
}

export interface SimulationTemplate {
  domain: string;
  entityCount: number;
  physicsPreset: "low-gravity" | "earth" | "water" | "vacuum";
  goalTypes: Array<
    "reach_position" | "avoid_zone" | "survive_duration" | "custom"
  >;
  notes?: string;
}

export interface ExampleTemplate {
  structure: "analogy" | "step-by-step" | "case-study" | "compare-contrast";
  includeRealWorldConnection: boolean;
  minWordCount: number;
  maxWordCount: number;
}

export interface TemplateMetadata {
  createdAt: string;  // ISO 8601
  updatedAt: string;
  createdBy: string;
  usageCount: number;
  tags: string[];
  /** Semantic version of this template – e.g. "1.0.0" */
  version: string;
  /** Whether this template is available to all workspace members. */
  isPublic: boolean;
  /** Average quality score of content generated from this template (0-100). */
  avgQualityScore: number | null;
}

/**
 * ContentTemplate — a stable, versioned blueprint for content generation.
 *
 * @doc.type interface
 * @doc.purpose Versioned, reusable scaffold for content packages
 * @doc.layer product
 * @doc.pattern ValueObject
 */
export interface ContentTemplate {
  id: string;
  name: string;
  description: string;
  /** Content domain, e.g. "Physics", "Mathematics", "Biology". */
  domain: string;
  gradeLevel: string;
  contentType: ContentType;
  difficulty: DifficultyLevel;
  /** Default learning objectives to pre-fill the generation form. */
  defaultObjectives: string[];
  /** Optional structured defaults for each sub-type. */
  animationTemplate?: AnimationTemplate;
  simulationTemplate?: SimulationTemplate;
  exampleTemplates?: ExampleTemplate[];
  metadata: TemplateMetadata;
}

/** Partial type for in-editor draft that may not yet have all fields. */
export type TemplateFormValues = Omit<
  ContentTemplate,
  "id" | "metadata"
> & { id?: string };

// ---------------------------------------------------------------------------
// Template library — built-in canonical templates
// ---------------------------------------------------------------------------

/** Factory that creates a `TemplateMetadata` with sensible defaults. */
function meta(createdBy = "system"): TemplateMetadata {
  const now = new Date().toISOString();
  return {
    createdAt: now,
    updatedAt: now,
    createdBy,
    usageCount: 0,
    tags: [],
    version: "1.0.0",
    isPublic: true,
    avgQualityScore: null,
  };
}

/**
 * Built-in template library.  These templates ship with the application and
 * cannot be deleted, only forked.
 */
export const BUILT_IN_TEMPLATES: ContentTemplate[] = [
  {
    id: "tpl-physics-kinematics",
    name: "Kinematics Lesson",
    description:
      "A standard lesson template for introductory kinematics covering " +
      "position, velocity, and acceleration.",
    domain: "Physics",
    gradeLevel: "Grade 9-10",
    contentType: "lesson",
    difficulty: "intermediate",
    defaultObjectives: [
      "Define position, displacement, velocity, and acceleration",
      "Apply kinematic equations to solve motion problems",
      "Interpret position-time and velocity-time graphs",
    ],
    animationTemplate: {
      durationMs: 6000,
      fps: 60,
      layerCount: 3,
      style: "diagram",
      notes: "Show ball in projectile flight; use two layers for X and Y motion",
    },
    simulationTemplate: {
      domain: "Physics",
      entityCount: 2,
      physicsPreset: "earth",
      goalTypes: ["reach_position", "survive_duration"],
      notes:
        "Place a moving ball + static target. Goal: guide ball to target using initial velocity controls.",
    },
    exampleTemplates: [
      {
        structure: "step-by-step",
        includeRealWorldConnection: true,
        minWordCount: 150,
        maxWordCount: 300,
      },
    ],
    metadata: { ...meta(), tags: ["physics", "kinematics", "motion"], usageCount: 42 },
  },
  {
    id: "tpl-math-algebra-intro",
    name: "Introductory Algebra",
    description:
      "Template for algebra lessons covering variables, expressions, and " +
      "simple equation solving.",
    domain: "Mathematics",
    gradeLevel: "Grade 7-8",
    contentType: "lesson",
    difficulty: "beginner",
    defaultObjectives: [
      "Understand the concept of a variable",
      "Simplify algebraic expressions using order of operations",
      "Solve one-step and two-step linear equations",
    ],
    animationTemplate: {
      durationMs: 4000,
      fps: 30,
      layerCount: 2,
      style: "narrative",
    },
    exampleTemplates: [
      {
        structure: "analogy",
        includeRealWorldConnection: true,
        minWordCount: 100,
        maxWordCount: 200,
      },
    ],
    metadata: { ...meta(), tags: ["mathematics", "algebra", "beginner"], usageCount: 87 },
  },
  {
    id: "tpl-bio-cell-biology",
    name: "Cell Biology Overview",
    description:
      "Comprehensive template for cell biology lessons at middle- and high-school level.",
    domain: "Biology",
    gradeLevel: "Grade 8-11",
    contentType: "lesson",
    difficulty: "intermediate",
    defaultObjectives: [
      "Identify the key organelles of eukaryotic cells and their functions",
      "Distinguish between prokaryotic and eukaryotic cells",
      "Explain cell membrane structure and selective permeability",
    ],
    animationTemplate: {
      durationMs: 8000,
      fps: 60,
      layerCount: 5,
      style: "rich",
      notes:
        "Animate organelle labels appearing sequentially; zoom into mitochondria",
    },
    exampleTemplates: [
      {
        structure: "compare-contrast",
        includeRealWorldConnection: false,
        minWordCount: 200,
        maxWordCount: 400,
      },
    ],
    metadata: { ...meta(), tags: ["biology", "cell", "organelles"], usageCount: 63 },
  },
  {
    id: "tpl-physics-simulation-gravity",
    name: "Gravitational Simulation",
    description:
      "Physics simulation template focusing on gravitational effects " +
      "and orbital mechanics at introductory level.",
    domain: "Physics",
    gradeLevel: "Grade 10-12",
    contentType: "simulation",
    difficulty: "advanced",
    defaultObjectives: [
      "Demonstrate Newton's Law of Universal Gravitation",
      "Observe how mass affects gravitational attraction",
      "Model circular orbital motion",
    ],
    simulationTemplate: {
      domain: "Astrophysics",
      entityCount: 3,
      physicsPreset: "vacuum",
      goalTypes: ["survive_duration", "reach_position"],
      notes:
        "Planet A (large mass, static) + Planet B (medium mass, static) + Satellite (small, dynamic). " +
        "Goal: Satellite completes one orbit around Planet A.",
    },
    metadata: {
      ...meta(),
      tags: ["physics", "gravity", "simulation", "orbital"],
      usageCount: 29,
    },
  },
  {
    id: "tpl-quiz-science-general",
    name: "General Science Quiz",
    description:
      "Generic quiz template adaptable to any science domain.",
    domain: "Science",
    gradeLevel: "Grade 6-12",
    contentType: "quiz",
    difficulty: "intermediate",
    defaultObjectives: [
      "Recall key vocabulary from the current unit",
      "Apply core concepts to solve novel problems",
      "Demonstrate understanding through multiple-choice and short-answer formats",
    ],
    exampleTemplates: [
      {
        structure: "step-by-step",
        includeRealWorldConnection: false,
        minWordCount: 50,
        maxWordCount: 100,
      },
    ],
    metadata: { ...meta(), tags: ["quiz", "science", "assessment"], usageCount: 115 },
  },
];

// ---------------------------------------------------------------------------
// Template → GenerationRequest adapter
// ---------------------------------------------------------------------------

/**
 * Convert a `ContentTemplate` into a partial `GenerationRequest` that can be
 * used to pre-fill the generation form.
 */
export function templateToGenerationRequest(
  template: ContentTemplate,
  topic: string,
): Partial<GenerationRequest> {
  return {
    type: template.contentType,
    subject: template.domain,
    topic,
    gradeLevel: template.gradeLevel,
    difficulty: template.difficulty,
    learningObjectives: [...template.defaultObjectives],
  };
}

// ---------------------------------------------------------------------------
// Local template store (localStorage backed, singleton)
// ---------------------------------------------------------------------------

const STORAGE_KEY = "content-explorer:user-templates";

/**
 * TemplateStore — client-side template CRUD backed by localStorage.
 *
 * In production this would call the tutorputor API; the localStorage layer
 * provides instant reads even when the backend is unavailable.
 */
export class TemplateStore {
  private static instance: TemplateStore | null = null;
  private userTemplates: ContentTemplate[] = [];

  private constructor() {
    this.load();
  }

  static getInstance(): TemplateStore {
    if (!TemplateStore.instance) {
      TemplateStore.instance = new TemplateStore();
    }
    return TemplateStore.instance;
  }

  /** All templates (built-in + user-created), sorted by usage desc. */
  getAll(): ContentTemplate[] {
    return [...BUILT_IN_TEMPLATES, ...this.userTemplates].sort(
      (a, b) => b.metadata.usageCount - a.metadata.usageCount,
    );
  }

  /** Filter by domain and/or contentType. */
  filter(opts: {
    domain?: string;
    contentType?: ContentType;
    gradeLevel?: string;
  }): ContentTemplate[] {
    return this.getAll().filter((t) => {
      if (opts.domain && t.domain !== opts.domain) return false;
      if (opts.contentType && t.contentType !== opts.contentType) return false;
      if (opts.gradeLevel && t.gradeLevel !== opts.gradeLevel) return false;
      return true;
    });
  }

  getById(id: string): ContentTemplate | undefined {
    return this.getAll().find((t) => t.id === id);
  }

  /** Save a user-defined template. Generates an id if not provided. */
  save(values: TemplateFormValues): ContentTemplate {
    const now = new Date().toISOString();
    const existing = values.id
      ? this.userTemplates.find((t) => t.id === values.id)
      : undefined;

    const template: ContentTemplate = {
      ...values,
      id: values.id ?? `tpl-user-${crypto.randomUUID()}`,
      metadata: existing
        ? { ...existing.metadata, updatedAt: now, version: this.bumpVersion(existing.metadata.version) }
        : {
            createdAt: now,
            updatedAt: now,
            createdBy: "current-user",
            usageCount: 0,
            tags: [],
            version: "1.0.0",
            isPublic: false,
            avgQualityScore: null,
          },
    };

    if (existing) {
      this.userTemplates = this.userTemplates.map((t) =>
        t.id === template.id ? template : t,
      );
    } else {
      this.userTemplates.push(template);
    }

    this.persist();
    return template;
  }

  delete(id: string): void {
    // Only user templates can be deleted
    this.userTemplates = this.userTemplates.filter((t) => t.id !== id);
    this.persist();
  }

  recordUsage(id: string): void {
    const tpl = this.userTemplates.find((t) => t.id === id);
    if (tpl) {
      tpl.metadata.usageCount += 1;
      tpl.metadata.updatedAt = new Date().toISOString();
      this.persist();
    }
    // For built-in templates usage is not persisted (read-only)
  }

  private load(): void {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      this.userTemplates = raw ? (JSON.parse(raw) as ContentTemplate[]) : [];
    } catch {
      this.userTemplates = [];
    }
  }

  private persist(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.userTemplates));
    } catch {
      // Storage quota exceeded — silently ignore; data lives in memory
    }
  }

  private bumpVersion(v: string): string {
    const parts = v.split(".").map(Number);
    parts[2] = (parts[2] ?? 0) + 1;
    return parts.join(".");
  }
}
