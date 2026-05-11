/**
 * Import Orchestration Service
 *
 * @doc.type service
 * @doc.purpose Orchestrate import/decompile workflows for page artifacts
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import type { BuilderDocument, SerializedDocument } from '@ghatana/ui-builder';
import { deserializeDocument, serializeDocument } from '@ghatana/ui-builder';
import type { PageArtifactDocument } from '../../components/canvas/page/pageArtifactDocument';
import { importPageArtifactsFromCode } from '../../components/canvas/page/artifactCompilerBridge';
import { importSourceToPageArtifacts, type ImportSourceType } from '../compiler/ImportSourceWorkflow';
import {
  checkArtifactCompilerRuntimeHealth,
  type ArtifactCompilerRuntimeHealth,
} from '../compiler/ArtifactCompilerRuntimeHealth';
import type { PageBuilderCommandAuditContext } from './commands/PageBuilderCommandAuditService';
import { validateSemanticImport, type SemanticImportGovernanceContext } from './SemanticImportGovernance';

export type ImportWorkflowMode = 'semantic-model' | 'source';

export interface ImportOrchestrationInput {
  readonly mode: ImportWorkflowMode;
  readonly semanticModelInput?: string;
  readonly sourceType?: ImportSourceType;
  readonly sourceLocator?: string;
  readonly projectId?: string;
  readonly auditContext?: PageBuilderCommandAuditContext;
  readonly currentDocument: BuilderDocument;
  readonly isDevMode?: boolean;
}

export interface ImportOrchestrationResult {
  readonly success: boolean;
  readonly error?: string;
  readonly importedDocument?: BuilderDocument;
  readonly artifacts?: readonly PageArtifactDocument[];
  readonly rollbackMetadata?: {
    readonly strategy: 'restore-builder-document';
    readonly serializedBuilderDocument: SerializedDocument;
    readonly capturedAt: string;
    readonly reason: string;
  };
  readonly runtimeHealth?: ArtifactCompilerRuntimeHealth;
  readonly residualIslandIds?: readonly string[];
  readonly artifactId?: string;
  readonly documentId?: string;
  readonly source?: string;
  readonly roundTripFidelity?: PageArtifactDocument['roundTripFidelity'];
  readonly artifactGraph?: PageArtifactDocument['artifactGraph'];
  readonly affectedNodeIds?: readonly string[];
}

export interface ImportOrchestrationOptions {
  readonly maxSourceLength?: number;
  readonly requireServerImport?: boolean;
}

/**
 * Validates import input based on workflow mode
 */
function validateImportInput(input: ImportOrchestrationInput): { readonly valid: boolean; readonly error?: string } {
  const { mode, semanticModelInput, sourceLocator } = input;

  if (mode === 'semantic-model') {
    const trimmedInput = semanticModelInput?.trim() ?? '';
    if (!trimmedInput) {
      return { valid: false, error: 'Paste a JSON semantic model to import.' };
    }
    if (trimmedInput.startsWith('source:')) {
      return { valid: false, error: 'Source imports must use the governed source workflow. Choose Governed source instead of pasting source commands.' };
    }
  }

  if (mode === 'source') {
    const trimmedSourceLocator = sourceLocator?.trim() ?? '';
    if (!trimmedSourceLocator) {
      return { valid: false, error: 'Enter a source URL, route, Storybook story, artifact ID, or zip locator to import.' };
    }
  }

  return { valid: true };
}

/**
 * Validates import context requirements
 */
function validateImportContext(input: ImportOrchestrationInput): { readonly valid: boolean; readonly error?: string } {
  const { mode, projectId, auditContext } = input;

  if (mode === 'source') {
    if (!projectId) {
      return { valid: false, error: 'Source imports require an active project context.' };
    }
    if (!auditContext?.tenantId || !auditContext?.workspaceId) {
      return { valid: false, error: 'Source imports require authenticated tenant and workspace context.' };
    }
  }

  return { valid: true };
}

/**
 * Orchestrates the import/decompile workflow for page artifacts
 *
 * This service encapsulates the complex import logic from PageDesigner.tsx,
 * making it independently testable and reusable.
 */
