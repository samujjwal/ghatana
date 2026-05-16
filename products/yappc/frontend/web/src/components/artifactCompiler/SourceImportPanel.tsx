/**
 * @fileoverview Source Import Panel for Artifact Compiler
 *
 * P2-2: Source import panel with:
 * - Provider selector (GitHub, GitLab, ZIP archive)
 * - Repo/ref/archive input fields
 * - Progress display during import
 * - Job polling functionality
 * - Unsupported state display
 *
 * @doc.type component
 * @doc.purpose UI component for source code import into artifact compiler
 * @doc.layer product
 * @doc.pattern Panel
 */

import { useState, useEffect } from 'react';
import { 
  X, 
  Github, 
  Gitlab, 
  Archive, 
  Upload, 
  Loader2, 
  CheckCircle2, 
  AlertCircle,
  RefreshCw
} from 'lucide-react';

import { cn } from '@/lib/utils';
import { Button } from '../ui/Button';
import { getArtifactCompilerClient } from '@/clients/artifactCompiler/ArtifactCompilerClient';
import type { ArtifactGraphIngestRequest, ArtifactGraphIngestResponse } from '@/clients/artifactCompiler/ArtifactCompilerClient';

// ============================================================================
// Types
// ============================================================================

export type ImportProvider = 'github' | 'gitlab' | 'archive';

export interface ImportJob {
  id: string;
  provider: ImportProvider;
  status: 'pending' | 'running' | 'completed' | 'failed' | 'unsupported';
  progress: number;
  message: string;
  error?: string;
  unsupportedFeatures?: string[];
  snapshotId?: string;
  versionId?: string;
  nodeCount?: number;
  edgeCount?: number;
}

export interface SourceImportPanelProps {
  open: boolean;
  onClose: () => void;
  productId: string;
  tenantId: string;
  onImportComplete?: (jobId: string, result: ArtifactGraphIngestResponse) => void;
}

// ============================================================================
// Component
// ============================================================================

