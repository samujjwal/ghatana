/**
 * Canvas Export Dialog Component
 *
 * @description A dialog for exporting canvas content in various formats.
 * Supports PNG, SVG, JSON, PDF exports with quality and size options.
 *
 * @doc.type component
 * @doc.purpose Export canvas artifacts
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Download,
  Image,
  FileJson,
  FileText,
  FileCode2,
  Loader2,
  CheckCircle2,
  AlertCircle,
  Settings2,
  ZoomIn,
  ZoomOut,
  Maximize2,
  Copy,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Dialog } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import { Input } from '@ghatana/ui';
import { Progress } from '@ghatana/ui';
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuCheckboxItem,
  DropdownMenuSeparator,
} from '@ghatana/yappc-ui';

// =============================================================================
// Types
// =============================================================================

export type ExportFormat = 'png' | 'svg' | 'json' | 'pdf';

export type ExportQuality = 'low' | 'medium' | 'high' | 'maximum';

export type ExportStatus = 'idle' | 'preparing' | 'exporting' | 'success' | 'error';

export interface ExportOptions {
  /** Export format */
  format: ExportFormat;
  /** Quality setting */
  quality: ExportQuality;
  /** Include background */
  includeBackground: boolean;
  /** Scale factor */
  scale: number;
  /** Custom width */
  width?: number;
  /** Custom height */
  height?: number;
  /** Include metadata */
  includeMetadata: boolean;
  /** Preserve aspect ratio */
  preserveAspectRatio: boolean;
  /** File name */
  fileName: string;
}

