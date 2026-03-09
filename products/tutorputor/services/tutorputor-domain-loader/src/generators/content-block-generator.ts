import { createLogger } from '../utils/logger.js';
const logger = createLogger('content-block-generator');

/**
 * Content Block Generator
 *
 * Generates comprehensive content blocks for modules based on domain concepts.
 * Creates rich_text, simulation, ai_tutor_prompt, and exercise blocks.
 *
 * @doc.type module
 * @doc.purpose Generate content blocks from domain concepts
 * @doc.layer product
 * @doc.pattern Generator
 */

import type { TutorPrismaClient } from "../prisma-utils.js";
import type { DomainConcept } from "@ghatana/tutorputor-contracts/v1/curriculum/types";

// =============================================================================
// Types
// =============================================================================

/**
 * Options for content block generation.
 */
export interface ContentBlockGeneratorOptions {
    /** Include simulation block (even as placeholder) */
    includeSimulation?: boolean;

    /** Include AI tutor prompt block */
    includeAiTutor?: boolean;

    /** Include exercise block */
    includeExercise?: boolean;

    /** Include competencies block */
    includeCompetencies?: boolean;

    /** Verbose logging */
    verbose?: boolean;
}

/**
 * Result of content block generation.
 */
export interface ContentBlockGeneratorResult {
    /** Number of blocks created */
    blocksCreated: number;

    /** Block types created */
    blockTypes: string[];

    /** Module ID */
    moduleId: string;

    /** Warnings */
    warnings: string[];
}

// =============================================================================
// Main Generator
// =============================================================================

/**
 * Generate comprehensive content blocks for a module based on a domain concept.
 *
 * Creates the following blocks in order:
 * 1. Introduction (rich_text) - Concept name and description
 * 2. Learning Objectives (rich_text) - List of learning objectives
 * 3. Simulation (simulation) - Interactive visualization placeholder
 * 4. Key Concepts (rich_text) - Keywords and competencies
 * 5. AI Tutor (ai_tutor_prompt) - Contextual AI assistant
 * 6. Exercises (exercise) - Practice questions based on objectives
 */
export async function generateContentBlocks(
    prisma: TutorPrismaClient,
    moduleId: string,
    concept: DomainConcept,
    options: ContentBlockGeneratorOptions = {}
): Promise<ContentBlockGeneratorResult> {
    const {
        includeSimulation = true,
        includeAiTutor = true,
        includeExercise = true,
        includeCompetencies = true,
        verbose = false,
    } = options;

    const blockTypes: string[] = [];
    const warnings: string[] = [];
    let orderIndex = 0;

    // Remove existing content blocks for this module
    await prisma.moduleContentBlock.deleteMany({ where: { moduleId } });

    // Block 1: Introduction (rich_text)
    await createIntroductionBlock(prisma, moduleId, concept, orderIndex++);
    blockTypes.push("rich_text");

    // Block 2: Learning Objectives (rich_text)
    if (concept.pedagogicalMetadata.learningObjectives.length > 0) {
        await createLearningObjectivesBlock(prisma, moduleId, concept, orderIndex++);
        blockTypes.push("rich_text");
    }

    // Block 3: Simulation (simulation)
    if (includeSimulation && concept.simulationMetadata.simulationType) {
        await createSimulationBlock(prisma, moduleId, concept, orderIndex++);
        blockTypes.push("simulation");
    }

    // Block 4: Key Concepts (rich_text)
    if (includeCompetencies && (concept.keywords.length > 0 || concept.pedagogicalMetadata.competencies.length > 0)) {
        await createKeyConceptsBlock(prisma, moduleId, concept, orderIndex++);
        blockTypes.push("rich_text");
    }

    // Block 5: AI Tutor (ai_tutor_prompt)
    if (includeAiTutor) {
        await createAiTutorBlock(prisma, moduleId, concept, orderIndex++);
        blockTypes.push("ai_tutor_prompt");
    }

    // Block 6: Exercises (exercise)
    if (includeExercise && concept.pedagogicalMetadata.learningObjectives.length > 0) {
        await createExerciseBlock(prisma, moduleId, concept, orderIndex++);
        blockTypes.push("exercise");
    }

    if (verbose) {
        logger.info({}, `Created ${orderIndex} content blocks for module ${moduleId}`);
    }

    return {
        blocksCreated: orderIndex,
        blockTypes,
        moduleId,
        warnings,
    };
}

// =============================================================================
// Block Creators
// =============================================================================

/**
 * Create introduction block with concept name and description.
 */
