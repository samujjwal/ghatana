/**
 * @fileoverview Import Wizard for Page Designer
 *
 * Provides a wizard interface for importing artifacts from various sources:
 * - Paste code (semantic model JSON)
 * - Upload zip (HTTPS zip archive)
 * - Connect repo (HTTPS repository path)
 * - Import Storybook (CSF story URL)
 * - Import route (route file decompilation)
 *
 * @doc.type component
 * @doc.purpose Import wizard for page artifacts
 * @doc.layer product
 * @doc.pattern Wizard
 */

import React, { useState, useRef } from 'react';
import {
  Box,
  Stack,
  Typography,
  IconButton,
  Button,
  Surface as Paper,
  TextArea,
  Select,
  Input,
  Drawer,
  Divider,
  LinearProgress,
  Chip,
  Alert,
} from '@ghatana/design-system';
import { Upload, X, AlertTriangle, CheckCircle, FileText, AlertCircle } from 'lucide-react';
import { useTranslation } from '@ghatana/i18n';
import type { ImportSourceType, ImportConfidenceMetrics, ResidualIsland } from '../../../services/compiler/ImportSourceWorkflow';
import {
  checkArtifactCompilerRuntimeHealth,
  type ArtifactCompilerRuntimeHealth,
} from '../../../services/compiler/ArtifactCompilerRuntimeHealth';

type ImportWorkflowMode = 'semantic-model' | 'source';
type ImportReviewDecision = 'applied' | 'skipped' | 'promoted';
type WizardStep = 'input' | 'preview' | 'complete';

export interface ImportWizardTemplate {
  readonly id:
    | 'paste-code'
    | 'upload-zip'
    | 'connect-repo'
    | 'connect-github'
    | 'connect-gitlab'
    | 'connect-local-folder'
    | 'import-storybook'
    | 'import-route';
  readonly label: string;
  readonly description: string;
  readonly mode: ImportWorkflowMode;
  readonly sourceType?: ImportSourceType;
  readonly placeholder: string;
}

export type ImportWizardTemplateId = ImportWizardTemplate['id'];

export const IMPORT_WIZARD_TEMPLATES = [
  {
    id: 'paste-code',
    label: 'Paste code',
    description: 'Paste a reviewed semantic page model when you already have generated JSON.',
    mode: 'semantic-model',
    placeholder: '{"pages": [{"name": "Home", "confidence": 0.92}]}',
  },
  {
    id: 'upload-zip',
    label: 'Upload zip',
    description: 'Point to an HTTPS zip archive for governed server-side extraction.',
    mode: 'source',
    sourceType: 'zip',
    placeholder: 'https://example.com/artifacts/app-pages.zip',
  },
  {
    id: 'connect-repo',
    label: 'Connect repo',
    description: 'Use an HTTPS repository path that resolves to the route or component source.',
    mode: 'source',
    sourceType: 'route',
    placeholder: 'https://github.com/org/repo/tree/main/apps/web/src/routes',
  },
  {
    id: 'connect-github',
    label: 'Import from GitHub',
    description: 'Import a full GitHub repository and run the artifact compiler against it.',
    mode: 'source',
    sourceType: 'github',
    placeholder: 'owner/repo or https://github.com/owner/repo',
  },
  {
    id: 'connect-gitlab',
    label: 'Import from GitLab',
    description: 'Import a full GitLab repository and run the artifact compiler against it.',
    mode: 'source',
    sourceType: 'gitlab',
    placeholder: 'owner/repo or https://gitlab.com/owner/repo',
  },
  {
    id: 'connect-local-folder',
    label: 'Import local folder',
    description: 'Scan a local filesystem directory. Only available in trusted server-side or desktop contexts.',
    mode: 'source',
    sourceType: 'local-folder',
    placeholder: '/absolute/path/to/project or file:///path/to/project',
  },
  {
    id: 'import-storybook',
    label: 'Import Storybook',
    description: 'Import a CSF story URL or artifact reference through the compiler runtime.',
    mode: 'source',
    sourceType: 'storybook',
    placeholder: 'https://example.com/Button.stories.tsx#Primary',
  },
  {
    id: 'import-route',
    label: 'Import route',
    description: 'Import a route file and decompile the page structure into a builder document.',
    mode: 'source',
    sourceType: 'route',
    placeholder: 'https://example.com/routes/Home.tsx',
  },
] as const satisfies readonly ImportWizardTemplate[];

