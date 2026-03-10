/**
 * NL Service - Main orchestration for natural language simulation refinement.
 *
 * @doc.type class
 * @doc.purpose Coordinates NL parsing and manifest refinement with AI-powered suggestions
 * @doc.layer product
 * @doc.pattern Service
 */

import type { SimulationManifest } from '@ghatana/tutorputor-contracts/v1/simulation';
import type { AIProxyService } from '@ghatana/tutorputor-contracts/v1/services';
import { NLIntentParser, type ParsedIntent } from './intent-parser';
import { RefinementEngine, type RefinementResult } from './refinement-engine';

/**
 * Conversation context for multi-turn refinement.
 */
interface ConversationContext {
  /** Session ID */
  sessionId: string;
  /** The manifest being refined */
  manifest: SimulationManifest;
  /** Conversation history */
  history: ConversationTurn[];
  /** Last referenced entity */
  lastEntityReference?: string;
  /** Last referenced step */
  lastStepReference?: number;
}

/**
 * A single conversation turn.
 */
interface ConversationTurn {
  /** User input */
  userInput: string;
  /** Parsed intent */
  intent: ParsedIntent;
  /** Result of applying the intent */
  result: RefinementResult;
  /** Timestamp */
  timestamp: number;
}

/**
 * NL refinement response.
 */
export interface NLRefinementResponse {
  /** Whether the refinement was successful */
  success: boolean;
  /** The updated manifest */
  manifest?: SimulationManifest;
  /** Natural language response to the user */
  response: string;
  /** Suggestions for next actions */
  suggestions: string[];
  /** Parsed intent (for debugging) */
  intent: ParsedIntent;
  /** Confidence level */
  confidence: number;
}

/**
 * Active conversation sessions.
 */
const CONVERSATIONS: Map<string, ConversationContext> = new Map();

/**
 * NL Service class.
 */
export class NLService {
  private parser: NLIntentParser;
  private engine: RefinementEngine;
  private aiProxy?: AIProxyService;

  constructor(aiProxy?: AIProxyService) {
    this.parser = new NLIntentParser();
    this.engine = new RefinementEngine();
    this.aiProxy = aiProxy;
  }

  /**
   * Start a new refinement conversation.
   *
   * @param sessionId - Unique session identifier
   * @param manifest - The initial manifest
   * @returns The session ID
   */
  startConversation(sessionId: string, manifest: SimulationManifest): string {
    const context: ConversationContext = {
      sessionId,
      manifest,
      history: [],
    };
    CONVERSATIONS.set(sessionId, context);
    return sessionId;
  }

