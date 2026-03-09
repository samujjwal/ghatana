/**
 * Upload Documents Page
 *
 * @description Allows users to upload existing documents (PRD, specs, wireframes)
 * to bootstrap their project with AI-assisted extraction.
 *
 * @doc.type page
 * @doc.purpose Document upload for bootstrapping
 * @doc.layer page
 * @doc.phase bootstrapping
 */

import React, { useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router';
import { useSetAtom } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Upload,
  FileText,
  Image,
  File,
  X,
  CheckCircle2,
  AlertCircle,
  Loader2,
  ArrowRight,
  ArrowLeft,
  Sparkles,
  FileCode,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import { Button } from '@ghatana/ui';
import { Progress } from '@ghatana/ui';
import { Tooltip } from '@ghatana/ui';

import { uploadedDocsAtom } from '../../state/atoms';
import { ROUTES } from '../../router/paths';

// =============================================================================
// Types
// =============================================================================

type FileStatus = 'pending' | 'uploading' | 'processing' | 'ready' | 'error';

interface UploadedFile {
  id: string;
  file: File;
  name: string;
  size: number;
  type: string;
  status: FileStatus;
  progress: number;
  extractedContent?: string;
  error?: string;
  preview?: string;
}

type AcceptedFileType = 'document' | 'image' | 'code';

// =============================================================================
// File Type Configuration
// =============================================================================

const ACCEPTED_TYPES: Record<
  AcceptedFileType,
  { extensions: string[]; mimeTypes: string[]; icon: React.ElementType; label: string }
> = {
  document: {
    extensions: ['.pdf', '.doc', '.docx', '.md', '.txt'],
    mimeTypes: [
      'application/pdf',
      'application/msword',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'text/markdown',
      'text/plain',
    ],
    icon: FileText,
    label: 'Documents',
  },
  image: {
    extensions: ['.png', '.jpg', '.jpeg', '.svg', '.webp'],
    mimeTypes: ['image/png', 'image/jpeg', 'image/svg+xml', 'image/webp'],
    icon: Image,
    label: 'Images & Wireframes',
  },
  code: {
    extensions: ['.json', '.yaml', '.yml', '.ts', '.tsx', '.js'],
    mimeTypes: [
      'application/json',
      'text/yaml',
      'application/x-yaml',
      'text/typescript',
      'application/javascript',
    ],
    icon: FileCode,
    label: 'Code & Config',
  },
};

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
const MAX_FILES = 10;

// =============================================================================
// Helper Functions
// =============================================================================

const getFileType = (file: File): AcceptedFileType | null => {
  for (const [type, config] of Object.entries(ACCEPTED_TYPES)) {
    if (config.mimeTypes.includes(file.type)) {
      return type as AcceptedFileType;
    }
    const ext = '.' + file.name.split('.').pop()?.toLowerCase();
    if (config.extensions.includes(ext)) {
      return type as AcceptedFileType;
    }
  }
  return null;
};

const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const generateId = (): string => Math.random().toString(36).substr(2, 9);

// =============================================================================
// Drop Zone Component
// =============================================================================

interface DropZoneProps {
  onFilesDropped: (files: File[]) => void;
  disabled?: boolean;
}

const DropZone: React.FC<DropZoneProps> = ({ onFilesDropped, disabled }) => {
  const [isDragging, setIsDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setIsDragging(false);
      if (disabled) return;

      const files = Array.from(e.dataTransfer.files);
      onFilesDropped(files);
    },
    [onFilesDropped, disabled]
  );

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(e.target.files || []);
      onFilesDropped(files);
      if (inputRef.current) {
        inputRef.current.value = '';
      }
    },
    [onFilesDropped]
  );

  const acceptedExtensions = Object.values(ACCEPTED_TYPES)
    .flatMap((t) => t.extensions)
    .join(',');

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={cn(
        'relative rounded-lg border-2 border-dashed p-8 text-center transition-all duration-200',
        isDragging
          ? 'border-primary-500 bg-primary-500/10'
          : 'border-zinc-700 hover:border-zinc-600',
        disabled && 'cursor-not-allowed opacity-50'
      )}
    >
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={acceptedExtensions}
        onChange={handleInputChange}
        disabled={disabled}
        className="absolute inset-0 cursor-pointer opacity-0"
      />

      <div className="flex flex-col items-center gap-4">
        <div
          className={cn(
            'rounded-full p-4 transition-colors',
            isDragging ? 'bg-primary-500/20' : 'bg-zinc-800'
          )}
        >
          <Upload
            className={cn(
              'h-8 w-8 transition-colors',
              isDragging ? 'text-primary-400' : 'text-zinc-400'
            )}
          />
        </div>

        <div>
          <p className="text-lg font-medium text-zinc-100">
            Drop files here or click to upload
          </p>
          <p className="mt-1 text-sm text-zinc-400">
            PRD, specs, wireframes, or any project documentation
          </p>
        </div>

        <div className="flex flex-wrap justify-center gap-2">
          {Object.entries(ACCEPTED_TYPES).map(([key, config]) => {
            const Icon = config.icon;
            return (
              <span
                key={key}
                className="inline-flex items-center gap-1.5 rounded-full border border-zinc-700 bg-zinc-800/50 px-3 py-1 text-xs text-zinc-300"
              >
                <Icon className="h-3 w-3" />
                {config.label}
              </span>
            );
          })}
        </div>

        <p className="text-xs text-zinc-500">
          Max {MAX_FILES} files, {formatFileSize(MAX_FILE_SIZE)} each
        </p>
      </div>
    </div>
  );
};