async function createIntroductionBlock(
    prisma: TutorPrismaClient,
    moduleId: string,
    concept: DomainConcept,
    orderIndex: number
): Promise<void> {
    const levelBadge = getLevelBadge(concept.level);
    const domainBadge = getDomainBadge(concept.domain);

    const content = `
<div class="concept-header">
  <h1>${escapeHtml(concept.name)}</h1>
  <div class="badges">
    ${domainBadge}
    ${levelBadge}
    <span class="time-badge">⏱️ ${concept.learningObjectMetadata.typicalLearningTimeMinutes} min</span>
  </div>
</div>

<div class="concept-description">
  <p>${escapeHtml(concept.description)}</p>
</div>

<div class="prerequisite-note">
  ${concept.prerequisites.length > 0
            ? `<p><strong>Prerequisites:</strong> This module builds on ${concept.prerequisites.length} prior concept(s).</p>`
            : '<p>No prerequisites required for this module.</p>'
        }
</div>
`.trim();

    await prisma.moduleContentBlock.create({
        data: {
            moduleId,
            orderIndex,
            blockType: "rich_text",
            payload: {
                content,
                format: "html",
                metadata: {
                    section: "introduction",
                    conceptId: concept.id,
                },
            },
        },
    });
}

/**
 * Create learning objectives block.
 */
async function createLearningObjectivesBlock(
    prisma: TutorPrismaClient,
    moduleId: string,
    concept: DomainConcept,
    orderIndex: number
): Promise<void> {
    const objectives = concept.pedagogicalMetadata.learningObjectives;

    const objectiveItems = objectives
        .map((obj: string, i: number) => {
            const taxonomyLevel = inferTaxonomyLevel(obj);
            const taxonomyIcon = getTaxonomyIcon(taxonomyLevel);
            return `<li data-taxonomy="${taxonomyLevel}">${taxonomyIcon} ${escapeHtml(obj)}</li>`;
        })
        .join("\n");

    const content = `
<div class="learning-objectives">
  <h2>🎯 Learning Objectives</h2>
  <p>By the end of this module, you will be able to:</p>
  <ul class="objectives-list">
    ${objectiveItems}
  </ul>
</div>
`.trim();

    await prisma.moduleContentBlock.create({
        data: {
            moduleId,
            orderIndex,
            blockType: "rich_text",
            payload: {
                content,
                format: "html",
                metadata: {
                    section: "learning_objectives",
                    objectiveCount: objectives.length,
                },
            },
        },
    });
}

/**
 * Create simulation block (placeholder until manifest is generated).
 */
async function createSimulationBlock(
    prisma: TutorPrismaClient,
    moduleId: string,
    concept: DomainConcept,
    orderIndex: number
): Promise<void> {
    const simMeta = concept.simulationMetadata;

    await prisma.moduleContentBlock.create({
        data: {
            moduleId,
            orderIndex,
            blockType: "simulation",
            payload: {
                manifestId: null,
                inlineManifest: null,
                placeholder: true,
                simulationType: simMeta.simulationType,
                purpose: simMeta.purpose,
                interactivity: simMeta.recommendedInteractivity,
                estimatedTimeMinutes: simMeta.estimatedTimeMinutes,
                display: {
                    showControls: true,
                    showTimeline: true,
                    showNarration: true,
                    aspectRatio: "16:9",
                },
                tutorContext: {
                    enabled: true,
                    contextPrompt: `This simulation demonstrates ${concept.name}. ${simMeta.purpose}`,
                },
                metadata: {
                    conceptId: concept.id,
                    domain: concept.domain,
                    awaitingManifest: true,
                },
            },
        },
    });
}

/**
 * Create key concepts block with keywords and competencies.
 */
async function createKeyConceptsBlock(
    prisma: TutorPrismaClient,
    moduleId: string,
    concept: DomainConcept,
    orderIndex: number
): Promise<void> {
    const keywords = concept.keywords;
    const competencies = concept.pedagogicalMetadata.competencies;

    const keywordTags = keywords
        .map((kw: string) => `<span class="keyword-tag">${escapeHtml(kw)}</span>`)
        .join(" ");

    const competencyItems = competencies
        .map((comp: string) => `<li>✓ ${escapeHtml(comp)}</li>`)
        .join("\n");

    const content = `
<div class="key-concepts">
  <h2>📚 Key Concepts</h2>
  
  <div class="keywords-section">
    <h3>Keywords</h3>
    <div class="keyword-tags">
      ${keywordTags || '<span class="empty-note">No keywords specified</span>'}
    </div>
  </div>
  
  ${competencies.length > 0 ? `
  <div class="competencies-section">
    <h3>Competencies You'll Develop</h3>
    <ul class="competencies-list">
      ${competencyItems}
    </ul>
  </div>
  ` : ''}
</div>
`.trim();

    await prisma.moduleContentBlock.create({
        data: {
            moduleId,
            orderIndex,
            blockType: "rich_text",
            payload: {
                content,
                format: "html",
                metadata: {
                    section: "key_concepts",
                    keywordCount: keywords.length,
                    competencyCount: competencies.length,
                },
            },
        },
    });
}

