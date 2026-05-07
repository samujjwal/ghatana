/**
 * Canonical YAPPC terminology.
 *
 * @doc.type service
 * @doc.purpose Shared product glossary for UI, docs, and API wording
 * @doc.layer product
 */

export interface YappcTerminologyEntry {
  readonly term: string;
  readonly definition: string;
  readonly useWhen: string;
  readonly avoid?: readonly string[];
}

export const YAPPC_TERMINOLOGY = {
  project: {
    term: 'Project',
    definition: 'The workspace-scoped product effort that moves through the mounted YAPPC lifecycle.',
    useWhen: 'Naming the user-visible work item, route context, dashboard card, or API resource.',
    avoid: ['app', 'application', 'product effort'],
  },
  product: {
    term: 'Product',
    definition: 'The real-world software product or business outcome that a YAPPC project is shaping.',
    useWhen: 'Describing the market/customer outcome, not the stored YAPPC resource.',
  },
  app: {
    term: 'App',
    definition: 'A runnable software application produced, generated, deployed, or observed from a YAPPC project.',
    useWhen: 'Naming deployed or generated runtime software, not the YAPPC project container.',
    avoid: ['project', 'product'],
  },
  artifact: {
    term: 'Artifact',
    definition: 'A governed lifecycle output such as requirements, evidence, generated code, imports, or run results.',
    useWhen: 'Referring to auditable lifecycle outputs that can be reviewed, persisted, or traced.',
  },
  pageDocument: {
    term: 'Page document',
    definition: 'The persisted page artifact envelope that stores builder content, governance metadata, sync state, and operation history.',
    useWhen: 'Discussing page-artifact persistence, save/reload/conflict handling, or artifact operation logs.',
  },
  builderDocument: {
    term: 'Builder document',
    definition: 'The ui-builder document model containing registry-backed component instances and document metadata.',
    useWhen: 'Discussing component contracts, registry validation, serialization, and renderer behavior.',
  },
  canvasNode: {
    term: 'Canvas node',
    definition: 'A node in the product canvas graph. Page-builder nodes may embed page documents.',
    useWhen: 'Discussing spatial canvas interactions, graph import/export, or canvas provenance.',
  },
  lifecyclePacket: {
    term: 'Lifecycle packet',
    definition: 'The phase-specific bundle of persisted data, derived readiness, evidence, blockers, suggestions, review state, and governance trace.',
    useWhen: 'Describing what a mounted phase cockpit presents to help an operator decide the next action.',
  },
} as const satisfies Record<string, YappcTerminologyEntry>;

export type YappcTerminologyKey = keyof typeof YAPPC_TERMINOLOGY;

export function getYappcTerm(key: YappcTerminologyKey): string {
  return YAPPC_TERMINOLOGY[key].term;
}

export function getYappcTerminologyEntries(): readonly YappcTerminologyEntry[] {
  return Object.values(YAPPC_TERMINOLOGY);
}
