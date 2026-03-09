/**
 *
 */
export interface CanvasAcceptanceCriterion {
  /**
   * Unique identifier for the acceptance criterion, scoped to a story.
   * Example: AC-1.1-1 (story 1.1, first criterion).
   */
  id: string;
  /** Optional short title extracted from the Markdown bold heading, e.g. "Smooth zooming". */
  title?: string;
  /** Full text description of the criterion. */
  summary: string;
  /** Original Markdown line for traceability/debugging. */
  raw: string;
}

/**
 *
 */
export interface CanvasStoryTestReference {
  /**
   * Unique identifier for the test reference, scoped to a story.
   * Example: TEST-1.1-1 (story 1.1, first test reference).
   */
  id: string;
  /** Normalized label such as "Unit", "Integration", "E2E", "CI", etc. */
  type: string;
  /** Summary text describing what the test validates. */
  summary: string;
  /**
   * Targets extracted from inline code blocks, e.g. file paths or commands.
   * Preserved exactly as found in the source Markdown.
   */
  targets: string[];
  /** Original Markdown line for traceability/debugging. */
  raw: string;
}

/**
 *
 */
export interface CanvasFeatureStory {
  /** Hierarchical identifier from the Markdown heading, e.g. "1.1". */
  id: string;
  /**
   * URL-safe slug generated from the story title for linking/filtering.
   * Example: "viewport-management".
   */
  slug: string;
  /** Human-readable story title from the Markdown heading. */
  title: string;
  /** Order of the story within its category (0-based index). */
  order: number;
  /** Role-based story sentence, e.g. "As a designer I want ...". */
  narrative: string;
  /** Parent category identifier, e.g. "1". */
  categoryId: string;
  /** Parent category title for quick lookups. */
  categoryTitle: string;
  /** Parent category blueprint reference clause, if available. */
  blueprintReference?: string;
  /** Acceptance criteria parsed from the Markdown bullet list. */
  acceptanceCriteria: CanvasAcceptanceCriterion[];
  /** Test references parsed from the Markdown bullet list. */
  tests: CanvasStoryTestReference[];
  /** Original Markdown fragment for traceability/debugging. */
  raw: string[];
  /** Optional progress metadata captured from documentation. */
  progress?: CanvasFeatureStoryProgress;
}

/**
 *
 */
export interface CanvasFeatureStoryCategory {
  /** Numeric or alphanumeric identifier matching the Markdown section, e.g. "1". */
  id: string;
  /** Category title such as "Current Capabilities". */
  title: string;
  /** Reference back to the blueprint section if documented. */
  blueprintReference?: string;
  /** Order of the category within the document (0-based index). */
  order: number;
  /** Stories belonging to the category. */
  stories: CanvasFeatureStory[];
}

/**
 *
 */
export interface CanvasFeatureStoryProgress {
  /** High-level status label (e.g. Done, In Progress, Blocked). */
  status: string;
  /** Optional free-text summary describing recent updates. */
  summary?: string;
  /** Original Markdown line for traceability/debugging. */
  raw: string;
}