/**
 * Create AI tutor block with contextual prompts.
 */
async function createAiTutorBlock(
    prisma: TutorPrismaClient,
    moduleId: string,
    concept: DomainConcept,
    orderIndex: number
): Promise<void> {
    const contextPrompt = buildTutorContextPrompt(concept);
    const suggestedQuestions = generateSuggestedQuestions(concept);

    await prisma.moduleContentBlock.create({
        data: {
            moduleId,
            orderIndex,
            blockType: "ai_tutor_prompt",
            payload: {
                contextPrompt,
                suggestedQuestions,
                persona: "helpful_tutor",
                difficulty: concept.learningObjectMetadata.difficulty,
                scaffoldingLevel: concept.pedagogicalMetadata.scaffoldingLevel,
                metadata: {
                    conceptId: concept.id,
                    domain: concept.domain,
                    level: concept.level,
                },
            },
        },
    });
}

/**
 * Create exercise block with practice questions.
 */
async function createExerciseBlock(
    prisma: TutorPrismaClient,
    moduleId: string,
    concept: DomainConcept,
    orderIndex: number
): Promise<void> {
    const exercises = generateExercises(concept);

    // Build payload as JSON-compatible structure
    const payload = {
        title: `Practice: ${concept.name}`,
        instructions: "Complete the following exercises to test your understanding.",
        exercises: exercises.map((ex) => ({
            id: ex.id,
            type: ex.type,
            prompt: ex.prompt,
            taxonomyLevel: ex.taxonomyLevel,
            points: ex.points,
            hints: ex.hints,
            rubric: ex.rubric,
        })),
        gradingMode: "self_assessment",
        showHints: true,
        showSolutions: true,
        metadata: {
            conceptId: concept.id as string,
            difficulty: concept.learningObjectMetadata.difficulty as string,
            objectiveCount: concept.pedagogicalMetadata.learningObjectives.length,
        },
    };

    await prisma.moduleContentBlock.create({
        data: {
            moduleId,
            orderIndex,
            blockType: "exercise",
            payload,
        },
    });
}

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Build context prompt for AI tutor.
 */
function buildTutorContextPrompt(concept: DomainConcept): string {
    const parts = [
        `You are an expert tutor helping a student understand "${concept.name}".`,
        `Domain: ${concept.domain}.`,
        `Level: ${concept.level}.`,
        `\n\nConcept Description:\n${concept.description}`,
        `\n\nKey Topics: ${concept.keywords.join(", ")}.`,
    ];

    if (concept.pedagogicalMetadata.learningObjectives.length > 0) {
        parts.push(`\n\nLearning Objectives:\n- ${concept.pedagogicalMetadata.learningObjectives.join("\n- ")}`);
    }

    if (concept.pedagogicalMetadata.accessibilityNotes) {
        parts.push(`\n\nAccessibility Notes: ${concept.pedagogicalMetadata.accessibilityNotes}`);
    }

    parts.push("\n\nProvide clear, patient explanations. Use analogies and examples when helpful. Encourage the student to think critically and ask questions.");

    return parts.join(" ");
}

/**
 * Generate suggested questions based on concept.
 */
function generateSuggestedQuestions(concept: DomainConcept): string[] {
    const questions: string[] = [
        `What is the main idea behind ${concept.name}?`,
        `Can you explain how ${concept.name} works in simpler terms?`,
        `What are some real-world applications of ${concept.name}?`,
        `What are common misconceptions about this topic?`,
    ];

    // Add objective-based questions
    for (const objective of concept.pedagogicalMetadata.learningObjectives.slice(0, 2)) {
        questions.push(`Help me understand: ${objective}`);
    }

    // Add competency-based questions
    for (const competency of concept.pedagogicalMetadata.competencies.slice(0, 1)) {
        questions.push(`How can I develop this skill: ${competency}?`);
    }

    return questions.slice(0, 6); // Limit to 6 questions
}

/**
 * Generate exercise items based on learning objectives.
 */
function generateExercises(concept: DomainConcept): ExerciseItem[] {
    const exercises: ExerciseItem[] = [];

    for (let i = 0; i < concept.pedagogicalMetadata.learningObjectives.length; i++) {
        const objective = concept.pedagogicalMetadata.learningObjectives[i];
        const taxonomyLevel = inferTaxonomyLevel(objective);

        exercises.push({
            id: `ex-${i + 1}`,
            type: getExerciseType(taxonomyLevel),
            prompt: generateExercisePrompt(objective, concept),
            taxonomyLevel,
            points: getPointsForTaxonomy(taxonomyLevel),
            hints: [
                `Review the learning objective: "${objective}"`,
                `Think about how this relates to ${concept.keywords[0] || concept.name}`,
            ],
            rubric: `Demonstrates understanding of: ${objective}`,
        });
    }

    return exercises;
}

