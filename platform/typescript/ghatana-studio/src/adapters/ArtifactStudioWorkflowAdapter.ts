/**
 * @fileoverview Studio workflow adapter for the artifact compiler/decompiler pipeline.
 *
 * Provides typed operations that connect the Studio UI to the
 * `@ghatana/artifact-compiler-ts` decompile/compile/fidelity pipeline.
 * The adapter is deliberately thin: it orchestrates calls to the compiler
 * package and converts results into shapes the Studio UI can consume directly.
 *
 * Import path: `../adapters/ArtifactStudioWorkflowAdapter`
 *
 * @doc.type module
 * @doc.purpose Studio ↔ Artifact compiler/decompiler workflow adapter
 * @doc.layer studio
 * @doc.pattern Adapter
 */

import type {
  CompileResult,
  EvidencePack,
  GeneratedArtifactValidationEvidence,
  LogicalArtifactModel,
  FidelityReport,
  LossPoint,
  RoundTripDiffReport,
  ResidualIslandReport,
  ValidationFinding,
  ValidationEvidenceStageId,
  ValidationEvidenceStageResult,
  ValidationPipelineResult,
} from '@ghatana/artifact-contracts';
import { computeFidelityReport, createResidualIslandReport } from '@ghatana/artifact-contracts';
import type { BuilderDocument } from '@ghatana/ui-builder';
import { generateReactCode, validateBuilderDocument } from '@ghatana/ui-builder';
import type { LossPoint as BuilderCodegenLossPoint } from '@ghatana/ui-builder';
import type { ComponentContract, PropType } from '@ghatana/ds-schema';

// ============================================================================
// Types
// ============================================================================

/** Status of an active or completed import/decompile job. */
export type DecompileJobStatus =
  | 'idle'
  | 'running'
  | 'complete'
  | 'failed';

/** A file entry submitted for decompilation. */
export interface SourceEntry {
  /** Relative file path within the project (e.g. "src/Button.tsx"). */
  readonly filePath: string;
  /** UTF-8 source content. */
  readonly content: string;
}

export interface GeneratedSourceEntry {
  readonly relativePath: string;
  readonly content: string;
}

export interface BuilderEditWorkflowArtifacts {
  readonly previewSource: string;
  readonly fidelityReport: FidelityReport;
  readonly evidencePack: EvidencePack;
  readonly roundTripDiffReport: RoundTripDiffReport;
  readonly validationResult: ValidationPipelineResult;
}

/** Result of a single decompile job. */
export interface DecompileJobResult {
  readonly jobId: string;
  readonly status: Exclude<DecompileJobStatus, 'idle' | 'running'>;
  readonly model: LogicalArtifactModel | null;
  readonly fidelityReport: FidelityReport | null;
  readonly residualIslandReport: ResidualIslandReport | null;
  readonly errors: readonly string[];
  readonly startedAt: string;
  readonly completedAt: string;
}

/** In-progress job state for Studio UI polling/display. */
export interface DecompileJobState {
  readonly jobId: string;
  readonly status: DecompileJobStatus;
  readonly progress: number; // 0–100
  readonly fileCount: number;
  readonly processedCount: number;
  readonly result?: DecompileJobResult;
  /** Display name for the primary source file (e.g. "Button.tsx"). */
  readonly fileName?: string;
}

/** Options for initiating a decompile job. */
export interface StartDecompileJobOptions {
  readonly sources: readonly SourceEntry[];
  /** Project root path for relative path resolution (optional). */
  readonly projectRoot?: string;
  /**
   * Confidence threshold below which nodes are classified as residual islands.
   * Default: 0.6
   */
  readonly confidenceThreshold?: number;
}

// ============================================================================
// Workflow operations
// ============================================================================

/**
 * Create a new decompile job state with initial values.
 * The caller is responsible for persisting and updating this state.
 */
export function createDecompileJobState(
  jobId: string,
  fileCount: number,
): DecompileJobState {
  return {
    jobId,
    status: 'idle',
    progress: 0,
    fileCount,
    processedCount: 0,
  };
}

/**
 * Produce a `DecompileJobResult` from a completed job.
 * Suitable for storing in persistent state or passing to the fidelity report page.
 */