export interface ImportWizardProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly onImport: (
    input: string,
    mode: ImportWorkflowMode,
    sourceType?: ImportSourceType
  ) => Promise<{
    success: boolean;
    confidence?: ImportConfidenceMetrics;
    residuals?: ResidualIsland[];
    fileCount?: number;
    warnings?: string[];
    errors?: string[];
  }>;
  readonly artifactRuntimeHealth: ArtifactCompilerRuntimeHealth | null;
}

export const ImportWizard: React.FC<ImportWizardProps> = ({
  open,
  onClose,
  onImport,
  artifactRuntimeHealth,
}) => {
  const { t } = useTranslation('common');
  const [templateId, setTemplateId] = useState<ImportWizardTemplate['id']>('paste-code');
  const [input, setInput] = useState('');
  const [ref, setRef] = useState(''); // Branch, tag, or commit SHA for GitHub/GitLab
  const [workflowMode, setWorkflowMode] = useState<ImportWorkflowMode>('semantic-model');
  const [guidedSourceType, setGuidedSourceType] = useState<ImportSourceType>('tsx');
  const [error, setError] = useState<string | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const [step, setStep] = useState<WizardStep>('input');
  const [jobId, setJobId] = useState<string | null>(null); // Job ID for long-running imports
  const [progress, setProgress] = useState(0); // Import progress percentage
  const [previewData, setPreviewData] = useState<{
    success: boolean;
    confidence?: ImportConfidenceMetrics;
    residuals?: ResidualIsland[];
    fileCount?: number;
    skippedFiles?: Array<{ path: string; reason: string }>;
    warnings?: string[];
    errors?: string[];
  } | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const selectedTemplate = IMPORT_WIZARD_TEMPLATES.find(t => t.id === templateId) ?? IMPORT_WIZARD_TEMPLATES[0]!;

  const handleTemplateChange = (newTemplateId: ImportWizardTemplate['id']) => {
    setTemplateId(newTemplateId);
    const newTemplate = IMPORT_WIZARD_TEMPLATES.find(t => t.id === newTemplateId)!;
    setWorkflowMode(newTemplate.mode);
    if ('sourceType' in newTemplate && newTemplate.sourceType) {
      setGuidedSourceType(newTemplate.sourceType);
    }
    setInput('');
    setRef('');
    setError(null);
    setStep('input');
    setPreviewData(null);
    setJobId(null);
    setProgress(0);
  };

  const handleImport = async () => {
    if (!input.trim()) {
      setError('Please provide input for the import.');
      return;
    }

    setIsImporting(true);
    setError(null);

    try {
      const result = await onImport(
        input,
        workflowMode,
        'sourceType' in selectedTemplate ? selectedTemplate.sourceType : undefined
      );
      
      // P6.2: Show preview with confidence and residuals before final application
      setPreviewData(result);
      setStep('preview');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Import failed.';
      setError(message);
    } finally {
      setIsImporting(false);
    }
  };

  const handleApplyImport = async () => {
    // P6.2: Apply the import after review
    setStep('complete');
    onClose();
    // Reset state after close
    setTimeout(() => {
      setInput('');
      setPreviewData(null);
      setStep('input');
    }, 300);
  };

  const handleBackToInput = () => {
    setStep('input');
    setPreviewData(null);
  };

  const isRuntimeHealthy = artifactRuntimeHealth?.status === 'available';

  if (!open) {
    return null;
  }

  return (
    <Drawer open={open} onClose={onClose} anchor="right">
      <Paper padding={4} elevation={2} style={{ minWidth: 600, maxWidth: 700 }}>
        <Stack spacing={4}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="h6">
              {step === 'preview' ? 'Review Import' : 'Import Artifacts'}
            </Typography>
            <IconButton onClick={onClose} aria-label="Close import wizard">
              <X />
            </IconButton>
          </Stack>

          {!isRuntimeHealthy && (
            <Box padding={3} style={{ backgroundColor: '#fff3cd', border: '1px solid #ffc107', borderRadius: '4px' }}>
              <Stack direction="row" spacing={2} alignItems="flex-start">
                <AlertTriangle size={20} style={{ flexShrink: 0, marginTop: 2 }} />
                <Typography variant="body2" color="text.secondary">
                  Artifact compiler runtime is not healthy. Import functionality may be limited.
                </Typography>
              </Stack>
            </Box>
          )}

          {step === 'input' && (
            <>
              <Stack spacing={2}>
                <Typography variant="subtitle2">Import Source</Typography>
                <Select
                  value={templateId}
                  onChange={(e) => handleTemplateChange(e.target.value as ImportWizardTemplate['id'])}
                  options={IMPORT_WIZARD_TEMPLATES.map(t => ({
                    value: t.id,
                    label: t.label,
                  }))}
                  fullWidth
                />
                <Typography variant="body2" color="text.secondary">
                  {selectedTemplate.description}
                </Typography>
              </Stack>

              {workflowMode === 'source' && (
                <Stack spacing={2}>
                  <Typography variant="subtitle2">Source Type</Typography>
                  <Select
                    value={guidedSourceType}
                    onChange={(e) => setGuidedSourceType(e.target.value as ImportSourceType)}
                    options={[
                      { value: 'tsx', label: 'TypeScript/TSX' },
                      { value: 'zip', label: 'Zip Archive' },
                      { value: 'storybook', label: 'Storybook CSF' },
                      { value: 'route', label: 'Route File' },
                      { value: 'github', label: 'GitHub Repository' },
                      { value: 'gitlab', label: 'GitLab Repository' },
                      { value: 'local-folder', label: 'Local Folder' },
                    ]}
                    fullWidth
                  />
                </Stack>
              )}

              <Stack spacing={2}>
                <Typography variant="subtitle2">
                  {workflowMode === 'semantic-model' ? 'Paste JSON' : 'Source Locator'}
                </Typography>
                {workflowMode === 'semantic-model' ? (
                  <TextArea
                    ref={textareaRef}
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    placeholder={selectedTemplate.placeholder}
                    rows={10}
                    disabled={isImporting}
                  />
                ) : (
                  <Input
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    placeholder={selectedTemplate.placeholder}
                    disabled={isImporting}
                  />
                )}

                {/* Ref field for GitHub/GitLab (branch, tag, or commit SHA) */}
                {(guidedSourceType === 'github' || guidedSourceType === 'gitlab') && (
                  <Stack spacing={1}>
                    <Typography variant="subtitle2">Branch, Tag, or Commit SHA (Optional)</Typography>
                    <Input
                      value={ref}
                      onChange={(e) => setRef(e.target.value)}
                      placeholder="main, v1.0.0, abc123def..."
                      disabled={isImporting}
                    />
                  </Stack>
                )}
              </Stack>
            </>
          )}

          {step === 'preview' && previewData && (
            <>
              <Divider />
              
              {/* P6.2: Import Preview */}
              <Stack spacing={3}>
                <Typography variant="h6">Import Preview</Typography>
                
                {/* Confidence Metrics */}
                {previewData.confidence && (
                  <Stack spacing={2}>
                    <Typography variant="subtitle2">Confidence Metrics</Typography>
                    <Box padding={3} style={{ backgroundColor: '#f8f9fa', borderRadius: '4px' }}>
                      <Stack spacing={2}>
                        <Stack direction="row" justifyContent="space-between" alignItems="center">
                          <Typography variant="body2">Overall Confidence</Typography>
                          <Chip
                            label={`${(previewData.confidence.overallConfidence * 100).toFixed(0)}%`}
                            color={previewData.confidence.overallConfidence >= 0.8 ? 'success' : previewData.confidence.overallConfidence >= 0.5 ? 'warning' : 'error'}
                          />
                        </Stack>
                        <LinearProgress
                          variant="determinate"
                          value={previewData.confidence.overallConfidence * 100}
                          color={previewData.confidence.overallConfidence >= 0.8 ? 'success' : previewData.confidence.overallConfidence >= 0.5 ? 'warning' : 'error'}
                        />
                        <Stack direction="row" spacing={3}>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <CheckCircle size={16} color="#28a745" />
                            <Typography variant="caption">{previewData.confidence.highConfidenceCount} High</Typography>
                          </Stack>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <AlertCircle size={16} color="#ffc107" />
                            <Typography variant="caption">{previewData.confidence.mediumConfidenceCount} Medium</Typography>
                          </Stack>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <AlertTriangle size={16} color="#dc3545" />
                            <Typography variant="caption">{previewData.confidence.lowConfidenceCount} Low</Typography>
                          </Stack>
                        </Stack>
                      </Stack>
                    </Box>
                  </Stack>
                )}

                {/* Residual Islands */}
                {previewData.residuals && previewData.residuals.length > 0 && (
                  <Stack spacing={2}>
                    <Typography variant="subtitle2">Residual Islands (Requires Review)</Typography>
                    <Alert severity="warning">
                      <Typography variant="body2">
                        {previewData.residuals.length} area{previewData.residuals.length !== 1 ? 's' : ''} could not be automatically modeled and require manual review.
                      </Typography>
                    </Alert>
                    <Box style={{ maxHeight: 200, overflowY: 'auto' }}>
                      {previewData.residuals.map((residual) => (
                        <Box
                          key={residual.id}
                          padding={2}
                          marginBottom={1}
                          style={{ backgroundColor: '#fff3cd', border: '1px solid #ffc107', borderRadius: '4px' }}
                        >
                          <Stack spacing={1}>
                            <Stack direction="row" spacing={1} alignItems="center">
                              <FileText size={14} />
                              <Typography variant="caption" fontWeight="bold">
                                {residual.sourcePath}
                              </Typography>
                            </Stack>
                            <Typography variant="caption" color="text.secondary">
                              {residual.description}
                            </Typography>
                            <Stack direction="row" spacing={2}>
                              <Chip label={residual.type} size="small" variant="outlined" />
                              <Chip
                                label={`Confidence: ${(residual.confidence * 100).toFixed(0)}%`}
                                size="small"
                                color={residual.confidence >= 0.5 ? 'success' : 'error'}
                              />
                            </Stack>
                          </Stack>
                        </Box>
                      ))}
                    </Box>
                  </Stack>
                )}

                {/* File Count */}
                {previewData.fileCount !== undefined && (
                  <Stack spacing={1}>
                    <Typography variant="subtitle2">Files to Import</Typography>
                    <Typography variant="body2">{previewData.fileCount} file(s)</Typography>
                  </Stack>
                )}

                {/* Skipped Files */}
                {previewData.skippedFiles && previewData.skippedFiles.length > 0 && (
                  <Stack spacing={2}>
                    <Typography variant="subtitle2">Skipped Files</Typography>
                    <Alert severity="info">
                      <Typography variant="body2">
                        {previewData.skippedFiles.length} file{previewData.skippedFiles.length !== 1 ? 's were' : ' was'} skipped during import.
                      </Typography>
                    </Alert>
                    <Box style={{ maxHeight: 200, overflowY: 'auto' }}>
                      {previewData.skippedFiles.map((skipped, idx) => (
                        <Box
                          key={idx}
                          padding={2}
                          marginBottom={1}
                          style={{ backgroundColor: '#e7f3ff', border: '1px solid #90caf9', borderRadius: '4px' }}
                        >
                          <Stack spacing={1}>
                            <Typography variant="caption" fontWeight="bold">
                              {skipped.path}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              Reason: {skipped.reason}
                            </Typography>
                          </Stack>
                        </Box>
                      ))}
                    </Box>
                  </Stack>
                )}

                {/* Warnings */}
                {previewData.warnings && previewData.warnings.length > 0 && (
                  <Stack spacing={2}>
                    <Typography variant="subtitle2">Warnings</Typography>
                    {previewData.warnings.map((warning, idx) => (
                      <Alert key={idx} severity="warning">
                        <Typography variant="body2">{warning}</Typography>
                      </Alert>
                    ))}
                  </Stack>
                )}

                {/* Errors */}
                {previewData.errors && previewData.errors.length > 0 && (
                  <Stack spacing={2}>
                    <Typography variant="subtitle2">Errors</Typography>
                    {previewData.errors.map((err, idx) => (
                      <Alert key={idx} severity="error">
                        <Typography variant="body2">{err}</Typography>
                      </Alert>
                    ))}
                  </Stack>
                )}
              </Stack>
            </>
          )}

          {error && (
            <Box padding={2} style={{ backgroundColor: '#f8d7da', border: '1px solid #f5c6cb', borderRadius: '4px' }}>
              <Typography variant="body2" color="error">
                {error}
              </Typography>
            </Box>
          )}

          <Stack direction="row" spacing={2} justifyContent="flex-end">
            {step === 'preview' && (
              <Button variant="outlined" onClick={handleBackToInput} disabled={isImporting}>
                Back
              </Button>
            )}
            <Button variant="outlined" onClick={onClose} disabled={isImporting}>
              Cancel
            </Button>
            {step === 'input' ? (
              <Button variant="contained" onClick={handleImport} disabled={isImporting || !input.trim()}>
                {isImporting ? 'Importing...' : 'Preview'}
              </Button>
            ) : (
              <Button
                variant="contained"
                onClick={handleApplyImport}
                disabled={isImporting || !previewData?.success}
              >
                Apply Import
              </Button>
            )}
          </Stack>
        </Stack>
      </Paper>
    </Drawer>
  );
};
