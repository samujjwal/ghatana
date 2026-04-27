/**
 * ExportDialog
 *
 * Allows users to export project artifacts (requirements, diagrams, code) in
 * a chosen format. Calls the `createExport` GraphQL mutation and polls the
 * artifact status until ready, then opens the download URL.
 *
 * @doc.type component
 * @doc.purpose Project artifact export with format selection and status polling
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Typography,
} from '@ghatana/design-system';
import { Download, FileText, Archive, Image, X, CheckCircle2, Loader2 } from 'lucide-react';
import { cn } from '../../utils/cn';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type ExportFormat = 'JSON' | 'MARKDOWN' | 'PDF' | 'ZIP' | 'HTML';
export type ExportStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';

export interface ExportArtifact {
  id: string;
  projectId: string;
  format: ExportFormat;
  status: ExportStatus;
  includeRequirements: boolean;
  includeDiagrams: boolean;
  includeCode: boolean;
  downloadUrl?: string;
  errorMessage?: string;
  createdAt: string;
  completedAt?: string;
}

export interface ExportDialogProps {
  projectId: string;
  projectName: string;
  /** Called when the dialog should close */
  onClose: () => void;
  /** Triggered when user requests an export; implementation submits the GraphQL mutation */
  onCreateExport: (params: {
    projectId: string;
    format: ExportFormat;
    includeRequirements: boolean;
    includeDiagrams: boolean;
    includeCode: boolean;
  }) => Promise<ExportArtifact>;
  /** Optional list of past export artifacts for the project */
  pastExports?: ExportArtifact[];
  className?: string;
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const FORMAT_OPTIONS: Array<{
  value: ExportFormat;
  label: string;
  description: string;
  icon: React.ReactNode;
}> = [
  {
    value: 'MARKDOWN',
    label: 'Markdown',
    description: 'Requirements, stories, and docs as .md files',
    icon: <FileText className="h-4 w-4" />,
  },
  {
    value: 'JSON',
    label: 'JSON',
    description: 'Full project data as structured JSON',
    icon: <FileText className="h-4 w-4" />,
  },
  {
    value: 'HTML',
    label: 'HTML',
    description: 'Rendered pages and diagrams as static HTML',
    icon: <Image className="h-4 w-4" />,
  },
  {
    value: 'ZIP',
    label: 'ZIP Bundle',
    description: 'All artifacts bundled in a single archive',
    icon: <Archive className="h-4 w-4" />,
  },
];

const STATUS_LABEL: Record<ExportStatus, string> = {
  PENDING: 'Queued',
  PROCESSING: 'Processing…',
  READY: 'Ready',
  FAILED: 'Failed',
};

