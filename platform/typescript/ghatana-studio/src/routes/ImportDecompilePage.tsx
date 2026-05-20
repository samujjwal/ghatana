/**
 * @fileoverview Import / Decompile workflow page.
 *
 * Lets users upload TypeScript/TSX source files, run the artifact decompiler,
 * review the resulting fidelity report and residual island queue, and push the
 * resolved `LogicalArtifactModel` into the Canvas or Builder workspace.
 *
 * This is a front-end–only page; the actual decompilation is performed by
 * `@ghatana/artifact-compiler-ts` (imported lazily in production, imported
 * directly here for simplicity).
 *
 * @doc.type component
 * @doc.purpose Import / decompile workflow UI
 * @doc.layer studio
 */

import { useState, useCallback } from 'react';
import type { ReactElement, ChangeEvent, FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useSetAtom } from 'jotai';
import {
  createDecompileJobState,
  buildStudioEvidencePack,
  buildDecompileJobResult,
  fidelityTrafficLight,
  fidelitySummaryText,
} from '../adapters/ArtifactStudioWorkflowAdapter.js';
import type {
  DecompileJobState,
  DecompileJobResult,
  SourceEntry,
} from '../adapters/ArtifactStudioWorkflowAdapter.js';
import { setArtifactWorkflowAtom } from '../state/artifactWorkflowStore.js';
import {
  defaultProviderRegistry,
  type ArchiveUploadInput,
  type PastedSourceInput,
  type RepositorySourceInput,
  type SourceFileEntry,
} from '../providers/source-acquisition.js';

// ============================================================================
// Helpers
// ============================================================================

/**
 * Convert SourceFileEntry from provider to SourceEntry for workflow adapter.
 */
function sourceEntryFromProvider(entry: SourceFileEntry): SourceEntry {
  return {
    filePath: entry.relativePath,
    content: entry.content,
  };
}

// ============================================================================
// Component
// ============================================================================