export function SourceImportPanel({
  open,
  onClose,
  productId,
  tenantId,
  onImportComplete,
}: SourceImportPanelProps) {
  const [provider, setProvider] = useState<ImportProvider>('github');
  const [repoUrl, setRepoUrl] = useState('');
  const [ref, setRef] = useState('main');
  const [archiveFile, setArchiveFile] = useState<File | null>(null);
  const [job, setJob] = useState<ImportJob | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Poll job status
  useEffect(() => {
    if (!job || job.status !== 'running') {
      return;
    }

    const pollInterval = setInterval(async () => {
      try {
        // In a real implementation, this would poll the job status endpoint
        // For now, we'll simulate progress
        setJob((prev) => {
          if (!prev || prev.status !== 'running') {
            return prev;
          }
          const newProgress = Math.min(prev.progress + 10, 100);
          if (newProgress === 100) {
            return {
              ...prev,
              status: 'completed',
              progress: 100,
              message: 'Import completed successfully',
            };
          }
          return {
            ...prev,
            progress: newProgress,
            message: `Processing... ${newProgress}%`,
          };
        });
      } catch (error) {
        console.error('Error polling job status:', error);
      }
    }, 1000);

    return () => clearInterval(pollInterval);
  }, [job]);

  if (!open) {
    return null;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);

    try {
      const client = getArtifactCompilerClient({
        baseUrl: '/api',
        authToken: '',
        tenantId,
      });

      // Create import job
      const newJob: ImportJob = {
        id: `job-${Date.now()}`,
        provider,
        status: 'pending',
        progress: 0,
        message: 'Initializing import...',
      };
      setJob(newJob);

      // Simulate starting the job
      setTimeout(() => {
        setJob((prev) => ({
          ...prev!,
          status: 'running',
          progress: 10,
          message: 'Scanning source files...',
        }));
      }, 500);

      // In a real implementation, this would call the actual import API
      // For now, we'll simulate the process
      setTimeout(() => {
        const mockResponse: ArtifactGraphIngestResponse = {
          success: true,
          message: 'Import completed successfully',
          snapshotId: 'snapshot-123',
          versionId: 'version-456',
          nodeCount: 100,
          edgeCount: 200,
        };

        setJob((prev) => ({
          ...prev!,
          status: 'completed',
          progress: 100,
          message: 'Import completed successfully',
          ...mockResponse,
        }));

        onImportComplete?.(newJob.id, mockResponse);
      }, 3000);

    } catch (error) {
      setJob((prev) => ({
        ...prev!,
        status: 'failed',
        progress: 0,
        message: 'Import failed',
        error: error instanceof Error ? error.message : 'Unknown error',
      }));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] || null;
    setArchiveFile(file);
  };

  const handleRetry = () => {
    setJob(null);
  };

  return (
    <aside
      className="fixed right-0 top-14 z-40 flex h-[calc(100vh-3.5rem)] w-full max-w-lg flex-col border-l border-divider bg-bg-paper shadow-2xl"
      role="complementary"
      aria-label="Source Import Panel"
      data-testid="source-import-panel"
    >
      {/* Header */}
      <div className="flex items-center justify-between border-b border-divider px-5 py-4">
        <div className="flex items-center gap-3">
          <div className="rounded-full bg-primary-100 p-2 text-primary-700">
            <Upload className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-sm font-semibold text-text-primary">Import Source Code</h2>
            <p className="text-xs text-text-secondary">Add repository or archive</p>
          </div>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={onClose}
          className="rounded-md p-1 text-text-secondary hover:bg-grey-100"
          aria-label="Close panel"
        >
          <X className="h-4 w-4" />
        </Button>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto px-5 py-4">
        {!job ? (
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Provider Selector */}
            <div>
              <label className="mb-2 block text-sm font-medium text-text-primary">
                Source Provider
              </label>
              <div className="grid grid-cols-3 gap-2">
                <button
                  type="button"
                  onClick={() => setProvider('github')}
                  className={cn(
                    'flex flex-col items-center gap-2 rounded-lg border-2 p-3 transition-colors',
                    provider === 'github'
                      ? 'border-primary-500 bg-primary-50'
                      : 'border-divider bg-bg-default hover:border-primary-300'
                  )}
                >
                  <Github className="h-5 w-5" />
                  <span className="text-xs font-medium">GitHub</span>
                </button>
                <button
                  type="button"
                  onClick={() => setProvider('gitlab')}
                  className={cn(
                    'flex flex-col items-center gap-2 rounded-lg border-2 p-3 transition-colors',
                    provider === 'gitlab'
                      ? 'border-primary-500 bg-primary-50'
                      : 'border-divider bg-bg-default hover:border-primary-300'
                  )}
                >
                  <Gitlab className="h-5 w-5" />
                  <span className="text-xs font-medium">GitLab</span>
                </button>
                <button
                  type="button"
                  onClick={() => setProvider('archive')}
                  className={cn(
                    'flex flex-col items-center gap-2 rounded-lg border-2 p-3 transition-colors',
                    provider === 'archive'
                      ? 'border-primary-500 bg-primary-50'
                      : 'border-divider bg-bg-default hover:border-primary-300'
                  )}
                >
                  <Archive className="h-5 w-5" />
                  <span className="text-xs font-medium">Archive</span>
                </button>
              </div>
            </div>

            {/* GitHub/GitLab Input */}
            {(provider === 'github' || provider === 'gitlab') && (
              <div className="space-y-3">
                <div>
                  <label htmlFor="repoUrl" className="mb-2 block text-sm font-medium text-text-primary">
                    Repository URL
                  </label>
                  <input
                    id="repoUrl"
                    type="url"
                    value={repoUrl}
                    onChange={(e) => setRepoUrl(e.target.value)}
                    placeholder={`https://${provider}.com/owner/repo`}
                    className="w-full rounded-md border border-divider px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                    required
                  />
                </div>
                <div>
                  <label htmlFor="ref" className="mb-2 block text-sm font-medium text-text-primary">
                    Branch / Tag / Commit
                  </label>
                  <input
                    id="ref"
                    type="text"
                    value={ref}
                    onChange={(e) => setRef(e.target.value)}
                    placeholder="main"
                    className="w-full rounded-md border border-divider px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                  />
                </div>
              </div>
            )}

            {/* Archive Input */}
            {provider === 'archive' && (
              <div>
                <label htmlFor="archiveFile" className="mb-2 block text-sm font-medium text-text-primary">
                  Archive File
                </label>
                <input
                  id="archiveFile"
                  type="file"
                  accept=".zip,.tar,.tar.gz,.tgz"
                  onChange={handleFileChange}
                  className="w-full rounded-md border border-divider px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                  required
                />
                {archiveFile && (
                  <p className="mt-2 text-xs text-text-secondary">
                    Selected: {archiveFile.name}
                  </p>
                )}
              </div>
            )}

            {/* Submit Button */}
            <Button
              type="submit"
              disabled={isSubmitting}
              className="w-full"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Starting import...
                </>
              ) : (
                'Start Import'
              )}
            </Button>
          </form>
        ) : (
          /* Job Status */
          <div className="space-y-4">
            {job.status === 'running' && (
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <Loader2 className="h-5 w-5 animate-spin text-primary-600" />
                  <div>
                    <h3 className="text-sm font-semibold text-text-primary">Importing...</h3>
                    <p className="text-xs text-text-secondary">{job.message}</p>
                  </div>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-divider">
                  <div
                    className="h-full bg-primary-600 transition-all duration-300"
                    style={{ width: `${job.progress}%` }}
                  />
                </div>
                <p className="text-center text-xs text-text-secondary">{job.progress}%</p>
              </div>
            )}

            {job.status === 'completed' && (
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <CheckCircle2 className="h-5 w-5 text-success-color" />
                  <div>
                    <h3 className="text-sm font-semibold text-text-primary">Import Complete</h3>
                    <p className="text-xs text-text-secondary">{job.message}</p>
                  </div>
                </div>
                <div className="rounded-lg border border-divider bg-bg-default p-4 space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-text-secondary">Nodes extracted:</span>
                    <span className="font-medium text-text-primary">{job.nodeCount}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-text-secondary">Edges extracted:</span>
                    <span className="font-medium text-text-primary">{job.edgeCount}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-text-secondary">Snapshot ID:</span>
                    <span className="font-mono text-xs text-text-primary">{job.snapshotId}</span>
                  </div>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  onClick={onClose}
                  className="w-full"
                >
                  Close
                </Button>
              </div>
            )}

            {job.status === 'failed' && (
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <AlertCircle className="h-5 w-5 text-destructive" />
                  <div>
                    <h3 className="text-sm font-semibold text-text-primary">Import Failed</h3>
                    <p className="text-xs text-text-secondary">{job.message}</p>
                  </div>
                </div>
                {job.error && (
                  <div className="rounded-lg bg-destructive-bg/10 border border-destructive-border p-3">
                    <p className="text-sm text-destructive">{job.error}</p>
                  </div>
                )}
                <Button
                  type="button"
                  variant="outline"
                  onClick={handleRetry}
                  className="w-full"
                >
                  <RefreshCw className="mr-2 h-4 w-4" />
                  Retry
                </Button>
              </div>
            )}

            {job.status === 'unsupported' && (
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <AlertCircle className="h-5 w-5 text-warning-color" />
                  <div>
                    <h3 className="text-sm font-semibold text-text-primary">Unsupported Features</h3>
                    <p className="text-xs text-text-secondary">{job.message}</p>
                  </div>
                </div>
                {job.unsupportedFeatures && job.unsupportedFeatures.length > 0 && (
                  <div className="rounded-lg border border-warning-border bg-warning-bg/10 p-3">
                    <p className="mb-2 text-sm font-medium text-warning-color">Unsupported features detected:</p>
                    <ul className="list-inside list-disc space-y-1 text-sm text-text-secondary">
                      {job.unsupportedFeatures.map((feature, index) => (
                        <li key={index}>{feature}</li>
                      ))}
                    </ul>
                  </div>
                )}
                <Button
                  type="button"
                  variant="outline"
                  onClick={handleRetry}
                  className="w-full"
                >
                  <RefreshCw className="mr-2 h-4 w-4" />
                  Retry
                </Button>
              </div>
            )}
          </div>
        )}
      </div>
    </aside>
  );
}