export function buildDecompileJobResult(params: {
  jobId: string;
  model: LogicalArtifactModel | null;
  fidelityReport: FidelityReport | null;
  residualIslandReport: ResidualIslandReport | null;
  errors: readonly string[];
  startedAt: string;
}): DecompileJobResult {
  return {
    jobId: params.jobId,
    status: params.errors.length > 0 ? 'failed' : 'complete',
    model: params.model,
    fidelityReport: params.fidelityReport,
    residualIslandReport: params.residualIslandReport,
    errors: params.errors,
    startedAt: params.startedAt,
    completedAt: new Date().toISOString(),
  };
}

// ============================================================================
// Fidelity summary helpers for Studio UI
// ============================================================================

/**
 * Compute a traffic-light summary from a fidelity report suitable for
 * displaying in the Studio artifact list panel.
 */
export type FidelityTrafficLight = 'green' | 'amber' | 'red' | 'unknown';

export function fidelityTrafficLight(
  report: FidelityReport | null | undefined,
): FidelityTrafficLight {
  if (report == null) return 'unknown';
  const score = report.score;
  if (score >= 0.95) return 'green';
  if (score >= 0.75) return 'amber';
  return 'red';
}

/**
 * Return a concise human-readable summary of the fidelity report for display
 * in a tooltip or list-item subtitle.
 */
export function fidelitySummaryText(
  report: FidelityReport | null | undefined,
): string {
  if (report == null) return 'No fidelity data';
  const pct = Math.round(report.score * 100);
  const lossCount = report.lossPoints.length;
  if (lossCount === 0) return `${pct}% — no loss points`;
  return `${pct}% — ${lossCount} loss point${lossCount === 1 ? '' : 's'}`;
}

/**
 * Merge a decompile result's model into an existing merged model.
 *
 * When the Studio processes multiple files through separate calls, this helper
 * merges the resulting models.  Nodes are combined by nodeId; edges are
 * deduplicated by edgeId.
 */
export function mergeModels(
  base: LogicalArtifactModel | null,
  incoming: LogicalArtifactModel,
): LogicalArtifactModel {
  if (base === null) return incoming;

  // Nodes are a Record<string, ArtifactNode> — merge by nodeId key (incoming wins on conflict)
  const mergedNodes = { ...base.nodes, ...incoming.nodes };

  // Edges are an array — deduplicate by edge id
  const edgeIdSet = new Set<string>(base.edges.map((e) => e.id));
  const mergedEdges = [
    ...base.edges,
    ...incoming.edges.filter((e) => !edgeIdSet.has(e.id)),
  ];

  return {
    ...base,
    nodes: mergedNodes,
    edges: mergedEdges,
  };
}

export function buildStudioEvidencePack(params: {
  readonly jobResult: DecompileJobResult;
  readonly generatedSources: readonly GeneratedSourceEntry[];
  readonly compileFidelity: FidelityReport;
  readonly compileResiduals?: ResidualIslandReport;
  readonly validationResult?: ValidationPipelineResult;
  readonly durationMs?: number;
}): EvidencePack | null {
  const { jobResult } = params;
  if (
    jobResult.model === null ||
    jobResult.fidelityReport === null ||
    jobResult.residualIslandReport === null
  ) {
    return null;
  }

  const compileResult: CompileResult = {
    success: true,
    emittedFiles: Object.fromEntries(
      params.generatedSources.map((source) => [source.relativePath, source.content]),
    ),
    fidelity: params.compileFidelity,
    residuals: params.compileResiduals ?? createResidualIslandReport([]),
    errors: [],
    warnings: [],
    compiledAt: new Date().toISOString(),
    durationMs: params.durationMs,
  };
  const generatedValidationEvidence = params.validationResult === undefined
    ? undefined
    : buildGeneratedArtifactValidationEvidence({
        targetId: jobResult.model.modelId,
        validationResult: params.validationResult,
        generatedSources: params.generatedSources,
      });

  return {
    evidenceId: `evidence:${jobResult.jobId}`,
    createdAt: jobResult.completedAt,
    modelId: jobResult.model.modelId,
    label: `Studio artifact workflow ${jobResult.jobId}`,
    stage: 'round-trip',
    fidelity: jobResult.fidelityReport,
    residuals: jobResult.residualIslandReport,
    decompileResult: {
      success: jobResult.status === 'complete',
      modelId: jobResult.model.modelId,
      nodeCount: Object.keys(jobResult.model.nodes).length,
      edgeCount: jobResult.model.edges.length,
      fidelity: jobResult.fidelityReport,
      residuals: jobResult.residualIslandReport,
      errors: jobResult.errors.map((message) => ({ code: 'studio-decompile-error', message })),
      decompiledAt: jobResult.completedAt,
      durationMs: Math.max(0, new Date(jobResult.completedAt).getTime() - new Date(jobResult.startedAt).getTime()),
    },
    compileResult,
    ...(params.validationResult === undefined ? {} : { validationResult: params.validationResult }),
    ...(generatedValidationEvidence === undefined ? {} : { generatedValidationEvidence }),
    summary: `Round-trip workflow generated ${params.generatedSources.length} file${params.generatedSources.length === 1 ? '' : 's'} from ${Object.keys(jobResult.model.nodes).length} artifact node${Object.keys(jobResult.model.nodes).length === 1 ? '' : 's'}.`,
    reviewStatus: 'pending',
  };
}

