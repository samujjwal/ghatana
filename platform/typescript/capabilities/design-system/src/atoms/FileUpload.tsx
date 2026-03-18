import React, { forwardRef, useRef, useState } from 'react';
import { tokens } from '@ghatana/tokens';

export interface FileUploadProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  /** Label for the file upload */
  label?: string;
  /** Error message */
  error?: string;
  /** Helper text */
  helperText?: string;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Full width */
  fullWidth?: boolean;
  /** Show file preview */
  showPreview?: boolean;
  /** Custom upload button text */
  buttonText?: string;
  /** Drag and drop enabled */
  dragAndDrop?: boolean;
}

export const FileUpload = forwardRef<HTMLInputElement, FileUploadProps>(
  (
    {
      label,
      error,
      helperText,
      size = 'md',
      fullWidth = false,
      showPreview = true,
      buttonText = 'Choose File',
      dragAndDrop = true,
      disabled,
      className,
      onChange,
      ...props
    },
    ref
  ) => {
    const [selectedFiles, setSelectedFiles] = useState<FileList | null>(null);
    const [isDragging, setIsDragging] = useState(false);
    const fileInputId = useRef(`fileupload-${Math.random().toString(36).substr(2, 9)}`).current;
    const errorId = error ? `${fileInputId}-error` : undefined;
    const helperTextId = helperText ? `${fileInputId}-helper` : undefined;

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      setSelectedFiles(e.target.files);
      onChange?.(e);
    };

    const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
      if (!disabled && dragAndDrop) {
        e.preventDefault();
        setIsDragging(true);
      }
    };

    const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
      if (!disabled && dragAndDrop) {
        e.preventDefault();
        setIsDragging(false);
      }
    };

    const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
      if (!disabled && dragAndDrop) {
        e.preventDefault();
        setIsDragging(false);
        const files = e.dataTransfer.files;
        if (files.length > 0) {
          setSelectedFiles(files);
          // Trigger onChange with synthetic event
          const input = document.getElementById(fileInputId) as HTMLInputElement;
          if (input) {
            const dataTransfer = new DataTransfer();
            Array.from(files).forEach((file) => dataTransfer.items.add(file));
            input.files = dataTransfer.files;
            const event = new Event('change', { bubbles: true });
            input.dispatchEvent(event);
          }
        }
      }
    };

    const sizeStyles = {
      sm: {
        padding: tokens.spacing[2],
        fontSize: tokens.typography.fontSize.sm,
        minHeight: '80px',
      },
      md: {
        padding: tokens.spacing[4],
        fontSize: tokens.typography.fontSize.base,
        minHeight: '120px',
      },
      lg: {
        padding: tokens.spacing[6],
        fontSize: tokens.typography.fontSize.lg,
        minHeight: '160px',
      },
    };

    const containerStyles: React.CSSProperties = {
      width: fullWidth ? '100%' : 'auto',
    };

    const labelStyles: React.CSSProperties = {
      display: 'block',
      marginBottom: tokens.spacing[2],
      fontSize: tokens.typography.fontSize.sm,
      fontWeight: tokens.typography.fontWeight.medium,
      color: tokens.colors.neutral[700],
    };

    const dropZoneStyles: React.CSSProperties = {
      ...sizeStyles[size],
      width: '100%',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: tokens.spacing[2],
      border: `2px dashed ${
        error
          ? tokens.colors.error[500]
          : isDragging
          ? tokens.colors.primary[500]
          : tokens.colors.neutral[300]
      }`,
      borderRadius: tokens.borderRadius.lg,
      backgroundColor: disabled
        ? tokens.colors.neutral[50]
        : isDragging
        ? tokens.colors.primary[50]
        : tokens.colors.neutral[50],
      cursor: disabled ? 'not-allowed' : 'pointer',
      transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
    };

    const buttonStyles: React.CSSProperties = {
      padding: `${tokens.spacing[2]} ${tokens.spacing[4]}`,
      fontSize: tokens.typography.fontSize.sm,
      fontWeight: tokens.typography.fontWeight.medium,
      color: tokens.colors.white,
      backgroundColor: disabled ? tokens.colors.neutral[400] : tokens.colors.primary[600],
      border: 'none',
      borderRadius: tokens.borderRadius.md,
      cursor: disabled ? 'not-allowed' : 'pointer',
      transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
    };

    const helperTextStyles: React.CSSProperties = {
      marginTop: tokens.spacing[2],
      fontSize: tokens.typography.fontSize.xs,
      color: error ? tokens.colors.error[600] : tokens.colors.neutral[600],
    };

    const fileListStyles: React.CSSProperties = {
      marginTop: tokens.spacing[2],
      padding: tokens.spacing[2],
      backgroundColor: tokens.colors.neutral[50],
      borderRadius: tokens.borderRadius.md,
      fontSize: tokens.typography.fontSize.sm,
    };

    return (
      <div style={containerStyles} className={className}>
        {label && (
          <label htmlFor={fileInputId} style={labelStyles}>
            {label}
            {props.required && (
              <span style={{ color: tokens.colors.error[500], marginLeft: tokens.spacing[1] }}>
                *
              </span>
            )}
          </label>
        )}
        <div
          style={dropZoneStyles}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onClick={() => !disabled && document.getElementById(fileInputId)?.click()}
        >
          <svg
            width="48"
            height="48"
            viewBox="0 0 24 24"
            fill="none"
            stroke={disabled ? tokens.colors.neutral[400] : tokens.colors.neutral[600]}
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="17 8 12 3 7 8" />
            <line x1="12" y1="3" x2="12" y2="15" />
          </svg>
          <button
            type="button"
            style={buttonStyles}
            disabled={disabled}
            onMouseEnter={(e) => {
              if (!disabled) {
                e.currentTarget.style.backgroundColor = tokens.colors.primary[700];
              }
            }}
            onMouseLeave={(e) => {
              if (!disabled) {
                e.currentTarget.style.backgroundColor = tokens.colors.primary[600];
              }
            }}
          >
            {buttonText}
          </button>
          {dragAndDrop && (
            <p style={{ margin: 0, color: tokens.colors.neutral[600], fontSize: tokens.typography.fontSize.sm }}>
              or drag and drop files here
            </p>
          )}
        </div>
        <input
          ref={ref}
          id={fileInputId}
          type="file"
          disabled={disabled}
          aria-invalid={!!error}
          aria-describedby={error ? errorId : helperTextId}
          style={{ display: 'none' }}
          onChange={handleChange}
          {...props}
        />
        {showPreview && selectedFiles && selectedFiles.length > 0 && (
          <div style={fileListStyles}>
            <strong>Selected files:</strong>
            <ul style={{ margin: `${tokens.spacing[1]} 0 0 0`, paddingLeft: tokens.spacing[4] }}>
              {Array.from(selectedFiles).map((file, index) => (
                <li key={index}>
                  {file.name} ({(file.size / 1024).toFixed(2)} KB)
                </li>
              ))}
            </ul>
          </div>
        )}
        {(error || helperText) && (
          <div id={error ? errorId : helperTextId} style={helperTextStyles} role={error ? 'alert' : undefined}>
            {error || helperText}
          </div>
        )}
      </div>
    );
  }
);

FileUpload.displayName = 'FileUpload';