  /**
   * Process a natural language refinement request.
   *
   * @param sessionId - The conversation session ID
   * @param userInput - The user's natural language input
   * @returns The refinement response
   */
  async refine(sessionId: string, userInput: string): Promise<NLRefinementResponse> {
    const context = CONVERSATIONS.get(sessionId);
    if (!context) {
      return {
        success: false,
        response: 'No active session found. Please start a new conversation.',
        suggestions: [],
        intent: {
          type: 'unknown',
          confidence: 0,
          params: {},
          originalInput: userInput,
          normalizedInput: userInput,
        },
        confidence: 0,
      };
    }

    // Resolve pronouns and references
    const resolvedInput = this.resolveReferences(userInput, context);

    // Parse intent
    let intent: ParsedIntent;
    const manifestEntities = context.manifest.entities ?? [];
    const manifestSteps = context.manifest.steps ?? [];

    if (this.aiProxy) {
      const contextStr = `Entities: ${manifestEntities.map((e) => `${e.id} (${e.type})`).join(", ")}. Steps: ${manifestSteps.length}.`;
      try {
        intent = await this.aiProxy.parseSimulationIntent({
          userInput: resolvedInput,
          context: contextStr,
        });

        // Fallback to regex if AI is unsure
        if (intent.type === "unknown" || intent.confidence < 0.5) {
          const regexIntent = this.parser.parse(resolvedInput);
          if (regexIntent.confidence > intent.confidence) {
            intent = regexIntent;
          }
        }
      } catch (e) {
        console.error("AI parsing failed, falling back to regex", e);
        intent = this.parser.parse(resolvedInput);
      }
    } else {
      intent = this.parser.parse(resolvedInput);
    }

    // Check confidence
    if (intent.confidence < 0.3 && intent.type !== 'explain' && intent.type !== 'clarify') {
      return {
        success: false,
        response: `I'm not sure what you mean by "${userInput}". Could you rephrase that?`,
        suggestions: this.getSuggestionsForLowConfidence(context.manifest),
        intent,
        confidence: intent.confidence,
      };
    }

    // Apply intent
    const result = this.engine.apply(context.manifest, intent);

    // Update context
    if (result.success && result.manifest) {
      context.manifest = result.manifest as SimulationManifest;
    }

    // Update references
    if (intent.params.targetEntity) {
      context.lastEntityReference = intent.params.targetEntity;
    }
    if (intent.params.targetStep !== undefined) {
      context.lastStepReference = typeof intent.params.targetStep === 'number'
        ? intent.params.targetStep
        : parseInt(String(intent.params.targetStep));
    }

    // Record turn
    context.history.push({
      userInput,
      intent,
      result,
      timestamp: Date.now(),
    });

    // Generate response
    let response: string;
    if ((intent.type === 'explain' || intent.type === 'clarify') && this.aiProxy) {
      response = await this.aiProxy.explainSimulation({
        manifest: context.manifest,
        query: userInput
      });
    } else {
      response = this.generateResponse(result, intent);
    }

    return {
      success: result.success,
      manifest: result.manifest,
      response,
      suggestions: result.suggestions.length > 0 ? result.suggestions : this.generateSuggestions(context),
      intent,
      confidence: intent.confidence,
    };
  }

  /**
   * Process multiple refinements in one call.
   *
   * @param sessionId - The session ID
   * @param userInput - The compound input
   * @returns Array of refinement responses
   */
  async refineMultiple(sessionId: string, userInput: string): Promise<NLRefinementResponse[]> {
    const intents = this.parser.parseMultiple(userInput);
    const responses: NLRefinementResponse[] = [];

    for (const intent of intents) {
      const context = CONVERSATIONS.get(sessionId);
      if (!context) break;

      const result = this.engine.apply(context.manifest, intent);

      if (result.success && result.manifest) {
        context.manifest = result.manifest;
      }

      responses.push({
        success: result.success,
        manifest: result.manifest,
        response: this.generateResponse(result, intent),
        suggestions: result.suggestions,
        intent,
        confidence: intent.confidence,
      });

      if (!result.success) break;
    }

    return responses;
  }

  /**
   * Get the current manifest from a conversation.
   *
   * @param sessionId - The session ID
   * @returns The current manifest or undefined
   */
  getManifest(sessionId: string): SimulationManifest | undefined {
    return CONVERSATIONS.get(sessionId)?.manifest;
  }

  /**
   * End a conversation and return the final manifest.
   *
   * @param sessionId - The session ID
   * @returns The final manifest
   */
  endConversation(sessionId: string): SimulationManifest | undefined {
    const context = CONVERSATIONS.get(sessionId);
    CONVERSATIONS.delete(sessionId);
    return context?.manifest;
  }

  /**
   * Get conversation history.
   *
   * @param sessionId - The session ID
   * @returns Array of conversation turns
   */
  getHistory(sessionId: string): ConversationTurn[] {
    return CONVERSATIONS.get(sessionId)?.history ?? [];
  }

  // === Private Helpers ===