export default function ImportDecompilePage(): ReactElement {
  const [jobState, setJobState] = useState<DecompileJobState | null>(null);
  const [completedResult, setCompletedResult] = useState<DecompileJobResult | null>(null);
  const [fileErrors, setFileErrors] = useState<string[]>([]);
  const [providerKind, setProviderKind] = useState<'upload' | 'paste' | 'github-repository' | 'gitlab-repository' | 'archive'>('upload');
  const [pastedPath, setPastedPath] = useState('src/App.tsx');
  const [pastedContent, setPastedContent] = useState('');
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [repositoryRef, setRepositoryRef] = useState('');
  const navigate = useNavigate();
  const setWorkflow = useSetAtom(setArtifactWorkflowAtom);

  const runAcquisition = useCallback(
    async (input: unknown) => {
      const acquisitionResult = await defaultProviderRegistry.acquire(input);
      setFileErrors([...acquisitionResult.errors]);

      if (acquisitionResult.sources.length === 0) return;

      const sources: SourceEntry[] = acquisitionResult.sources.map(sourceEntryFromProvider);
      const jobId = crypto.randomUUID();
      const startedAt = new Date().toISOString();

      const initialState = createDecompileJobState(jobId, sources.length);
      setJobState({ ...initialState, status: 'running', progress: 10 });
      setCompletedResult(null);

      try {
        // Dynamically import the compiler to avoid bundling TypeScript compiler
        // into the main chunk.
        const { decompileTsx } = await import('@ghatana/artifact-compiler-ts');
        setJobState((prev) =>
          prev ? { ...prev, progress: 40, processedCount: 1 } : prev,
        );

        const result = decompileTsx({
          label: 'Studio import',
          modelId: jobId,
          files: sources.map((s) => ({ relativePath: s.filePath, content: s.content })),
        });

        setJobState((prev) =>
          prev ? { ...prev, progress: 80, processedCount: sources.length } : prev,
        );

        // Detect residual islands (requires re-import to keep bundle tidy)
        const { detectResidualIslands } = await import('@ghatana/artifact-compiler-ts');
        const residualReport = detectResidualIslands(result.model);

        const jobResult = buildDecompileJobResult({
          jobId,
          model: result.model,
          fidelityReport: result.fidelityReport,
          residualIslandReport: residualReport,
          errors: [],
          startedAt,
        });

        // Project the model into a canonical BuilderDocument via the studio adapter
        const { projectModelToBuilderDocument } = await import(
          '../adapters/ModelToBuilderAdapter.js'
        );
        const projectedBuilderDocument = projectModelToBuilderDocument(result.model);

        // Compile preview source from the top-level components
        const { buildRoundTripDiffReport, compileReact } = await import('@ghatana/artifact-compiler-ts');
        const compiled = compileReact(result.model);
        const previewSource = compiled.emittedFiles.map(f => f.content).join('\n\n');
        const reimported = decompileTsx({
          label: `${result.model.label} re-import`,
          modelId: result.model.modelId,
          files: compiled.emittedFiles.map((file) => ({
            relativePath: file.relativePath,
            content: file.content,
          })),
        });
        const roundTripDiffReport = buildRoundTripDiffReport({
          reportId: `round-trip:${jobId}`,
          model: result.model,
          originalSources: sources.map((source) => ({
            relativePath: source.filePath,
            content: source.content,
          })),
          generatedSources: compiled.emittedFiles,
          reimportedModel: reimported.model,
          fidelity: compiled.overallFidelity,
          residuals: residualReport,
        });
        const evidencePack = buildStudioEvidencePack({
          jobResult,
          generatedSources: compiled.emittedFiles,
          compileFidelity: compiled.overallFidelity,
          compileResiduals: residualReport,
        });

        // Persist into the workflow store so all routes can read it
        setWorkflow({
          jobResult,
          model: result.model,
          projectedBuilderDocument,
          previewSource,
          fidelityReport: result.fidelityReport,
          evidencePack,
          roundTripDiffReport,
          lastDecompileAt: new Date().toISOString(),
        });

        setJobState((prev) =>
          prev ? { ...prev, status: 'complete', progress: 100 } : prev,
        );
        setCompletedResult(jobResult);
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        const jobResult = buildDecompileJobResult({
          jobId,
          model: null,
          fidelityReport: null,
          residualIslandReport: null,
          errors: [message],
          startedAt,
        });
        setJobState((prev) =>
          prev ? { ...prev, status: 'failed', progress: 0 } : prev,
        );
        setCompletedResult(jobResult);
      }
    },
    [],
  );

  const handleFilesSelected = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      const files = event.target.files;
      if (!files) return;
      await runAcquisition({ files });
    },
    [runAcquisition],
  );

  const handlePasteSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      const input: PastedSourceInput = {
        kind: 'pasted-source',
        relativePath: pastedPath,
        content: pastedContent,
      };
      await runAcquisition(input);
    },
    [pastedContent, pastedPath, runAcquisition],
  );

  const handleRepositorySubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      const input: RepositorySourceInput = {
        kind: providerKind === 'gitlab-repository' ? 'gitlab-repository' : 'github-repository',
        repositoryUrl,
        ref: repositoryRef.trim() || undefined,
      };
      await runAcquisition(input);
    },
    [providerKind, repositoryRef, repositoryUrl, runAcquisition],
  );

  const handleArchiveSelected = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      if (!file) return;
      const input: ArchiveUploadInput = {
        kind: 'archive-upload',
        file,
      };
      await runAcquisition(input);
    },
    [runAcquisition],
  );

  const trafficLight = fidelityTrafficLight(completedResult?.fidelityReport);
  const fidelitySummary = fidelitySummaryText(completedResult?.fidelityReport);

  const trafficLightColor: Record<typeof trafficLight, string> = {
    green: 'text-green-700 bg-green-50 border-green-200',
    amber: 'text-yellow-700 bg-yellow-50 border-yellow-200',
    red: 'text-red-700 bg-red-50 border-red-200',
    unknown: 'text-gray-700 bg-gray-50 border-gray-200',
  };

  return (
    <section className="space-y-6 p-6" aria-labelledby="import-decompile-title">
      <div className="space-y-2">
        <h2
          id="import-decompile-title"
          className="text-2xl font-semibold text-gray-950"
        >
          Import &amp; Decompile
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          Upload TypeScript or TSX source files to extract a Logical Artifact
          Model, review the fidelity report, and resolve any residual islands
          before pushing to the Builder or Canvas workspace.
        </p>
      </div>

      <div className="space-y-4 rounded-lg border border-gray-200 bg-gray-50 p-6">
        <label htmlFor="source-provider-select" className="block text-sm font-medium text-gray-700">
          Source provider
        </label>
        <select
          id="source-provider-select"
          value={providerKind}
          onChange={(event) => { setProviderKind(event.target.value as typeof providerKind); }}
          className="block w-full max-w-sm rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm"
        >
          <option value="upload">Browser upload</option>
          <option value="paste">Pasted source</option>
          <option value="github-repository">GitHub repository</option>
          <option value="gitlab-repository">GitLab repository</option>
          <option value="archive">Archive upload</option>
        </select>

        {providerKind === 'upload' && (
          <div className="rounded-lg border border-dashed border-gray-300 bg-white p-4">
            <label
              htmlFor="source-file-input"
              className="block text-sm font-medium text-gray-700 mb-2"
            >
              Select source files (.ts / .tsx)
            </label>
            <input
              id="source-file-input"
              type="file"
              multiple
              accept=".ts,.tsx"
              onChange={handleFilesSelected}
              className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4
                file:rounded-md file:border-0 file:text-sm file:font-semibold
                file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
              aria-describedby={fileErrors.length > 0 ? 'file-errors' : undefined}
            />
          </div>
        )}

        {providerKind === 'paste' && (
          <form className="space-y-3 rounded-lg border border-gray-200 bg-white p-4" onSubmit={handlePasteSubmit}>
            <label htmlFor="pasted-source-path" className="block text-sm font-medium text-gray-700">
              Source path
            </label>
            <input
              id="pasted-source-path"
              type="text"
              value={pastedPath}
              onChange={(event) => { setPastedPath(event.target.value); }}
              className="block w-full max-w-md rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
            <label htmlFor="pasted-source-content" className="block text-sm font-medium text-gray-700">
              Source content
            </label>
            <textarea
              id="pasted-source-content"
              value={pastedContent}
              onChange={(event) => { setPastedContent(event.target.value); }}
              rows={8}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 font-mono text-sm"
            />
            <button
              type="submit"
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-500"
            >
              Decompile pasted source
            </button>
          </form>
        )}

        {(providerKind === 'github-repository' || providerKind === 'gitlab-repository') && (
          <form className="space-y-3 rounded-lg border border-gray-200 bg-white p-4" onSubmit={handleRepositorySubmit}>
            <label htmlFor="repository-url" className="block text-sm font-medium text-gray-700">
              Repository URL
            </label>
            <input
              id="repository-url"
              type="url"
              value={repositoryUrl}
              onChange={(event) => { setRepositoryUrl(event.target.value); }}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
            <label htmlFor="repository-ref" className="block text-sm font-medium text-gray-700">
              Ref
            </label>
            <input
              id="repository-ref"
              type="text"
              value={repositoryRef}
              onChange={(event) => { setRepositoryRef(event.target.value); }}
              className="block w-full max-w-md rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
            <button
              type="submit"
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-500"
            >
              Start repository acquisition
            </button>
          </form>
        )}

        {providerKind === 'archive' && (
          <div className="rounded-lg border border-dashed border-gray-300 bg-white p-4">
            <label htmlFor="archive-file-input" className="block text-sm font-medium text-gray-700 mb-2">
              Select source archive
            </label>
            <input
              id="archive-file-input"
              type="file"
              accept=".zip,.tar,.tgz,.tar.gz"
              onChange={handleArchiveSelected}
              className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4
                file:rounded-md file:border-0 file:text-sm file:font-semibold
                file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
            />
          </div>
        )}
      </div>

      {/* File validation errors */}
      {fileErrors.length > 0 && (
        <ul
          id="file-errors"
          className="rounded-md border border-yellow-200 bg-yellow-50 p-4 space-y-1"
          role="alert"
        >
          {fileErrors.map((err) => (
            <li key={err} className="text-sm text-yellow-800">
              {err}
            </li>
          ))}
        </ul>
      )}

      {/* Job progress */}
      {jobState !== null && jobState.status === 'running' && (
        <div
          className="rounded-lg border border-blue-200 bg-blue-50 p-4"
          role="status"
          aria-live="polite"
        >
          <p className="text-sm font-medium text-blue-700">
            Decompiling… {jobState.progress}%
          </p>
          <div className="mt-2 h-2 w-full rounded-full bg-blue-100">
            <div
              className="h-2 rounded-full bg-blue-600 transition-all"
              style={{ width: `${jobState.progress}%` }}
              aria-valuenow={jobState.progress}
              aria-valuemin={0}
              aria-valuemax={100}
              role="progressbar"
            />
          </div>
        </div>
      )}

      {/* Result summary */}
      {completedResult !== null && (
        <div className="space-y-4">
          {completedResult.status === 'failed' ? (
            <div
              className="rounded-lg border border-red-200 bg-red-50 p-4"
              role="alert"
            >
              <p className="text-sm font-semibold text-red-700">
                Decompilation failed
              </p>
              <ul className="mt-1 space-y-1">
                {completedResult.errors.map((e) => (
                  <li key={e} className="text-sm text-red-600">
                    {e}
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <>
              {/* Fidelity badge */}
              <div
                className={`inline-flex items-center rounded-full border px-3 py-1 text-sm font-medium ${trafficLightColor[trafficLight]}`}
              >
                {fidelitySummary}
              </div>

              {/* Model stats */}
              {completedResult.model !== null && (
                <div className="rounded-lg border border-gray-200 bg-white p-4 space-y-1">
                  <p className="text-sm font-medium text-gray-700">
                    Extracted model
                  </p>
                  <ul className="text-sm text-gray-600 space-y-0.5">
                    <li>
                      Nodes: {Object.keys(completedResult.model.nodes).length}
                    </li>
                    <li>
                      Edges: {completedResult.model.edges.length}
                    </li>
                  </ul>
                </div>
              )}

              {/* Residual islands */}
              {completedResult.residualIslandReport !== null &&
                completedResult.residualIslandReport.islands.length > 0 && (
                  <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-4">
                    <p className="text-sm font-semibold text-yellow-800">
                      {completedResult.residualIslandReport.islands.length} residual
                      island{completedResult.residualIslandReport.islands.length === 1 ? '' : 's'}{' '}
                      detected — review before pushing to Builder
                    </p>
                    <ul className="mt-2 space-y-1">
                      {completedResult.residualIslandReport.islands.slice(0, 5).map((island) => (
                        <li key={island.id} className="text-sm text-yellow-700">
                          {island.id}: {island.description}
                        </li>
                      ))}
                      {completedResult.residualIslandReport.islands.length > 5 && (
                        <li className="text-sm text-yellow-600 italic">
                          …and {completedResult.residualIslandReport.islands.length - 5} more
                        </li>
                      )}
                    </ul>
                  </div>
                )}

              {/* Workflow action buttons */}
              <div
                className="flex flex-wrap gap-3 pt-2"
                role="group"
                aria-label="Workflow actions"
              >
                <button
                  type="button"
                  onClick={() => { void navigate('/canvas'); }}
                  className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-40"
                >
                  Open in Canvas
                </button>
                <button
                  type="button"
                  onClick={() => { void navigate('/builder'); }}
                  className="inline-flex items-center gap-2 rounded-md bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-emerald-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-40"
                >
                  Open in Builder
                </button>
                {completedResult.fidelityReport !== null && (
                  <button
                    type="button"
                    onClick={() => { void navigate('/fidelity-report'); }}
                    className="inline-flex items-center gap-2 rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-semibold text-gray-700 shadow-sm hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gray-400"
                  >
                    View Fidelity Report
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      )}
    </section>
  );
}
