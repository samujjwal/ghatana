/**
 * ImportWizard Module
 *
 * @doc.type component
 * @doc.purpose Import wizard for importing semantic models and source code
 * @doc.layer product
 * @doc.pattern Widget
 */

import React, { useState, useCallback, useRef } from 'react';
import { TextArea } from '@ghatana/design-system';
import { Upload, X } from 'lucide-react';
import { Select } from '../../ui/Select';
import { Button } from '@ghatana/design-system';
import { Input } from '../../ui/Input';
import type { ImportSourceType } from '../../../../services/compiler/ImportSourceWorkflow';

type ImportWorkflowMode = 'semantic-model' | 'source';

export interface ImportWizardTemplate {
  readonly id: 'paste-code' | 'upload-zip' | 'connect-repo' | 'import-storybook' | 'import-route';
  readonly label: string;
  readonly description: string;
  readonly mode: ImportWorkflowMode;
  readonly sourceType?: ImportSourceType;
  readonly placeholder: string;
}

export const IMPORT_WIZARD_TEMPLATES: readonly ImportWizardTemplate[] = [
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
] as const;

export interface ImportWizardProps {
  readonly isOpen: boolean;
  readonly onClose: () => void;
  readonly onConfirm: () => Promise<void>;
  readonly error: string | null;
  readonly onTemplateSelect: (template: ImportWizardTemplate) => void;
  readonly selectedTemplate: ImportWizardTemplate;
}

export function ImportWizard({
  isOpen,
  onClose,
  onConfirm,
  error,
  onTemplateSelect,
  selectedTemplate,
}: ImportWizardProps): React.JSX.Element | null {
  const [input, setInput] = useState('');
  const [sourceType, setSourceType] = useState<ImportSourceType>('tsx');
  const [sourceLocator, setSourceLocator] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleTemplateChange = useCallback(
    (templateId: string) => {
      const template = IMPORT_WIZARD_TEMPLATES.find((t) => t.id === templateId);
      if (template) {
        onTemplateSelect(template);
        setInput('');
        setSourceLocator('');
        if (template.sourceType) {
          setSourceType(template.sourceType);
        }
      }
    },
    [onTemplateSelect]
  );

  const handleConfirm = useCallback(async () => {
    await onConfirm();
  }, [onConfirm]);

  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-full max-w-2xl rounded-lg bg-white p-6 shadow-lg dark:bg-gray-900">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Import Page Artifacts</h2>
          <button
            onClick={onClose}
            className="rounded p-2 hover:bg-gray-100 dark:hover:bg-gray-800"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="mb-4">
          <label className="mb-2 block text-sm font-medium">Import Method</label>
          <Select
            value={selectedTemplate.id}
            onValueChange={handleTemplateChange}
            options={IMPORT_WIZARD_TEMPLATES.map((t) => ({ value: t.id, label: t.label }))}
          />
          <p className="mt-1 text-sm text-gray-500">{selectedTemplate.description}</p>
        </div>

        {selectedTemplate.mode === 'semantic-model' ? (
          <div className="mb-4">
            <label className="mb-2 block text-sm font-medium">Semantic Model JSON</label>
            <TextArea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={selectedTemplate.placeholder}
              rows={10}
              className="w-full rounded border p-2 font-mono text-sm"
            />
          </div>
        ) : (
          <div className="mb-4 space-y-4">
            <div>
              <label className="mb-2 block text-sm font-medium">Source Type</label>
              <Select
                value={sourceType}
                onValueChange={(value) => setSourceType(value as ImportSourceType)}
                options={[
                  { value: 'zip', label: 'ZIP Archive' },
                  { value: 'route', label: 'Route File' },
                  { value: 'storybook', label: 'Storybook Story' },
                ]}
              />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium">Source Locator</label>
              <Input
                value={sourceLocator}
                onChange={(e) => setSourceLocator(e.target.value)}
                placeholder={selectedTemplate.placeholder}
                className="w-full"
              />
            </div>
          </div>
        )}

        {error && (
          <div className="mb-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/20 dark:text-red-200">
            {error}
          </div>
        )}

        <div className="flex justify-end gap-3">
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleConfirm}
            className="flex items-center gap-2"
          >
            <Upload className="h-4 w-4" />
            Import
          </Button>
        </div>
      </div>
    </div>
  );
}