const STATUS_COLOR: Record<ExportStatus, string> = {
  PENDING: 'bg-slate-100 text-slate-700',
  PROCESSING: 'bg-blue-100 text-blue-700',
  READY: 'bg-emerald-100 text-emerald-700',
  FAILED: 'bg-red-100 text-red-700',
};

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export const ExportDialog: React.FC<ExportDialogProps> = ({
  projectId,
  projectName,
  onClose,
  onCreateExport,
  pastExports = [],
  className = '',
}) => {
  const [selectedFormat, setSelectedFormat] = useState<ExportFormat>('MARKDOWN');
  const [includeRequirements, setIncludeRequirements] = useState(true);
  const [includeDiagrams, setIncludeDiagrams] = useState(true);
  const [includeCode, setIncludeCode] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [latestExport, setLatestExport] = useState<ExportArtifact | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleExport = useCallback(async () => {
    setIsSubmitting(true);
    setError(null);
    try {
      const artifact = await onCreateExport({
        projectId,
        format: selectedFormat,
        includeRequirements,
        includeDiagrams,
        includeCode,
      });
      setLatestExport(artifact);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Export failed. Please try again.';
      setError(message);
    } finally {
      setIsSubmitting(false);
    }
  }, [
    projectId,
    selectedFormat,
    includeRequirements,
    includeDiagrams,
    includeCode,
    onCreateExport,
  ]);

  const handleDownload = useCallback((url: string) => {
    window.open(url, '_blank', 'noopener,noreferrer');
  }, []);

  return (
    <Box
      role="dialog"
      aria-modal="true"
      aria-label={`Export ${projectName}`}
      className={cn(
        'fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4',
        className
      )}
    >
      <Card className="w-full max-w-lg shadow-xl">
        <CardContent className="space-y-5 p-6">
          {/* Header */}
          <Box className="flex items-center justify-between">
            <Typography className="text-lg font-semibold">
              Export &ldquo;{projectName}&rdquo;
            </Typography>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Close export dialog"
              onClick={onClose}
            >
              <X className="h-4 w-4" />
            </Button>
          </Box>

          {/* Format selection */}
          <Box className="space-y-2">
            <Typography className="text-sm font-medium text-gray-700">
              Format
            </Typography>
            <Box className="grid grid-cols-2 gap-2">
              {FORMAT_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  aria-pressed={selectedFormat === opt.value}
                  onClick={() => setSelectedFormat(opt.value)}
                  className={cn(
                    'flex items-start gap-2 rounded-md border p-3 text-left text-sm transition-colors',
                    selectedFormat === opt.value
                      ? 'border-blue-500 bg-blue-50 text-blue-700'
                      : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
                  )}
                >
                  <span className="mt-0.5">{opt.icon}</span>
                  <Box>
                    <Typography className="font-medium leading-tight">
                      {opt.label}
                    </Typography>
                    <Typography className="text-xs text-gray-500 leading-tight">
                      {opt.description}
                    </Typography>
                  </Box>
                </button>
              ))}
            </Box>
          </Box>

          {/* Content toggles */}
          <Box className="space-y-2">
            <Typography className="text-sm font-medium text-gray-700">
              Include
            </Typography>
            <Box className="flex flex-wrap gap-2">
              {(
                [
                  { key: 'requirements', label: 'Requirements', value: includeRequirements, set: setIncludeRequirements },
                  { key: 'diagrams', label: 'Diagrams', value: includeDiagrams, set: setIncludeDiagrams },
                  { key: 'code', label: 'Generated Code', value: includeCode, set: setIncludeCode },
                ] as const
              ).map((item) => (
                <button
                  key={item.key}
                  type="button"
                  aria-pressed={item.value}
                  onClick={() => item.set(!item.value)}
                  className={cn(
                    'rounded-full border px-3 py-1 text-sm transition-colors',
                    item.value
                      ? 'border-blue-500 bg-blue-100 text-blue-700'
                      : 'border-gray-200 text-gray-600 hover:border-gray-300'
                  )}
                >
                  {item.label}
                </button>
              ))}
            </Box>
          </Box>

          {/* Error state */}
          {error && (
            <Box className="rounded-md bg-red-50 p-3 text-sm text-red-700">
              {error}
            </Box>
          )}

          {/* Latest export result */}
          {latestExport && (
            <Box className="rounded-md border border-gray-200 p-3 space-y-2">
              <Box className="flex items-center justify-between">
                <Typography className="text-sm font-medium">
                  Export {latestExport.format}
                </Typography>
                <Chip
                  label={STATUS_LABEL[latestExport.status]}
                  size="sm"
                  className={STATUS_COLOR[latestExport.status]}
                />
              </Box>
              {latestExport.status === 'PROCESSING' && (
                <Box className="flex items-center gap-2 text-sm text-blue-600">
                  <Loader2 className="h-3 w-3 animate-spin" />
                  <span>Generating your export…</span>
                </Box>
              )}
              {latestExport.status === 'READY' && latestExport.downloadUrl && (
                <Box className="flex items-center gap-2">
                  <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleDownload(latestExport.downloadUrl!)}
                  >
                    <Download className="mr-1 h-3 w-3" />
                    Download
                  </Button>
                </Box>
              )}
              {latestExport.status === 'FAILED' && (
                <Typography className="text-sm text-red-600">
                  {latestExport.errorMessage ?? 'Export failed. Try again.'}
                </Typography>
              )}
            </Box>
          )}

          {/* Past exports */}
          {pastExports.length > 0 && !latestExport && (
            <Box className="space-y-2">
              <Typography className="text-xs font-medium text-gray-500 uppercase tracking-wide">
                Previous Exports
              </Typography>
              <Box className="space-y-1 max-h-40 overflow-y-auto">
                {pastExports.map((exp) => (
                  <Box
                    key={exp.id}
                    className="flex items-center justify-between rounded border border-gray-100 px-3 py-2 text-sm"
                  >
                    <Box className="flex items-center gap-2">
                      <Typography className="font-medium">{exp.format}</Typography>
                      <Typography className="text-xs text-gray-400">
                        {formatTimestamp(exp.createdAt)}
                      </Typography>
                    </Box>
                    <Box className="flex items-center gap-2">
                      <Chip
                        label={STATUS_LABEL[exp.status]}
                        size="sm"
                        className={STATUS_COLOR[exp.status]}
                      />
                      {exp.status === 'READY' && exp.downloadUrl && (
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label="Download export"
                          onClick={() => handleDownload(exp.downloadUrl!)}
                        >
                          <Download className="h-3 w-3" />
                        </Button>
                      )}
                    </Box>
                  </Box>
                ))}
              </Box>
            </Box>
          )}

          {/* Actions */}
          <Box className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button
              onClick={handleExport}
              disabled={isSubmitting}
              aria-busy={isSubmitting}
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Exporting…
                </>
              ) : (
                <>
                  <Download className="mr-2 h-4 w-4" />
                  Export
                </>
              )}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

export default ExportDialog;
