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
} from '@ghatana/design-system';
import { Upload, X, AlertTriangle } from 'lucide-react';
import { useTranslation } from '@ghatana/i18n';
import type { ImportSourceType } from '../../../services/compiler/ImportSourceWorkflow';
import {
  checkArtifactCompilerRuntimeHealth,
  type ArtifactCompilerRuntimeHealth,
} from '../../../services/compiler/ArtifactCompilerRuntimeHealth';

type ImportWorkflowMode = 'semantic-model' | 'source';
type ImportReviewDecision = 'applied' | 'skipped' | 'promoted';

export interface ImportWizardTemplate {
  readonly id: 'paste-code' | 'upload-zip' | 'connect-repo' | 'import-storybook' | 'import-route';
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
  ) => Promise<void>;
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
  const [workflowMode, setWorkflowMode] = useState<ImportWorkflowMode>('semantic-model');
  const [guidedSourceType, setGuidedSourceType] = useState<ImportSourceType>('tsx');
  const [error, setError] = useState<string | null>(null);
  const [isImporting, setIsImporting] = useState(false);
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
    setError(null);
  };

  const handleImport = async () => {
    if (!input.trim()) {
      setError('Please provide input for the import.');
      return;
    }

    setIsImporting(true);
    setError(null);

    try {
      await onImport(
        input,
        workflowMode,
        'sourceType' in selectedTemplate ? selectedTemplate.sourceType : undefined
      );
      setInput('');
      onClose();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Import failed.';
      setError(message);
    } finally {
      setIsImporting(false);
    }
  };

  const isRuntimeHealthy = artifactRuntimeHealth?.status === 'available';

  if (!open) {
    return null;
  }

  return (
    <Drawer open={open} onClose={onClose} anchor="right">
      <Paper padding={4} elevation={2} style={{ minWidth: 600, maxWidth: 600 }}>
        <Stack spacing={4}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="h6">Import Artifacts</Typography>
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
          </Stack>

          {error && (
            <Box padding={2} style={{ backgroundColor: '#f8d7da', border: '1px solid #f5c6cb', borderRadius: '4px' }}>
              <Typography variant="body2" color="error">
                {error}
              </Typography>
            </Box>
          )}

          <Stack direction="row" spacing={2} justifyContent="flex-end">
            <Button variant="outlined" onClick={onClose} disabled={isImporting}>
              Cancel
            </Button>
            <Button variant="contained" onClick={handleImport} disabled={isImporting || !input.trim()}>
              {isImporting ? 'Importing...' : 'Import'}
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Drawer>
  );
};