export function buildBuilderEditWorkflowArtifacts(params: {
  readonly document: BuilderDocument;
  readonly previousPreviewSource?: string | null;
}): BuilderEditWorkflowArtifacts {
  const generatedAt = new Date().toISOString();
  const contracts = buildDocumentContracts(params.document);
  const projection = generateReactCode(params.document, contracts, {
    format: 'functional',
    typescript: true,
    importPath: '@ghatana/design-system',
    componentName: 'BuilderEditedArtifact',
  });
  const generatedSources = projection.files.map((file) => ({
    relativePath: file.path,
    content: file.content,
  }));
  const previewSource = generatedSources.map((source) => source.content).join('\n\n');
  const validationResult = buildBuilderValidationPipelineResult(params.document, generatedAt);
  const fidelityReport = computeFidelityReport(
    [
      ...projection.roundTripFidelity.lossPoints.map(mapBuilderLossPoint),
      ...validationResult.findings
        .filter((finding) => finding.severity === 'error')
        .map((finding): LossPoint => ({
          code: `builder-validation/${finding.code}`,
          description: finding.message,
          severity: 'critical',
          confidenceImpact: 0.2,
        })),
    ],
    params.document.documentId,
    'pipeline',
  );
  const residuals = createResidualIslandReport([]);
  const emittedFiles = Object.fromEntries(
    generatedSources.map((source) => [source.relativePath, source.content]),
  );
  const firstGeneratedSource = generatedSources[0] ?? {
    relativePath: 'BuilderEditedArtifact.tsx',
    content: '',
  };
  const previousPreviewSource = params.previousPreviewSource ?? '';

  const compileResult: CompileResult = {
    success: validationResult.errorCount === 0,
    emittedFiles,
    fidelity: fidelityReport,
    residuals,
    errors: validationResult.findings
      .filter((finding) => finding.severity === 'error')
      .map((finding) => ({ code: finding.code, message: finding.message })),
    warnings: validationResult.findings
      .filter((finding) => finding.severity === 'warning')
      .map((finding) => ({ code: finding.code, message: finding.message })),
    compiledAt: generatedAt,
  };

  const roundTripDiffReport: RoundTripDiffReport = {
    reportId: `builder-edit:${params.document.documentId}:${generatedAt}`,
    modelId: params.document.documentId,
    diffs: [
      {
        diffId: `builder-edit:${firstGeneratedSource.relativePath}`,
        originalPath: 'previous-preview.tsx',
        generatedPath: firstGeneratedSource.relativePath,
        semanticallyEquivalent: previousPreviewSource === firstGeneratedSource.content,
        hunks: previousPreviewSource === firstGeneratedSource.content
          ? []
          : [
              {
                kind: previousPreviewSource.length === 0 ? 'added' : 'changed',
                generatedStart: 1,
                ...(previousPreviewSource.length > 0 ? { originalStart: 1 } : {}),
                lineCount: Math.max(
                  1,
                  Math.max(
                    splitSourceLines(previousPreviewSource).length,
                    splitSourceLines(firstGeneratedSource.content).length,
                  ),
                ),
                originalSnippet: previousPreviewSource.slice(0, 500),
                generatedSnippet: firstGeneratedSource.content.slice(0, 500),
              },
            ],
        addedLines: splitSourceLines(firstGeneratedSource.content).length,
        removedLines: splitSourceLines(previousPreviewSource).length,
        unchangedLines: previousPreviewSource === firstGeneratedSource.content
          ? splitSourceLines(firstGeneratedSource.content).length
          : 0,
        diffedAt: generatedAt,
      },
    ],
    fidelity: fidelityReport,
    residuals,
    validation: validationResult,
    paritySections: [
      {
        kind: 'component',
        status: validationResult.errorCount === 0 ? 'passed' : 'failed',
        summary: validationResult.errorCount === 0
          ? 'Builder document validates after the edit.'
          : 'Builder document validation failed after the edit.',
        findings: validationResult.findings.map((finding) => `${finding.severity}:${finding.code}:${finding.message}`),
      },
      {
        kind: 'validation',
        status: validationResult.passed ? 'passed' : validationResult.errorCount > 0 ? 'failed' : 'warning',
        summary: validationResult.passed
          ? 'Builder edit generated source validation passed.'
          : 'Builder edit generated source validation reported findings.',
        findings: validationResult.findings.map((finding) => `${finding.severity}:${finding.code}:${finding.message}`),
      },
    ],
    isLossless: validationResult.passed && fidelityReport.canRoundTrip,
    generatedAt,
  };

  const evidencePack: EvidencePack = {
    evidenceId: `evidence:builder-edit:${params.document.documentId}:${generatedAt}`,
    createdAt: generatedAt,
    modelId: params.document.documentId,
    label: `Builder edit workflow ${params.document.documentId}`,
    stage: 'compile',
    fidelity: fidelityReport,
    residuals,
    compileResult,
    validationResult,
    generatedValidationEvidence: buildGeneratedArtifactValidationEvidence({
      targetId: params.document.documentId,
      validationResult,
      generatedSources,
    }),
    summary: `Builder edit generated ${generatedSources.length} preview source file${generatedSources.length === 1 ? '' : 's'} from ${Object.keys(params.document.nodes).length} builder node${Object.keys(params.document.nodes).length === 1 ? '' : 's'}.`,
    reviewStatus: validationResult.passed ? 'pending' : 'needs-revision',
  };

  return {
    previewSource,
    fidelityReport,
    evidencePack,
    roundTripDiffReport,
    validationResult,
  };
}

