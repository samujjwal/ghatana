import React from 'react';

import { cn } from '../../utils/cn';

/**
 * Uploaded file info
 */
export interface UploadedFile {
  id: string;
  file: File;
  name: string;
  size: number;
  type: string;
  progress?: number;
  status?: 'pending' | 'uploading' | 'success' | 'error';
  error?: string;
  preview?: string;
}

/**
 * FileUpload component props
 */
export interface FileUploadProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /**
   * Callback when files are selected
   */
  onChange?: (files: File[]) => void;

  /**
   * Callback when upload starts
   */
  onUpload?: (files: UploadedFile[]) => Promise<void>;

  /**
   * Accepted file types (e.g., 'image/*', '.pdf,.doc')
   */
  accept?: string;

  /**
   * Allow multiple file selection
   */
  multiple?: boolean;

  /**
   * Maximum file size in bytes
   */
  maxSize?: number;

  /**
   * Maximum number of files
   */
  maxFiles?: number;

  /**
   * Whether the uploader is disabled
   */
  disabled?: boolean;

  /**
   * Show file preview thumbnails
   */
  showPreview?: boolean;

  /**
   * Show file list
   */
  showFileList?: boolean;

  /**
   * Custom upload text
   */
  uploadText?: string;

  /**
   * Custom drag text
   */
  dragText?: string;
}

// Icons
const UploadIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={2}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
    />
  </svg>
);

const FileIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={2}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
    />
  </svg>
);

const CheckCircleIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
);

const XCircleIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
);

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${Math.round(bytes / Math.pow(k, i) * 100) / 100  } ${  sizes[i]}`;
};

/**
 * FileUpload component - Drag-and-drop file uploader
 */
export const FileUpload = React.forwardRef<HTMLDivElement, FileUploadProps>((props, _ref) => {
  const {
    onChange,
    onUpload,
    accept,
    multiple = false,
    maxSize,
    maxFiles,
    disabled = false,
    showPreview = true,
    showFileList = true,
    uploadText = 'Click to upload',
    dragText = 'or drag and drop',
    className,
    ...rest
  } = props;

  const [files, setFiles] = React.useState<UploadedFile[]>([]);
  const [isDragging, setIsDragging] = React.useState(false);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const dragCounter = React.useRef(0);

  const validateFile = (file: File): string | null => {
    // Check file size
    if (maxSize && file.size > maxSize) {
      return `File size exceeds ${formatFileSize(maxSize)}`;
    }

    // Check file type
    if (accept) {
      const acceptedTypes = accept.split(',').map((t) => t.trim());
      const fileType = file.type;
      const fileExtension = `.${  file.name.split('.').pop()}`;

      const isAccepted = acceptedTypes.some((type) => {
        if (type.startsWith('.')) {
          return fileExtension === type;
        }
        if (type.endsWith('/*')) {
          return fileType.startsWith(type.slice(0, -1));
        }
        return fileType === type;
      });

      if (!isAccepted) {
        return 'File type not accepted';
      }
    }

    return null;
  };

  const createUploadedFile = (file: File): UploadedFile => {
    const uploadedFile: UploadedFile = {
      id: Math.random().toString(36).substr(2, 9),
      file,
      name: file.name,
      size: file.size,
      type: file.type,
      status: 'pending',
    };

    // Generate preview for images
    if (showPreview && file.type.startsWith('image/')) {
      const reader = new FileReader();
      reader.onload = (e) => {
        setFiles((prev) =>
          prev.map((f) =>
            f.id === uploadedFile.id ? { ...f, preview: e.target?.result as string } : f
          )
        );
      };
      reader.readAsDataURL(file);
    }

    return uploadedFile;
  };

  const handleFiles = (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0 || disabled) return;

    const newFilesArray = Array.from(fileList);

    // Check max files
    if (maxFiles && files.length + newFilesArray.length > maxFiles) {
      alert(`Maximum ${maxFiles} files allowed`);
      return;
    }

    const validFiles: File[] = [];
    const newUploadedFiles: UploadedFile[] = [];

    newFilesArray.forEach((file) => {
      const error = validateFile(file);
      if (error) {
        const uploadedFile = createUploadedFile(file);
        uploadedFile.status = 'error';
        uploadedFile.error = error;
        newUploadedFiles.push(uploadedFile);
      } else {
        validFiles.push(file);
        newUploadedFiles.push(createUploadedFile(file));
      }
    });

    setFiles((prev) => [...prev, ...newUploadedFiles]);
    onChange?.(validFiles);

    // Auto-upload if handler provided
    if (onUpload && validFiles.length > 0) {
      const validUploadedFiles = newUploadedFiles.filter((f) => f.status === 'pending');
      validUploadedFiles.forEach((f) => {
        f.status = 'uploading';
        f.progress = 0;
      });
      setFiles((prev) => [...prev]);

      onUpload(validUploadedFiles)
        .then(() => {
          setFiles((prev) =>
            prev.map((f) =>
              validUploadedFiles.find((vf) => vf.id === f.id)
                ? { ...f, status: 'success', progress: 100 }
                : f
            )
          );
        })
        .catch((error) => {
          setFiles((prev) =>
            prev.map((f) =>
              validUploadedFiles.find((vf) => vf.id === f.id)
                ? { ...f, status: 'error', error: error.message }
                : f
            )
          );
        });
    }
  };

  const handleDragEnter = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter.current++;
    if (e.dataTransfer.items && e.dataTransfer.items.length > 0) {
      setIsDragging(true);
    }
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter.current--;
    if (dragCounter.current === 0) {
      setIsDragging(false);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    dragCounter.current = 0;

    if (disabled) return;

    const { files: droppedFiles } = e.dataTransfer;
    handleFiles(droppedFiles);
  };

  const handleClick = () => {
    if (!disabled) {
      inputRef.current?.click();
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    handleFiles(e.target.files);
    // Reset input to allow same file selection
    e.target.value = '';
  };

  const handleRemove = (fileId: string) => {
    setFiles((prev) => prev.filter((f) => f.id !== fileId));
  };

  return (
    <div className={cn('w-full', className)} {...rest}>
      {/* Drop Zone */}
      <div
        onDragEnter={handleDragEnter}
        onDragLeave={handleDragLeave}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        onClick={handleClick}
        className={cn(
          'border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors',
          isDragging && !disabled && 'border-primary-500 bg-primary-50',
          !isDragging && !disabled && 'border-grey-300 hover:border-primary-400 hover:bg-grey-50',
          disabled && 'border-grey-200 bg-grey-50 cursor-not-allowed opacity-50'
        )}
      >
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          multiple={multiple}
          onChange={handleInputChange}
          disabled={disabled}
          className="hidden"
        />

        <UploadIcon
          className={cn(
            'w-12 h-12 mx-auto mb-4',
            isDragging ? 'text-primary-500' : 'text-grey-400'
          )}
        />

        <div className="text-sm">
          <span className="font-semibold text-primary-600">{uploadText}</span>
          <p className="text-grey-500 mt-1">{dragText}</p>
        </div>

        {(accept || maxSize) && (
          <div className="mt-2 text-xs text-grey-500">
            {accept && <div>Accepted: {accept}</div>}
            {maxSize && <div>Max size: {formatFileSize(maxSize)}</div>}
          </div>
        )}
      </div>

      {/* File List */}
      {showFileList && files.length > 0 && (
        <div className="mt-4 space-y-2">
          {files.map((file) => (
            <div
              key={file.id}
              className="flex items-center gap-3 p-3 border border-grey-300 rounded-lg bg-white"
            >
              {/* Preview or Icon */}
              <div className="flex-shrink-0">
                {file.preview ? (
                  <img
                    src={file.preview}
                    alt={file.name}
                    className="w-12 h-12 object-cover rounded"
                  />
                ) : (
                  <div className="w-12 h-12 flex items-center justify-center bg-grey-100 rounded">
                    <FileIcon className="w-6 h-6 text-grey-400" />
                  </div>
                )}
              </div>

              {/* File Info */}
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-grey-900 truncate">{file.name}</div>
                <div className="text-xs text-grey-500">{formatFileSize(file.size)}</div>

                {/* Progress Bar */}
                {file.status === 'uploading' && file.progress !== undefined && (
                  <div className="mt-1 h-1 bg-grey-200 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-primary-500 transition-all duration-300"
                      style={{ width: `${file.progress}%` }}
                    />
                  </div>
                )}

                {/* Error */}
                {file.status === 'error' && file.error && (
                  <div className="mt-1 text-xs text-error-500">{file.error}</div>
                )}
              </div>

              {/* Status Icon */}
              <div className="flex-shrink-0">
                {file.status === 'success' && (
                  <CheckCircleIcon className="w-5 h-5 text-success-500" />
                )}
                {file.status === 'error' && <XCircleIcon className="w-5 h-5 text-error-500" />}
              </div>

              {/* Remove Button */}
              <button
                type="button"
                onClick={() => handleRemove(file.id)}
                className="flex-shrink-0 text-grey-400 hover:text-grey-600 transition-colors"
              >
                <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
                  <path
                    fillRule="evenodd"
                    d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                    clipRule="evenodd"
                  />
                </svg>
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
});

FileUpload.displayName = 'FileUpload';
