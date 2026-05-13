import { z } from 'zod';

export const observabilityFacetSchema = z.enum([
  'trace',
  'tenantContext',
  'metrics',
  'audit',
  'safeLogging',
  'redaction',
]);

export const observabilityFlowKindSchema = z.enum(['api', 'bridge', 'background', 'frontend', 'job']);

export const behaviorObservabilityEvidenceSchema = z.object({
  type: z.literal('behavior'),
  file: z.string().min(1),
  requiredFacets: z.array(observabilityFacetSchema).min(1),
});

export const observabilityEvidenceSchema = behaviorObservabilityEvidenceSchema;

export const observabilityFlowSchema = z.object({
  product: z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/),
  flow: z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/),
  kind: observabilityFlowKindSchema,
  facets: z.array(observabilityFacetSchema).min(1),
  evidence: z.array(observabilityEvidenceSchema).min(1),
});

export const observabilityFlowManifestSchema = z.object({
  schemaVersion: z.literal('1.0.0'),
  requiredFacets: z.array(observabilityFacetSchema).min(1),
  flows: z.array(observabilityFlowSchema).min(1),
});

export type ObservabilityFacet = z.infer<typeof observabilityFacetSchema>;
export type ObservabilityFlowKind = z.infer<typeof observabilityFlowKindSchema>;
export type ObservabilityEvidence = z.infer<typeof observabilityEvidenceSchema>;
export type ProductObservabilityFlow = z.infer<typeof observabilityFlowSchema>;
export type ObservabilityFlowManifest = z.infer<typeof observabilityFlowManifestSchema>;

export interface ObservabilityFlowValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
  readonly manifest?: ObservabilityFlowManifest;
}

export function validateObservabilityFlowManifest(value: unknown): ObservabilityFlowValidationResult {
  const parsed = observabilityFlowManifestSchema.safeParse(value);
  if (!parsed.success) {
    return {
      valid: false,
      errors: parsed.error.issues.map((issue) => `${pathFor(issue.path)} ${issue.message}`),
    };
  }

  const errors: string[] = [];
  const requiredFacets = new Set(parsed.data.requiredFacets);
  const flowKeys = new Set<string>();

  for (const flow of parsed.data.flows) {
    const flowKey = `${flow.product}:${flow.flow}`;
    if (flowKeys.has(flowKey)) {
      errors.push(`${flowKey} must be unique`);
    }
    flowKeys.add(flowKey);

    const flowFacets = new Set(flow.facets);
    for (const requiredFacet of requiredFacets) {
      if (!flowFacets.has(requiredFacet)) {
        errors.push(`${flowKey} is missing required facet ${requiredFacet}`);
      }
    }

    for (const evidence of flow.evidence) {
      if (evidence.type === 'behavior') {
        for (const facet of evidence.requiredFacets) {
          if (!flowFacets.has(facet)) {
            errors.push(`${flowKey} behavior evidence ${evidence.file} declares undeclared facet ${facet}`);
          }
        }
      }
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    manifest: parsed.data,
  };
}

function pathFor(path: readonly PropertyKey[]): string {
  if (path.length === 0) {
    return '<root>';
  }
  return path
    .map((part) => (typeof part === 'number' ? `[${part}]` : `.${String(part)}`))
    .join('')
    .replace(/^\./, '');
}
