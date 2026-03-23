/**
 * Module Editor Types
 * 
 * Shared types and interfaces for the CMS Module Editor
 * 
 * @doc.type types
 * @doc.purpose Type definitions for module editor
 * @doc.layer product
 * @doc.pattern Types
 */

/** Content block type options */
export type ContentBlockType =
    | "text"
    | "rich_text"
    | "video"
    | "image"
    | "simulation"
    | "exercise"
    | "assessment_item_ref"
    | "ai_tutor_prompt";

/** Learning objective taxonomy levels */
export type TaxonomyLevel = "REMEMBER" | "UNDERSTAND" | "APPLY" | "ANALYZE" | "EVALUATE" | "CREATE";

/** Learning objective interface */
export interface LearningObjective {
    label: string;
    taxonomyLevel: TaxonomyLevel;
}

/** Content block interface */
export interface ContentBlock {
    id: string;
    orderIndex: number;
    blockType: ContentBlockType;
    payload: unknown;
}

/** Module draft input for create */
export interface ModuleDraftInput {
    slug: string;
    title: string;
    description: string;
    domain: string;
    difficulty: string;
    estimatedTimeMinutes: number;
    tags: string[];
    learningObjectives: LearningObjective[];
    contentBlocks: ContentBlock[];
}

/** Module draft patch for update */
export interface ModuleDraftPatch {
    title?: string;
    description?: string;
    difficulty?: string;
    estimatedTimeMinutes?: number;
    tags?: string[];
    learningObjectives?: LearningObjective[];
    contentBlocks?: ContentBlock[];
}

/** Draft state interface */
export interface DraftState {
    slug: string;
    title: string;
    description: string;
    domain: string;
    difficulty: string;
    estimatedTimeMinutes: number;
    tags: string[];
    learningObjectives: LearningObjective[];
    contentBlocks: ContentBlock[];
}

/** Block type option */
export interface BlockTypeOption {
    value: ContentBlockType;
    label: string;
    icon: string;
}