  /**
   * Resolve pronouns and references in user input.
   */
  private resolveReferences(input: string, context: ConversationContext): string {
    let resolved = input;

    // Replace "it" with last entity reference
    if (context.lastEntityReference) {
      resolved = resolved.replace(/\bit\b/gi, context.lastEntityReference);
      resolved = resolved.replace(/\bthat\b/gi, context.lastEntityReference);
    }

    // Replace "this step" with last step reference
    if (context.lastStepReference !== undefined) {
      resolved = resolved.replace(/\bthis step\b/gi, `step ${context.lastStepReference}`);
      resolved = resolved.replace(/\bthat step\b/gi, `step ${context.lastStepReference}`);
    }

    return resolved;
  }

  /**
   * Generate a natural language response.
   */
  private generateResponse(result: RefinementResult, intent: ParsedIntent): string {
    if (!result.success) {
      return result.error ?? 'Something went wrong.';
    }

    switch (intent.type) {
      case 'add_entity':
        return `I've added a new ${intent.params.entityType ?? 'element'} to the simulation.`;
      case 'remove_entity':
        return `I've removed the ${intent.params.targetEntity} from the simulation.`;
      case 'modify_entity':
        return `I've updated the ${intent.params.targetEntity}'s ${intent.params.property}.`;
      case 'add_step':
        return `I've added a new step: "${intent.params.text ?? 'New step'}"`;
      case 'remove_step':
        return `I've removed step ${intent.params.targetStep}.`;
      case 'modify_step':
        return `I've modified step ${intent.params.targetStep}.`;
      case 'change_speed':
        return `I've adjusted the simulation speed.`;
      case 'change_visual':
        return `I've updated the visual style of ${intent.params.targetEntity}.`;
      case 'add_annotation':
        return `I've added an annotation: "${intent.params.text}"`;
      case 'undo':
        return `I've undone the last change.`;
      case 'redo':
        return `I've redone the previous action.`;
      case 'explain':
      case 'clarify':
        // Use AI to explain if available, otherwise fallback to suggestions
        // Note: This is a synchronous method in the current architecture, but we need async.
        // The `refine` method calls this `generateResponse`.
        // We might need to refactor `generateResponse` to be async or handle this in `refine`.
        return result.suggestions.join('\n');
      default:
        return result.changes.length > 0
          ? `Done! ${result.changes.join('. ')}`
          : 'Changes applied.';
    }
  }

  /**
   * Generate suggestions based on conversation context.
   */
  private generateSuggestions(context: ConversationContext): string[] {
    const suggestions: string[] = [];
    const manifest = context.manifest;
    const entities = manifest.entities ?? [];
    const steps = manifest.steps ?? [];

    // Suggest based on current state
    if (entities.length === 0) {
      suggestions.push('Add some elements to start building your simulation');
    } else if (steps.length === 0) {
      suggestions.push('Add steps to define what happens in the simulation');
    } else if (steps.length < 3) {
      suggestions.push('Add more steps to make the simulation more detailed');
    }

    // Suggest visual enhancements
    if (entities.length > 0 && !context.history.some((t) => t.intent.type === 'change_visual')) {
      suggestions.push('Try changing colors or sizes to make elements more distinguishable');
    }

    // Suggest annotations
    if (steps.length > 0 && !context.history.some((t) => t.intent.type === 'add_annotation')) {
      suggestions.push('Add annotations to explain what\'s happening');
    }

    return suggestions.slice(0, 3);
  }

  /**
   * Get suggestions for low-confidence intents.
   */
  private getSuggestionsForLowConfidence(manifest: SimulationManifest): string[] {
    const entities = manifest.entities ?? [];
    const entityNames = entities.slice(0, 3).map((e) => e.label);
    return [
      `Try: "add a new element"`,
      `Try: "make ${entityNames[0] ?? 'element'} blue"`,
      `Try: "add a step for comparing"`,
      `Try: "slow down the animation"`,
    ];
  }
}

/**
 * Create a new NL service instance.
 */
export function createNLService(aiProxy?: AIProxyService): NLService {
  return new NLService(aiProxy);
}