/**
 * Generate exercise prompt from learning objective.
 */
function generateExercisePrompt(objective: string, concept: DomainConcept): string {
    const taxonomyLevel = inferTaxonomyLevel(objective);

    switch (taxonomyLevel) {
        case "remember":
            return `Define or describe: ${extractKeyTerm(objective, concept)}`;
        case "understand":
            return `Explain in your own words: ${objective}`;
        case "apply":
            return `Given a scenario, demonstrate how to: ${objective}`;
        case "analyze":
            return `Compare and contrast: ${extractKeyTerm(objective, concept)} with related concepts`;
        case "evaluate":
            return `Critically assess: ${objective}`;
        case "create":
            return `Design or develop a solution that demonstrates: ${objective}`;
        default:
            return objective;
    }
}

/**
 * Extract key term from objective text.
 */
function extractKeyTerm(objective: string, concept: DomainConcept): string {
    // Try to find a matching keyword
    for (const keyword of concept.keywords) {
        if (objective.toLowerCase().includes(keyword.toLowerCase())) {
            return keyword;
        }
    }
    // Fall back to concept name
    return concept.name;
}

/**
 * Get exercise type based on taxonomy level.
 */
function getExerciseType(taxonomyLevel: string): string {
    switch (taxonomyLevel) {
        case "remember":
            return "short_answer";
        case "understand":
            return "explanation";
        case "apply":
            return "problem_solving";
        case "analyze":
            return "comparison";
        case "evaluate":
            return "critical_analysis";
        case "create":
            return "project";
        default:
            return "open_response";
    }
}

/**
 * Get points based on taxonomy level.
 */
function getPointsForTaxonomy(taxonomyLevel: string): number {
    switch (taxonomyLevel) {
        case "remember":
            return 5;
        case "understand":
            return 10;
        case "apply":
            return 15;
        case "analyze":
            return 20;
        case "evaluate":
            return 25;
        case "create":
            return 30;
        default:
            return 10;
    }
}

/**
 * Infer Bloom's taxonomy level from objective text.
 */
function inferTaxonomyLevel(objective: string): string {
    const lower = objective.toLowerCase();

    // Create level
    if (/design|create|develop|formulate|construct|build|generate|produce/.test(lower)) {
        return "create";
    }

    // Evaluate level
    if (/evaluate|assess|judge|critique|justify|defend|argue/.test(lower)) {
        return "evaluate";
    }

    // Analyze level
    if (/analyze|compare|contrast|differentiate|distinguish|examine|investigate/.test(lower)) {
        return "analyze";
    }

    // Apply level
    if (/apply|use|implement|solve|compute|calculate|demonstrate|execute/.test(lower)) {
        return "apply";
    }

    // Understand level
    if (/explain|describe|interpret|summarize|understand|classify|discuss/.test(lower)) {
        return "understand";
    }

    // Default to remember
    return "remember";
}

/**
 * Get badge HTML for curriculum level.
 */
function getLevelBadge(level: string): string {
    const colors: Record<string, string> = {
        FOUNDATIONAL: "#22c55e",
        INTERMEDIATE: "#3b82f6",
        ADVANCED: "#f59e0b",
        RESEARCH: "#8b5cf6",
    };
    const color = colors[level] || "#64748b";
    return `<span class="level-badge" style="background-color: ${color};">${level}</span>`;
}

/**
 * Get badge HTML for domain.
 */
function getDomainBadge(domain: string): string {
    const colors: Record<string, string> = {
        PHYSICS: "#3b82f6",
        CHEMISTRY: "#22c55e",
        BIOLOGY: "#10b981",
        MATHEMATICS: "#6366f1",
        CS_DISCRETE: "#f59e0b",
    };
    const color = colors[domain] || "#64748b";
    return `<span class="domain-badge" style="background-color: ${color};">${domain}</span>`;
}

/**
 * Get icon for taxonomy level.
 */
function getTaxonomyIcon(level: string): string {
    const icons: Record<string, string> = {
        remember: "📝",
        understand: "💡",
        apply: "🔧",
        analyze: "🔍",
        evaluate: "⚖️",
        create: "🎨",
    };
    return icons[level] || "📚";
}

/**
 * Escape HTML special characters.
 */
function escapeHtml(text: string): string {
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

// =============================================================================
// Types (Internal)
// =============================================================================

interface ExerciseItem {
    id: string;
    type: string;
    prompt: string;
    taxonomyLevel: string;
    points: number;
    hints: string[];
    rubric: string;
}
