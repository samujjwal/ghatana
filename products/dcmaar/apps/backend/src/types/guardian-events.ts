import { z } from 'zod';

export const guardianEventSchema = z.object({
    schema_version: z.number().int().positive().default(1),
    event_id: z.string().min(1),
    kind: z.string().min(1),
    subtype: z.string().min(1),
    occurred_at: z.string().min(1),
    received_at: z.string().min(1).optional(),
    source: z.object({
        agent_type: z.string().min(1),
        agent_version: z.string().min(1),
        device_id: z.string().optional(),
        child_id: z.string().optional(),
        org_id: z.string().optional(),
        session_id: z.string().optional(),
    }),
    context: z.record(z.string(), z.any()).optional(),
    payload: z.record(z.string(), z.any()).optional(),
    ai: z
        .object({
            risk_score: z.number().optional(),
            risk_bucket: z.string().optional(),
            labels: z.array(z.string()).optional(),
            model_version: z.string().optional(),
            explanation: z.string().optional(),
        })
        .optional(),
    privacy: z
        .object({
            pii_level: z.enum(['none', 'low', 'high']),
            contains_raw_content: z.boolean(),
            hashed_fields: z.array(z.string()).optional(),
        })
        .optional(),
    metadata: z.record(z.string(), z.any()).optional(),
});

export type GuardianEvent = z.infer<typeof guardianEventSchema>;

export const guardianCommandSchema = z.object({
    schema_version: z.number().int().positive().default(1),
    command_id: z.string().min(1),
    kind: z.string().min(1),
    action: z.string().min(1),
    target: z
        .object({
            device_id: z.string().optional(),
            child_id: z.string().optional(),
            org_id: z.string().optional(),
        })
        .optional(),
    params: z.record(z.string(), z.any()).optional(),
    issued_by: z.object({
        actor_type: z.enum(['parent', 'child', 'system']),
        user_id: z.string().optional(),
    }),
    created_at: z.string().min(1),
    expires_at: z.string().min(1).optional(),
    metadata: z.record(z.string(), z.any()).optional(),
});

export type GuardianCommand = z.infer<typeof guardianCommandSchema>;