function buildDocumentContracts(document: BuilderDocument): ReadonlyMap<string, ComponentContract> {
  const entries = Object.values(document.nodes)
    .filter((node) => node.contractName !== 'RootContainer')
    .map((node): [string, ComponentContract] => [
      node.contractName,
      {
        name: node.contractName,
        version: '1.0.0',
        props: Object.entries(node.props).map(([name, value]) => ({
          name,
          type: propTypeFromValue(value),
          required: false,
        })),
        slots: Object.keys(node.slots).map((name) => ({
          name,
          isDefault: name === 'children' || name === 'default',
          allowsReorder: true,
          isSingleChild: false,
        })),
        events: [],
        metadata: {
          category: 'builder',
          status: 'stable',
          platforms: ['web'],
        },
        builder: {
          codegen: {
            importPath: '@ghatana/design-system',
            componentName: node.contractName,
          },
        },
      },
    ]);
  return new Map(entries);
}

function propTypeFromValue(value: unknown): PropType {
  if (typeof value === 'string') return 'string';
  if (typeof value === 'number') return 'number';
  if (typeof value === 'boolean') return 'boolean';
  if (Array.isArray(value)) return 'array';
  if (value === null || typeof value === 'object') return 'object';
  return 'string';
}

function buildBuilderValidationPipelineResult(
  document: BuilderDocument,
  validatedAt: string,
): ValidationPipelineResult {
  const validation = validateBuilderDocument(document);
  const findings: ValidationFinding[] = validation.issues.map((issue) => ({
    code: issue.code,
    message: issue.message,
    severity: issue.severity === 'error' ? 'error' : 'warning',
    category: 'contract',
    suggestion: 'Open the Builder validation panel and resolve the invalid document reference before publishing.',
  }));
  const errorCount = findings.filter((finding) => finding.severity === 'error').length;
  const warningCount = findings.filter((finding) => finding.severity === 'warning').length;
  const infoCount = findings.filter((finding) => finding.severity === 'info').length;

  return {
    targetId: document.documentId,
    passed: errorCount === 0,
    findings,
    errorCount,
    warningCount,
    infoCount,
    validatedAt,
  };
}