// =============================================================================
// File Card Component
// =============================================================================

interface FileCardProps {
  file: UploadedFile;
  onRemove: (id: string) => void;
  onRetry: (id: string) => void;
}

const FileCard: React.FC<FileCardProps> = ({ file, onRemove, onRetry }) => {
  const fileType = getFileType(file.file);
  const config = fileType ? ACCEPTED_TYPES[fileType] : null;
  const Icon = config?.icon || File;

  const statusColors: Record<FileStatus, string> = {
    pending: 'text-zinc-400',
    uploading: 'text-blue-400',
    processing: 'text-purple-400',
    ready: 'text-success-400',
    error: 'text-error-400',
  };

  const statusLabels: Record<FileStatus, string> = {
    pending: 'Pending',
    uploading: 'Uploading...',
    processing: 'AI Processing...',
    ready: 'Ready',
    error: 'Failed',
  };

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      className={cn(
        'flex items-center gap-4 rounded-lg border p-4',
        file.status === 'error'
          ? 'border-error-500/30 bg-error-500/5'
          : file.status === 'ready'
            ? 'border-success-500/30 bg-success-500/5'
            : 'border-zinc-800 bg-zinc-900'
      )}
    >
      {/* File Icon */}
      <div
        className={cn(
          'flex h-12 w-12 items-center justify-center rounded-lg',
          file.status === 'ready' ? 'bg-success-500/10' : 'bg-zinc-800'
        )}
      >
        {file.status === 'uploading' || file.status === 'processing' ? (
          <Loader2 className={cn('h-6 w-6 animate-spin', statusColors[file.status])} />
        ) : file.status === 'ready' ? (
          <CheckCircle2 className="h-6 w-6 text-success-400" />
        ) : file.status === 'error' ? (
          <AlertCircle className="h-6 w-6 text-error-400" />
        ) : (
          <Icon className="h-6 w-6 text-zinc-400" />
        )}
      </div>

      {/* File Info */}
      <div className="flex-1 overflow-hidden">
        <p className="truncate font-medium text-zinc-100">{file.name}</p>
        <div className="flex items-center gap-2 text-sm">
          <span className="text-zinc-500">{formatFileSize(file.size)}</span>
          <span className={statusColors[file.status]}>{statusLabels[file.status]}</span>
        </div>
        {(file.status === 'uploading' || file.status === 'processing') && (
          <Progress value={file.progress} className="mt-2 h-1" />
        )}
        {file.status === 'error' && file.error && (
          <p className="mt-1 text-xs text-error-400">{file.error}</p>
        )}
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2">
        {file.status === 'error' && (
          <Tooltip title="Retry">
            <Button
              variant="ghost"
              size="sm"
              className="h-8 w-8 p-0"
              onClick={() => onRetry(file.id)}
            >
              <ArrowRight className="h-4 w-4" />
            </Button>
          </Tooltip>
        )}
        <Tooltip title="Remove">
          <Button
            variant="ghost"
            size="sm"
            className="h-8 w-8 p-0 text-zinc-400 hover:text-error-400"
            onClick={() => onRemove(file.id)}
          >
            <X className="h-4 w-4" />
          </Button>
        </Tooltip>
      </div>
    </motion.div>
  );
};