export interface CanvasExportDialogProps {
  /** Whether dialog is open */
  open: boolean;
  /** Called when open state changes */
  onOpenChange: (open: boolean) => void;
  /** Called when export is requested */
  onExport: (options: ExportOptions) => Promise<Blob | string>;
  /** Default file name */
  defaultFileName?: string;
  /** Preview image URL */
  previewUrl?: string;
  /** Canvas dimensions */
  canvasDimensions?: { width: number; height: number };
  /** Available formats */
  availableFormats?: ExportFormat[];
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Constants
// =============================================================================

const FORMAT_CONFIG: Record<
  ExportFormat,
  {
    icon: React.ElementType;
    label: string;
    description: string;
    extension: string;
    mimeType: string;
  }
> = {
  png: {
    icon: Image,
    label: 'PNG Image',
    description: 'High-quality raster image',
    extension: '.png',
    mimeType: 'image/png',
  },
  svg: {
    icon: FileCode2,
    label: 'SVG Vector',
    description: 'Scalable vector graphics',
    extension: '.svg',
    mimeType: 'image/svg+xml',
  },
  json: {
    icon: FileJson,
    label: 'JSON Data',
    description: 'Structured canvas data',
    extension: '.json',
    mimeType: 'application/json',
  },
  pdf: {
    icon: FileText,
    label: 'PDF Document',
    description: 'Printable document',
    extension: '.pdf',
    mimeType: 'application/pdf',
  },
};

const QUALITY_CONFIG: Record<
  ExportQuality,
  { label: string; multiplier: number; description: string }
> = {
  low: { label: 'Low', multiplier: 0.5, description: '50% - Smaller file' },
  medium: { label: 'Medium', multiplier: 1, description: '100% - Balanced' },
  high: { label: 'High', multiplier: 2, description: '200% - Sharp' },
  maximum: { label: 'Maximum', multiplier: 4, description: '400% - Print quality' },
};

const SCALE_OPTIONS = [0.5, 0.75, 1, 1.5, 2, 3, 4];

// =============================================================================
// Format Card
// =============================================================================

const FormatCard: React.FC<{
  format: ExportFormat;
  selected: boolean;
  onSelect: () => void;
}> = ({ format, selected, onSelect }) => {
  const config = FORMAT_CONFIG[format];
  const Icon = config.icon;

  return (
    <motion.button
      type="button"
      onClick={onSelect}
      whileHover={{ scale: 1.02 }}
      whileTap={{ scale: 0.98 }}
      className={cn(
        'relative flex flex-col items-center gap-2 rounded-xl border-2 p-4 transition-colors',
        selected
          ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
          : 'border-neutral-200 hover:border-neutral-300 dark:border-neutral-700 dark:hover:border-neutral-600'
      )}
    >
      <Icon
        className={cn(
          'h-8 w-8',
          selected ? 'text-primary-600' : 'text-neutral-500'
        )}
      />
      <span
        className={cn(
          'text-sm font-medium',
          selected ? 'text-primary-700 dark:text-primary-300' : 'text-neutral-700 dark:text-neutral-300'
        )}
      >
        {config.label}
      </span>
      <span className="text-xs text-neutral-500">{config.description}</span>

      {selected && (
        <motion.div
          layoutId="format-indicator"
          className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-primary-500 text-white"
        >
          <CheckCircle2 className="h-3 w-3" />
        </motion.div>
      )}
    </motion.button>
  );
};

// =============================================================================
// Preview Panel
// =============================================================================

const PreviewPanel: React.FC<{
  previewUrl?: string;
  scale: number;
  dimensions?: { width: number; height: number };
  onZoomIn: () => void;
  onZoomOut: () => void;
  onReset: () => void;
}> = ({ previewUrl, scale, dimensions, onZoomIn, onZoomOut, onReset }) => {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
          Preview
        </span>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            onClick={onZoomOut}
            disabled={scale <= 0.5}
          >
            <ZoomOut className="h-4 w-4" />
          </Button>
          <span className="w-14 text-center text-xs text-neutral-500">
            {Math.round(scale * 100)}%
          </span>
          <Button
            variant="ghost"
            size="sm"
            onClick={onZoomIn}
            disabled={scale >= 4}
          >
            <ZoomIn className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="sm" onClick={onReset}>
            <Maximize2 className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div className="relative h-48 overflow-hidden rounded-lg border border-neutral-200 bg-neutral-100 dark:border-neutral-700 dark:bg-neutral-800">
        {previewUrl ? (
          <motion.img
            src={previewUrl}
            alt="Canvas preview"
            animate={{ scale }}
            transition={{ type: 'spring', stiffness: 300, damping: 30 }}
            className="h-full w-full object-contain"
          />
        ) : (
          <div className="flex h-full items-center justify-center">
            <div className="text-center text-sm text-neutral-500">
              <Image className="mx-auto mb-2 h-8 w-8 opacity-50" />
              <p>No preview available</p>
            </div>
          </div>
        )}
      </div>

      {dimensions && (
        <div className="flex items-center justify-center gap-4 text-xs text-neutral-500">
          <span>
            Original: {dimensions.width} × {dimensions.height}px
          </span>
          <span>
            Export: {Math.round(dimensions.width * scale)} × {Math.round(dimensions.height * scale)}px
          </span>
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const CanvasExportDialog: React.FC<CanvasExportDialogProps> = ({
  open,
  onOpenChange,
  onExport,
  defaultFileName = 'canvas-export',
  previewUrl,
  canvasDimensions,
  availableFormats = ['png', 'svg', 'json', 'pdf'],
  className,
}) => {
  // State
  const [options, setOptions] = useState<ExportOptions>({
    format: 'png',
    quality: 'high',
    includeBackground: true,
    scale: 1,
    includeMetadata: true,
    preserveAspectRatio: true,
    fileName: defaultFileName,
  });
  const [status, setStatus] = useState<ExportStatus>('idle');
  const [progress, setProgress] = useState(0);
  const [errorMessage, setErrorMessage] = useState('');
  const [previewScale, setPreviewScale] = useState(1);
  const [showAdvanced, setShowAdvanced] = useState(false);

  // Update option
  const updateOption = useCallback(<K extends keyof ExportOptions>(
    key: K,
    value: ExportOptions[K]
  ) => {
    setOptions((prev) => ({ ...prev, [key]: value }));
  }, []);

  // Handle export
  const handleExport = useCallback(async () => {
    setStatus('preparing');
    setProgress(0);
    setErrorMessage('');

    try {
      // Simulate preparation
      await new Promise((resolve) => setTimeout(resolve, 500));
      setProgress(20);
      setStatus('exporting');

      // Perform export
      const result = await onExport(options);
      setProgress(80);

      // Create download
      const config = FORMAT_CONFIG[options.format];
      const fileName = `${options.fileName}${config.extension}`;

      if (typeof result === 'string') {
        // Handle string result (e.g., JSON)
        const blob = new Blob([result], { type: config.mimeType });
        downloadBlob(blob, fileName);
      } else {
        // Handle blob result
        downloadBlob(result, fileName);
      }

      setProgress(100);
      setStatus('success');

      // Auto close after success
      setTimeout(() => {
        onOpenChange(false);
        setStatus('idle');
        setProgress(0);
      }, 1500);
    } catch (error) {
      setStatus('error');
      setErrorMessage(error instanceof Error ? error.message : 'Export failed');
    }
  }, [options, onExport, onOpenChange]);

  // Download helper
  const downloadBlob = (blob: Blob, fileName: string) => {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  // Copy to clipboard
  const handleCopyToClipboard = useCallback(async () => {
    if (options.format !== 'json') return;

    try {
      const result = await onExport(options);
      const text = typeof result === 'string' ? result : await result.text();
      await navigator.clipboard.writeText(text);
    } catch {
      // Error handled by status
    }
  }, [options, onExport]);

  // Estimated file size
  const estimatedSize = canvasDimensions
    ? calculateEstimatedSize(options, canvasDimensions)
    : null;

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      header="Export Canvas"
      actions={
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          {options.format === 'json' && (
            <Button
              variant="outline"
              onClick={handleCopyToClipboard}
              disabled={status !== 'idle'}
            >
              <Copy className="mr-2 h-4 w-4" />
              Copy
            </Button>
          )}
          <Button
            variant="solid"
            colorScheme="primary"
            onClick={handleExport}
            disabled={status !== 'idle' && status !== 'error'}
          >
            {status === 'preparing' || status === 'exporting' ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Exporting...
              </>
            ) : status === 'success' ? (
              <>
                <CheckCircle2 className="mr-2 h-4 w-4" />
                Done!
              </>
            ) : (
              <>
                <Download className="mr-2 h-4 w-4" />
                Export
              </>
            )}
          </Button>
        </div>
      }
    >
      <div className={cn('flex flex-col gap-6', className)}>
        {/* Progress */}
        <AnimatePresence>
          {(status === 'preparing' || status === 'exporting') && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="overflow-hidden"
            >
              <div className="flex flex-col gap-2 rounded-lg bg-primary-50 p-4 dark:bg-primary-900/20">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-primary-700 dark:text-primary-300">
                    {status === 'preparing' ? 'Preparing export...' : 'Generating file...'}
                  </span>
                  <span className="text-sm text-primary-600">{progress}%</span>
                </div>
                <Progress value={progress} className="h-2" />
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Error */}
        <AnimatePresence>
          {status === 'error' && errorMessage && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="flex items-center gap-2 rounded-lg bg-error-100 p-3 text-error-700 dark:bg-error-900/30 dark:text-error-300"
            >
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span className="text-sm">{errorMessage}</span>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Format Selection */}
        <div className="flex flex-col gap-3">
          <span className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
            Format
          </span>
          <div className="grid grid-cols-4 gap-3">
            {availableFormats.map((format) => (
              <FormatCard
                key={format}
                format={format}
                selected={options.format === format}
                onSelect={() => updateOption('format', format)}
              />
            ))}
          </div>
        </div>

        {/* Preview */}
        <PreviewPanel
          previewUrl={previewUrl}
          scale={previewScale}
          dimensions={canvasDimensions}
          onZoomIn={() => setPreviewScale((s) => Math.min(s + 0.25, 4))}
          onZoomOut={() => setPreviewScale((s) => Math.max(s - 0.25, 0.25))}
          onReset={() => setPreviewScale(1)}
        />

        {/* File Name */}
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
            File Name
          </label>
          <div className="flex items-center gap-2">
            <Input
              value={options.fileName}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => updateOption('fileName', e.target.value)}
              placeholder="Enter file name"
              className="flex-1"
            />
            <Badge variant="outline">{FORMAT_CONFIG[options.format].extension}</Badge>
          </div>
        </div>

        {/* Quality (for raster formats) */}
        {(options.format === 'png' || options.format === 'pdf') && (
          <div className="flex flex-col gap-2">
            <label className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
              Quality
            </label>
            <div className="flex gap-2">
              {Object.entries(QUALITY_CONFIG).map(([key, config]) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => updateOption('quality', key as ExportQuality)}
                  className={cn(
                    'flex-1 rounded-lg border-2 p-2 text-center transition-colors',
                    options.quality === key
                      ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                      : 'border-neutral-200 hover:border-neutral-300 dark:border-neutral-700'
                  )}
                >
                  <span className="block text-sm font-medium">{config.label}</span>
                  <span className="block text-xs text-neutral-500">{config.description}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Advanced Options */}
        <div className="flex flex-col gap-3">
          <button
            type="button"
            onClick={() => setShowAdvanced(!showAdvanced)}
            className="flex items-center gap-2 text-sm text-neutral-500 hover:text-neutral-700"
          >
            <Settings2 className="h-4 w-4" />
            Advanced Options
            <motion.span
              animate={{ rotate: showAdvanced ? 180 : 0 }}
              className="text-xs"
            >
              ▼
            </motion.span>
          </button>

          <AnimatePresence>
            {showAdvanced && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                className="overflow-hidden"
              >
                <div className="flex flex-col gap-4 rounded-lg border border-neutral-200 p-4 dark:border-neutral-700">
                  {/* Scale */}
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-neutral-700 dark:text-neutral-300">
                      Scale
                    </span>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="outline" size="sm">
                          {options.scale}x
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent>
                        {SCALE_OPTIONS.map((scale) => (
                          <DropdownMenuCheckboxItem
                            key={scale}
                            checked={options.scale === scale}
                            onCheckedChange={() => updateOption('scale', scale)}
                          >
                            {scale}x
                          </DropdownMenuCheckboxItem>
                        ))}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>

                  <DropdownMenuSeparator />

                  {/* Toggles */}
                  <ToggleOption
                    label="Include Background"
                    description="Export with canvas background"
                    checked={options.includeBackground}
                    onChange={(checked) => updateOption('includeBackground', checked)}
                  />

                  <ToggleOption
                    label="Include Metadata"
                    description="Add export date, dimensions, etc."
                    checked={options.includeMetadata}
                    onChange={(checked) => updateOption('includeMetadata', checked)}
                  />

                  <ToggleOption
                    label="Preserve Aspect Ratio"
                    description="Maintain original proportions"
                    checked={options.preserveAspectRatio}
                    onChange={(checked) => updateOption('preserveAspectRatio', checked)}
                  />
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Estimated Size */}
        {estimatedSize && (
          <div className="flex items-center justify-between rounded-lg bg-neutral-100 p-3 text-sm dark:bg-neutral-800">
            <span className="text-neutral-600 dark:text-neutral-400">
              Estimated file size:
            </span>
            <Badge variant="outline">{formatFileSize(estimatedSize)}</Badge>
          </div>
        )}
      </div>
    </Dialog>
  );
};

// =============================================================================
// Helper Components
// =============================================================================

const ToggleOption: React.FC<{
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}> = ({ label, description, checked, onChange }) => (
  <label className="flex cursor-pointer items-center justify-between">
    <div>
      <span className="block text-sm font-medium text-neutral-700 dark:text-neutral-300">
        {label}
      </span>
      <span className="text-xs text-neutral-500">{description}</span>
    </div>
    <input
      type="checkbox"
      checked={checked}
      onChange={(e) => onChange(e.target.checked)}
      className="h-4 w-4 rounded border-neutral-300 text-primary-600 focus:ring-primary-500"
    />
  </label>
);

// =============================================================================
// Utilities
// =============================================================================

function calculateEstimatedSize(
  options: ExportOptions,
  dimensions: { width: number; height: number }
): number {
  const { width, height } = dimensions;
  const qualityMultiplier = QUALITY_CONFIG[options.quality].multiplier;
  const pixels = width * height * options.scale * options.scale;

  switch (options.format) {
    case 'png':
      // Rough estimate: 4 bytes per pixel * compression ratio
      return pixels * 4 * qualityMultiplier * 0.3;
    case 'svg':
      // SVG is text-based, estimate based on complexity
      return Math.min(pixels * 0.01, 50000);
    case 'json':
      // JSON depends on content
      return 5000;
    case 'pdf':
      // PDF has overhead
      return pixels * 4 * qualityMultiplier * 0.5 + 10000;
    default:
      return 0;
  }
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default CanvasExportDialog;