export async function orchestrateImport(
  input: ImportOrchestrationInput,
  options?: ImportOrchestrationOptions
): Promise<ImportOrchestrationResult> {
  const { mode, semanticModelInput, sourceType, sourceLocator, projectId, auditContext, currentDocument, isDevMode = false } = input;
  const maxSourceLength = options?.maxSourceLength ?? 4096;
  const requireServerImport = options?.requireServerImport ?? true;

  // Validate input
  const inputValidation = validateImportInput(input);
  if (!inputValidation.valid) {
    return { success: false, error: inputValidation.error };
  }

  // Validate context
  const contextValidation = validateImportContext(input);
  if (!contextValidation.valid) {
    return { success: false, error: contextValidation.error };
  }

  // Validate semantic import governance
  if (mode === 'semantic-model' && semanticModelInput) {
    const governanceContext: SemanticImportGovernanceContext = {
      isDevMode,
      projectId,
      auditContext: auditContext ? {
        tenantId: auditContext.tenantId,
        workspaceId: auditContext.workspaceId,
      } : undefined,
    };
    const governanceValidation = validateSemanticImport(semanticModelInput, governanceContext);
    if (!governanceValidation.allowed) {
      return { success: false, error: governanceValidation.reason };
    }
  }

  // Capture rollback document
  const rollbackMetadata = {
    strategy: 'restore-builder-document' as const,
    serializedBuilderDocument: serializeDocument(currentDocument),
    capturedAt: new Date().toISOString(),
    reason: 'Restore the builder document that was active before importing the decompiled semantic model.',
  };

  let artifacts: readonly PageArtifactDocument[];
  let runtimeHealth: ArtifactCompilerRuntimeHealth | undefined;

  // Handle source import workflow
  if (mode === 'source' && sourceType && sourceLocator) {
    // Check runtime health
    runtimeHealth = await checkArtifactCompilerRuntimeHealth();
    if (runtimeHealth.status !== 'available') {
      return { success: false, error: runtimeHealth.message, runtimeHealth };
    }

    // Import from source
    const importFromSourceResult = await importSourceToPageArtifacts(
      {
        sourceType,
        source: sourceLocator,
        projectId: projectId!,
        options: {
          requireServerImport,
          tenantId: auditContext!.tenantId,
          workspaceId: auditContext!.workspaceId,
          maxSourceLength,
        },
      },
      'import',
    );

    if (!importFromSourceResult.importResult.success) {
      return {
        success: false,
        error: importFromSourceResult.importResult.errors[0] ?? 'Source import failed.',
        runtimeHealth,
      };
    }

    artifacts = importFromSourceResult.pageArtifacts;
  } else {
    // Handle semantic model import
    const trimmedInput = semanticModelInput?.trim() ?? '';
    try {
      artifacts = importPageArtifactsFromCode(trimmedInput, 'import');
    } catch (err) {
      return {
        success: false,
        error: err instanceof Error ? err.message : 'Invalid JSON — could not parse semantic model.',
      };
    }
  }

  // Validate artifacts
  const first = artifacts[0];
  if (!first) {
    return { success: false, error: 'No pages found in the imported model.' };
  }

  // Deserialize and prepare imported document
  const imported = first.serializedBuilderDocument;
  const importedBuilderDocument = deserializeDocument(imported);

  // Calculate affected node IDs
  const importedNodes = importedBuilderDocument.nodes;
  const affectedNodeIds: readonly string[] =
    importedNodes instanceof Map
      ? [...importedNodes.keys()].map(String)
      : Object.keys(importedNodes as unknown as Record<string, unknown>);

  return {
    success: true,
    importedDocument: importedBuilderDocument,
    artifacts,
    rollbackMetadata,
    runtimeHealth,
    residualIslandIds: first.residualIslandIds,
    artifactId: first.artifactId,
    documentId: first.documentId,
    source: first.source,
    roundTripFidelity: first.roundTripFidelity,
    artifactGraph: first.artifactGraph,
    affectedNodeIds,
  };
}

/**
 * Builds an import review queue from loss points and residual islands
 */
export function buildImportReviewQueue(
  lossPoints: unknown[] | undefined,
  residualIslandIds: readonly string[]
): {
  readonly id: string;
  readonly kind: 'loss-point' | 'residual-island';
  readonly label: string;
  readonly details: string;
  readonly sourceEvidence: string;
  readonly governedEvidence: string;
  readonly reviewImpact: string;
}[] {
  const lossPointItems = (lossPoints ?? []).map((lossPoint, index: number) => {
    const lp = lossPoint as { readonly type?: string; readonly location?: string; readonly description?: string };
    return {
      id: `loss-${index}-${lp.type ?? 'unknown'}-${lp.location ?? 'unknown'}`,
      kind: 'loss-point' as const,
      label: `${lp.type ?? 'unknown'}${lp.location ? ` at ${lp.location}` : ''}`,
      details: lp.description ?? 'No description available.',
      sourceEvidence: lp.location
        ? `Source path: ${lp.location}`
        : 'Source path unavailable from decompiler metadata.',
      governedEvidence: `Builder impact: ${lp.type ?? 'unknown'} requires an explicit apply or skip decision before trust promotion.`,
      reviewImpact: lp.description ?? 'No impact description available.',
    };
  });

  const residualItems = residualIslandIds.map((residualIslandId) => ({
    id: `residual-${residualIslandId}`,
    kind: 'residual-island' as const,
    label: residualIslandId,
    details: 'Residual island requires an accept or reject decision before handoff.',
    sourceEvidence: `Residual source island: ${residualIslandId}`,
    governedEvidence: 'Builder impact: no reviewed registry contract was available for this imported element.',
    reviewImpact: 'Accept, reject, or promote the residual island so graph handoff does not hide unsupported structure.',
  }));

  return [...lossPointItems, ...residualItems];
}
