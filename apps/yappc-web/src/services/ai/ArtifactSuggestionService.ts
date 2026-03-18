/**
 * AI Suggestion Service
 * 
 * Generic service for generating AI-powered suggestions across all artifacts.
 * Integrates with existing AI service and provides context-aware recommendations.
 * 
 * @doc.type service
 * @doc.purpose AI-powered artifact suggestions
 * @doc.layer product
 * @doc.pattern Service
 */

import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';
import type { LifecyclePhase } from '@/types/lifecycle';

export interface ArtifactSuggestion {
    id: string;
    kind: LifecycleArtifactKind;
    title: string;
    summary: string;
    reasoning: string;
    confidence: number; // 0-100
    suggestedPayload: Record<string, unknown>;
}

export interface SuggestionContext {
    projectId: string;
    currentPhase: LifecyclePhase;
    existingArtifacts: Array<{ kind: LifecycleArtifactKind; payload: Record<string, unknown> }>;
    projectDescription?: string;
}

/**
 * Generate AI suggestions for missing artifacts
 */
export async function generateArtifactSuggestions(
    context: SuggestionContext,
    targetKinds?: LifecycleArtifactKind[]
): Promise<ArtifactSuggestion[]> {
    // Build context for AI
    const prompt = buildSuggestionPrompt(context, targetKinds);

    // Call AI service (reusing existing AI integration)
    const response = await fetch('/api/ai/suggest-artifacts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt, context }),
    });

    if (!response.ok) {
        throw new Error('Failed to generate suggestions');
    }

    const data = await response.json();
    return data.suggestions;
}

/**
 * Generate suggestion for a specific artifact kind
 */
export async function suggestArtifactContent(
    context: SuggestionContext,
    kind: LifecycleArtifactKind
): Promise<ArtifactSuggestion> {
    const suggestions = await generateArtifactSuggestions(context, [kind]);
    if (suggestions.length === 0) {
        throw new Error(`No suggestion generated for ${kind}`);
    }
    return suggestions[0];
}

/**
 * Build AI prompt based on context
 */
function buildSuggestionPrompt(
    context: SuggestionContext,
    targetKinds?: LifecycleArtifactKind[]
): string {
    const { currentPhase, existingArtifacts, projectDescription } = context;

    let prompt = `You are an expert software architect helping with the FOW lifecycle.\n\n`;

    if (projectDescription) {
        prompt += `Project: ${projectDescription}\n\n`;
    }

    prompt += `Current Phase: ${currentPhase}\n\n`;

    if (existingArtifacts.length > 0) {
        prompt += `Existing Artifacts:\n`;
        existingArtifacts.forEach(a => {
            prompt += `- ${a.kind}: ${JSON.stringify(a.payload).substring(0, 200)}...\n`;
        });
        prompt += `\n`;
    }

    if (targetKinds && targetKinds.length > 0) {
        prompt += `Suggest content for: ${targetKinds.join(', ')}\n\n`;
    } else {
        prompt += `Suggest missing artifacts for the current phase.\n\n`;
    }

    prompt += `Provide structured suggestions with title, summary, reasoning, and suggested content.`;

    return prompt;
}

/**
 * Get next recommended artifacts based on current phase
 */
export function getRecommendedArtifacts(
    currentPhase: LifecyclePhase,
    existingKinds: LifecycleArtifactKind[]
): LifecycleArtifactKind[] {
    const phaseArtifacts: Record<LifecyclePhase, LifecycleArtifactKind[]> = {
        INTENT: [
            LifecycleArtifactKind.IDEA_BRIEF,
            LifecycleArtifactKind.RESEARCH_PACK,
            LifecycleArtifactKind.PROBLEM_STATEMENT,
        ],
        SHAPE: [
            LifecycleArtifactKind.REQUIREMENTS,
            LifecycleArtifactKind.ADR,
            LifecycleArtifactKind.UX_SPEC,
            LifecycleArtifactKind.THREAT_MODEL,
        ],
        VALIDATE: [
            LifecycleArtifactKind.VALIDATION_REPORT,
            LifecycleArtifactKind.SIMULATION_RESULTS,
        ],
        GENERATE: [
            LifecycleArtifactKind.DELIVERY_PLAN,
            LifecycleArtifactKind.RELEASE_STRATEGY,
        ],
        RUN: [
            LifecycleArtifactKind.EVIDENCE_PACK,
            LifecycleArtifactKind.RELEASE_PACKET,
        ],
        OBSERVE: [
            LifecycleArtifactKind.OPS_BASELINE,
            LifecycleArtifactKind.INCIDENT_REPORT,
        ],
        IMPROVE: [
            LifecycleArtifactKind.ENHANCEMENT_REQUESTS,
            LifecycleArtifactKind.LEARNING_RECORD,
        ],
    };

    const expectedArtifacts = phaseArtifacts[currentPhase] || [];
    return expectedArtifacts.filter(kind => !existingKinds.includes(kind));
}
