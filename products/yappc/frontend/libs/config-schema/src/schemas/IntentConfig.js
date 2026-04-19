/**
 * IntentConfig Schema
 *
 * Declarative configuration for capturing user intent with AI lineage tracking.
 *
 * @packageDocumentation
 */
import { z } from 'zod';
/**
 * IntentConfig schema - captures natural language intent with AI metadata
 */
export const IntentConfigSchema = z.object({
    id: z.string(),
    version: z.string(),
    // Intent content
    intent: z.string().min(1),
    description: z.string().optional(),
    // Requirements linked to this intent
    requirementIds: z.array(z.string()),
    // AI lineage tracking
    aiGenerated: z.boolean().default(false),
    aiConfidence: z.number().min(0).max(1).optional(),
    aiModel: z.string().optional(),
    aiPrompt: z.string().optional(),
    aiTimestamp: z.string().optional(),
    // Metadata
    createdAt: z.string(),
    updatedAt: z.string(),
    author: z.string(),
    tags: z.array(z.string()),
}).strict();