// =============================================================================
// Main Page Component
// =============================================================================

const UploadDocsPage: React.FC = () => {
  const navigate = useNavigate();
  const setUploadedDocs = useSetAtom(uploadedDocsAtom);

  const [files, setFiles] = useState<UploadedFile[]>([]);

  // Calculate stats
  const readyCount = files.filter((f) => f.status === 'ready').length;
  const processingCount = files.filter(
    (f) => f.status === 'uploading' || f.status === 'processing'
  ).length;

  // Handle files dropped
  const handleFilesDropped = useCallback(
    (droppedFiles: File[]) => {
      const validFiles: UploadedFile[] = [];
      const errors: string[] = [];

      for (const file of droppedFiles) {
        // Check file count
        if (files.length + validFiles.length >= MAX_FILES) {
          errors.push(`Maximum ${MAX_FILES} files allowed`);
          break;
        }

        // Check file size
        if (file.size > MAX_FILE_SIZE) {
          errors.push(`${file.name} exceeds ${formatFileSize(MAX_FILE_SIZE)} limit`);
          continue;
        }

        // Check file type
        const fileType = getFileType(file);
        if (!fileType) {
          errors.push(`${file.name} is not a supported file type`);
          continue;
        }

        // Check for duplicates
        if (files.some((f) => f.name === file.name && f.size === file.size)) {
          errors.push(`${file.name} is already uploaded`);
          continue;
        }

        validFiles.push({
          id: generateId(),
          file,
          name: file.name,
          size: file.size,
          type: file.type,
          status: 'pending',
          progress: 0,
        });
      }

      if (validFiles.length > 0) {
        setFiles((prev) => [...prev, ...validFiles]);
        // Start uploading
        validFiles.forEach((f) => simulateUpload(f.id));
      }

      // NOTE: Show errors in toast
      if (errors.length > 0) {
        console.warn('Upload errors:', errors);
      }
    },
    [files]
  );

  // Simulate upload and processing
  const simulateUpload = useCallback((fileId: string) => {
    // Start upload
    setFiles((prev) =>
      prev.map((f) => (f.id === fileId ? { ...f, status: 'uploading', progress: 0 } : f))
    );

    // Simulate upload progress
    let progress = 0;
    const uploadInterval = setInterval(() => {
      progress += Math.random() * 30;
      if (progress >= 100) {
        clearInterval(uploadInterval);
        // Switch to processing
        setFiles((prev) =>
          prev.map((f) =>
            f.id === fileId ? { ...f, status: 'processing', progress: 0 } : f
          )
        );

        // Simulate AI processing
        let procProgress = 0;
        const procInterval = setInterval(() => {
          procProgress += Math.random() * 20;
          if (procProgress >= 100) {
            clearInterval(procInterval);
            // Mark as ready (or error randomly for demo)
            const isError = Math.random() < 0.1;
            setFiles((prev) =>
              prev.map((f) =>
                f.id === fileId
                  ? {
                      ...f,
                      status: isError ? 'error' : 'ready',
                      progress: 100,
                      error: isError ? 'Failed to process document' : undefined,
                      extractedContent: isError ? undefined : 'Extracted content...',
                    }
                  : f
              )
            );
          } else {
            setFiles((prev) =>
              prev.map((f) =>
                f.id === fileId ? { ...f, progress: Math.min(procProgress, 99) } : f
              )
            );
          }
        }, 200);
      } else {
        setFiles((prev) =>
          prev.map((f) =>
            f.id === fileId ? { ...f, progress: Math.min(progress, 99) } : f
          )
        );
      }
    }, 150);
  }, []);

  // Remove file
  const handleRemove = useCallback((fileId: string) => {
    setFiles((prev) => prev.filter((f) => f.id !== fileId));
  }, []);

  // Retry failed file
  const handleRetry = useCallback(
    (fileId: string) => {
      setFiles((prev) =>
        prev.map((f) => (f.id === fileId ? { ...f, status: 'pending', error: undefined } : f))
      );
      simulateUpload(fileId);
    },
    [simulateUpload]
  );

  // Proceed to bootstrapping
  const handleContinue = useCallback(() => {
    const readyFiles = files.filter((f) => f.status === 'ready');
    setUploadedDocs(
      readyFiles.map((f) => ({
        id: f.id,
        name: f.name,
        type: f.type,
        size: f.size,
        status: 'ready',
        extractedContent: f.extractedContent,
        uploadedAt: new Date().toISOString(),
      }))
    );
    // Navigate to create project with uploaded docs context
    navigate(ROUTES.TEMPLATES, { state: { hasUploadedDocs: true } });
  }, [files, setUploadedDocs, navigate]);

  const canContinue = readyCount > 0 && processingCount === 0;

  return (
    <div className="min-h-screen bg-zinc-950 p-6">
      <div className="mx-auto max-w-3xl">
        {/* Header */}
        <div className="mb-8">
          <Button
            variant="ghost"
            onClick={() => navigate(-1)}
            className="mb-4 gap-2"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </Button>
          <h1 className="text-2xl font-bold text-zinc-100">
            Upload Project Documents
          </h1>
          <p className="mt-1 text-sm text-zinc-400">
            Upload existing PRDs, specs, or wireframes. Our AI will extract
            requirements to jumpstart your project.
          </p>
        </div>

        {/* Drop Zone */}
        <DropZone
          onFilesDropped={handleFilesDropped}
          disabled={files.length >= MAX_FILES}
        />

        {/* File List */}
        {files.length > 0 && (
          <div className="mt-6">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-zinc-100">
                Uploaded Files ({files.length})
              </h2>
              {readyCount > 0 && (
                <span className="inline-flex items-center gap-1 rounded-full bg-success-500/10 px-2.5 py-0.5 text-xs font-medium text-success-400">
                  <CheckCircle2 className="h-3 w-3" />
                  {readyCount} ready
                </span>
              )}
            </div>

            <div className="space-y-3">
              <AnimatePresence mode="popLayout">
                {files.map((file) => (
                  <FileCard
                    key={file.id}
                    file={file}
                    onRemove={handleRemove}
                    onRetry={handleRetry}
                  />
                ))}
              </AnimatePresence>
            </div>
          </div>
        )}

        {/* AI Extraction Info */}
        {readyCount > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="mt-6 rounded-lg border border-primary-500/30 bg-primary-500/5 p-4"
          >
            <div className="flex items-start gap-3">
              <Sparkles className="h-5 w-5 flex-shrink-0 text-primary-400" />
              <div>
                <p className="font-medium text-zinc-100">
                  AI will extract from your documents:
                </p>
                <ul className="mt-2 space-y-1 text-sm text-zinc-400">
                  <li>• Project goals and objectives</li>
                  <li>• Features and user requirements</li>
                  <li>• Technical specifications</li>
                  <li>• UI/UX patterns from wireframes</li>
                </ul>
              </div>
            </div>
          </motion.div>
        )}

        {/* Actions */}
        <div className="mt-8 flex items-center justify-between">
          <Button variant="outline" onClick={() => navigate(ROUTES.TEMPLATES)}>
            Skip & Start Fresh
          </Button>
          <Button onClick={handleContinue} disabled={!canContinue}>
            {processingCount > 0 ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Processing...
              </>
            ) : (
              <>
                Continue
                <ArrowRight className="ml-2 h-4 w-4" />
              </>
            )}
          </Button>
        </div>
      </div>
    </div>
  );
};

export default UploadDocsPage;