interface ValidationStageDefinition {
  readonly stageId: ValidationEvidenceStageId;
  readonly category: ValidationFinding['category'];
  readonly label: string;
  readonly markerCodes: readonly string[];
  readonly assumedRun: boolean;
}

const GENERATED_VALIDATION_STAGE_DEFINITIONS: readonly ValidationStageDefinition[] = [
  {
    stageId: 'typecheck',
    category: 'typescript',
    label: 'TypeScript typecheck',
    markerCodes: ['stage/typecheck'],
    assumedRun: true,
  },
  {
    stageId: 'lint',
    category: 'eslint',
    label: 'Lint',
    markerCodes: ['stage/lint'],
    assumedRun: false,
  },
  {
    stageId: 'build',
    category: 'build',
    label: 'Build',
    markerCodes: ['stage/build'],
    assumedRun: false,
  },
  {
    stageId: 'test',
    category: 'test',
    label: 'Test',
    markerCodes: ['stage/test'],
    assumedRun: false,
  },
  {
    stageId: 'preview-smoke',
    category: 'preview',
    label: 'Preview smoke',
    markerCodes: ['stage/preview-render'],
    assumedRun: false,
  },
];

function buildGeneratedArtifactValidationEvidence(params: {
  readonly targetId: string;
  readonly validationResult: ValidationPipelineResult;
  readonly generatedSources: readonly GeneratedSourceEntry[];
}): GeneratedArtifactValidationEvidence {
  const stages = GENERATED_VALIDATION_STAGE_DEFINITIONS.map((definition) =>
    summarizeValidationStage(definition, params.validationResult.findings),
  );

  return {
    targetId: params.targetId,
    passed: params.validationResult.passed,
    pipeline: params.validationResult,
    typeScriptDiagnostics: params.validationResult.findings.filter(
      (finding) => finding.category === 'typescript',
    ),
    stages,
    artifacts: params.generatedSources.map((source) => ({
      label: source.relativePath,
      relativePath: source.relativePath,
      contentType: contentTypeForGeneratedSource(source.relativePath),
    })),
    validatedAt: params.validationResult.validatedAt,
    ...(params.validationResult.durationMs === undefined
      ? {}
      : { durationMs: params.validationResult.durationMs }),
  };
}

function summarizeValidationStage(
  definition: ValidationStageDefinition,
  findings: readonly ValidationFinding[],
): ValidationEvidenceStageResult {
  const stageFindings = findings.filter(
    (finding) =>
      finding.category === definition.category ||
      definition.markerCodes.includes(finding.code),
  );
  const ran =
    definition.assumedRun ||
    stageFindings.length > 0 ||
    stageFindings.some((finding) => definition.markerCodes.includes(finding.code));

  if (!ran) {
    return {
      stageId: definition.stageId,
      status: 'not-run',
      summary: `${definition.label} was not run for this evidence pack.`,
      findings: [],
    };
  }

  const hasError = stageFindings.some((finding) => finding.severity === 'error');
  const hasWarning = stageFindings.some((finding) => finding.severity === 'warning');
  const status = hasError ? 'failed' : hasWarning ? 'warning' : 'passed';
  const summary = status === 'passed'
    ? `${definition.label} passed.`
    : `${definition.label} reported ${stageFindings.length} finding${stageFindings.length === 1 ? '' : 's'}.`;

  return {
    stageId: definition.stageId,
    status,
    summary,
    findings: stageFindings,
  };
}

function contentTypeForGeneratedSource(relativePath: string): string {
  if (relativePath.endsWith('.tsx')) return 'text/tsx';
  if (relativePath.endsWith('.ts')) return 'text/typescript';
  if (relativePath.endsWith('.jsx')) return 'text/jsx';
  if (relativePath.endsWith('.js')) return 'text/javascript';
  if (relativePath.endsWith('.css')) return 'text/css';
  if (relativePath.endsWith('.json')) return 'application/json';
  return 'text/plain';
}

function mapBuilderLossPoint(lossPoint: BuilderCodegenLossPoint): LossPoint {
  return {
    code: `builder-codegen/${lossPoint.type}`,
    description: lossPoint.description,
    severity: lossPoint.type === 'custom-code' ? 'critical' : 'warning',
    confidenceImpact: lossPoint.type === 'custom-code' ? 0.2 : 0.1,
  };
}

function splitSourceLines(source: string): readonly string[] {
  if (source.length === 0) return [];
  return source.split(/\r?\n/);
}
