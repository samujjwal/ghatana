/**
 * CMS Module Editor Constants
 * 
 * Shared constants for the CMS Module Editor
 * 
 * @doc.type constants
 * @doc.purpose Constants for module editor
 * @doc.layer product
 * @doc.pattern Constants
 */

import type { ContentBlockType, DraftState, BlockTypeOption } from './types';

/** Block type options */
export const BLOCK_TYPE_OPTIONS: BlockTypeOption[] = [
    { value: "text", label: "Text", icon: "📝" },
    { value: "rich_text", label: "Rich Text", icon: "📄" },
    { value: "video", label: "Video", icon: "🎥" },
    { value: "image", label: "Image", icon: "🖼️" },
    { value: "simulation", label: "Simulation", icon: "🔬" },
    { value: "exercise", label: "Exercise", icon: "💪" },
    { value: "assessment_item_ref", label: "Assessment", icon: "📋" },
    { value: "ai_tutor_prompt", label: "AI Tutor", icon: "🤖" },
];

/** Difficulty options */
export const DIFFICULTY_OPTIONS = ["beginner", "intermediate", "advanced", "expert"] as const;

/** Domain options aligned with Prisma ModuleDomain enum */
export const DOMAIN_OPTIONS = [
    "math",
    "science",
    "tech",
    "engineering",
    "medicine",
    "health",
    "business",
    "management",
    "economics",
    "computer_science",
    "interdisciplinary",
] as const;

/** Initial draft state */
export const INITIAL_DRAFT: DraftState = {
    slug: "",
    title: "",
    description: "",
    domain: "computer_science",
    difficulty: "beginner",
    estimatedTimeMinutes: 30,
    tags: [],
    learningObjectives: [],
    contentBlocks: [],
};
