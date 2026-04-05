/**
 * Standardized Loading State Component
 * 
 * Provides consistent loading UI across the application.
 * Supports different sizes, variants, and custom messages.
 * 
 * @doc.type component
 * @doc.purpose Loading state UI
 * @doc.layer presentation
 */

import { Loader2 } from 'lucide-react';

export interface LoadingStateProps {
  /** Loading message to display */
  message?: string;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg' | 'xl';
  /** Display variant */
  variant?: 'spinner' | 'skeleton' | 'overlay';
  /** Full screen overlay */
  fullScreen?: boolean;
  /** Custom className */
  className?: string;
}

const sizeClasses = {
  sm: 'w-4 h-4',
  md: 'w-6 h-6',
  lg: 'w-8 h-8',
  xl: 'w-12 h-12',
};

const textSizeClasses = {
  sm: 'text-sm',
  md: 'text-base',
  lg: 'text-lg',
  xl: 'text-xl',
};

export function LoadingState({
  message = 'Loading...',
  size = 'md',
  variant = 'spinner',
  fullScreen = false,
  className = '',
}: LoadingStateProps) {
  if (variant === 'skeleton') {
    return <LoadingSkeleton size={size} className={className} />;
  }

  if (variant === 'overlay' || fullScreen) {
    return (
      <div
        className={`fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm ${className}`}
        role="status"
        aria-live="polite"
      >
        <div className="flex flex-col items-center gap-4 rounded-lg bg-surface p-8 shadow-xl">
          <Loader2 className={`${sizeClasses[size]} animate-spin text-brand`} />
          <p className={`${textSizeClasses[size]} text-foreground`}>{message}</p>
        </div>
      </div>
    );
  }

  return (
    <div
      className={`flex items-center gap-3 ${className}`}
      role="status"
      aria-live="polite"
    >
      <Loader2 className={`${sizeClasses[size]} animate-spin text-brand`} />
      {message && <span className={`${textSizeClasses[size]} text-muted-foreground`}>{message}</span>}
    </div>
  );
}

function LoadingSkeleton({ size, className }: { size: LoadingStateProps['size']; className?: string }) {
  const height = size === 'sm' ? 'h-4' : size === 'md' ? 'h-6' : size === 'lg' ? 'h-8' : 'h-12';
  
  return (
    <div className={`space-y-3 ${className}`} role="status" aria-live="polite">
      <div className={`${height} w-full animate-pulse rounded bg-muted`} />
      <div className={`${height} w-3/4 animate-pulse rounded bg-muted`} />
      <div className={`${height} w-1/2 animate-pulse rounded bg-muted`} />
    </div>
  );
}

/**
 * Inline loading spinner for buttons and small spaces
 */
export function LoadingSpinner({ size = 'sm', className = '' }: Pick<LoadingStateProps, 'size' | 'className'>) {
  return <Loader2 className={`${sizeClasses[size!]} animate-spin ${className}`} />;
}

/**
 * Page-level loading state
 */
export function PageLoading({ message }: { message?: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <LoadingState message={message} size="lg" />
    </div>
  );
}

/**
 * Section-level loading state
 */
export function SectionLoading({ message }: { message?: string }) {
  return (
    <div className="flex min-h-[200px] items-center justify-center">
      <LoadingState message={message} size="md" />
    </div>
  );
}
