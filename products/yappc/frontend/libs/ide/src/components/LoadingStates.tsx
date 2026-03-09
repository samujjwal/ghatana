/**
 * @ghatana/yappc-ide - Loading States Components
 * 
 * Comprehensive loading states including skeleton screens,
 * progress indicators, spinners, and loading overlays.
 * 
 * @doc.type component
 * @doc.purpose Loading states for better perceived performance
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';

/**
 * Loading spinner component
 */
interface SpinnerProps {
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  color?: 'primary' | 'secondary' | 'white' | 'current';
  className?: string;
}

export const Spinner: React.FC<SpinnerProps> = ({
  size = 'md',
  color = 'primary',
  className = '',
}) => {
  const sizeClasses = {
    xs: 'w-4 h-4',
    sm: 'w-5 h-5',
    md: 'w-6 h-6',
    lg: 'w-8 h-8',
    xl: 'w-12 h-12',
  };

  const colorClasses = {
    primary: 'text-blue-500',
    secondary: 'text-gray-500',
    white: 'text-white',
    current: 'text-current',
  };

  return (
    <div
      className={`
        animate-spin rounded-full border-2 border-current border-t-transparent
        ${sizeClasses[size]} ${colorClasses[color]} ${className}
      `}
      role="status"
      aria-label="Loading"
    >
      <span className="sr-only">Loading...</span>
    </div>
  );
};

/**
 * Dots loading indicator
 */
interface DotsLoaderProps {
  size?: 'sm' | 'md' | 'lg';
  color?: 'primary' | 'secondary' | 'white';
  className?: string;
}

export const DotsLoader: React.FC<DotsLoaderProps> = ({
  size = 'md',
  color = 'primary',
  className = '',
}) => {
  const sizeClasses = {
    sm: 'w-2 h-2',
    md: 'w-3 h-3',
    lg: 'w-4 h-4',
  };

  const colorClasses = {
    primary: 'bg-blue-500',
    secondary: 'bg-gray-500',
    white: 'bg-white',
  };

  return (
    <div className={`flex space-x-1 ${className}`} role="status" aria-label="Loading">
      {[0, 1, 2].map((index) => (
        <div
          key={index}
          className={`
            ${sizeClasses[size]} ${colorClasses[color]} rounded-full
            animate-bounce
          `}
          style={{
            animationDelay: `${index * 0.1}s`,
            animationDuration: '0.6s',
          }}
        />
      ))}
      <span className="sr-only">Loading...</span>
    </div>
  );
};

/**
 * Pulse loading indicator
 */
interface PulseLoaderProps {
  count?: number;
  size?: 'sm' | 'md' | 'lg';
  color?: 'primary' | 'secondary' | 'white';
  className?: string;
}

export const PulseLoader: React.FC<PulseLoaderProps> = ({
  count = 3,
  size = 'md',
  color = 'primary',
  className = '',
}) => {
  const sizeClasses = {
    sm: 'w-2 h-2',
    md: 'w-3 h-3',
    lg: 'w-4 h-4',
  };

  const colorClasses = {
    primary: 'bg-blue-500',
    secondary: 'bg-gray-500',
    white: 'bg-white',
  };

  return (
    <div className={`flex space-x-2 ${className}`} role="status" aria-label="Loading">
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          className={`
            ${sizeClasses[size]} ${colorClasses[color]} rounded-full
            animate-pulse
          `}
          style={{
            animationDelay: `${index * 0.2}s`,
            animationDuration: '1.5s',
          }}
        />
      ))}
      <span className="sr-only">Loading...</span>
    </div>
  );
};

/**
 * Skeleton text component
 */
interface SkeletonTextProps {
  lines?: number;
  width?: string | string[];
  height?: string;
  className?: string;
}

export const SkeletonText: React.FC<SkeletonTextProps> = ({
  lines = 3,
  width = '100%',
  height = '1rem',
  className = '',
}) => {
  const getWidth = (index: number) => {
    if (Array.isArray(width)) {
      return width[index] || width[0];
    }
    return width;
  };

  return (
    <div className={`space-y-2 ${className}`} aria-hidden="true">
      {Array.from({ length: lines }).map((_, index) => (
        <div
          key={index}
          className="bg-gray-200 dark:bg-gray-700 rounded animate-pulse"
          style={{
            width: getWidth(index),
            height,
          }}
        />
      ))}
    </div>
  );
};

/**
 * Skeleton card component
 */
interface SkeletonCardProps {
  showAvatar?: boolean;
  showTitle?: boolean;
  showDescription?: boolean;
  lines?: number;
  className?: string;
}

export const SkeletonCard: React.FC<SkeletonCardProps> = ({
  showAvatar = true,
  showTitle = true,
  showDescription = true,
  lines = 3,
  className = '',
}) => {
  return (
    <div className={`p-4 bg-white dark:bg-gray-800 rounded-lg shadow-sm ${className}`} aria-hidden="true">
      {showAvatar && (
        <div className="flex items-center space-x-3 mb-4">
          <div className="w-10 h-10 bg-gray-200 dark:bg-gray-700 rounded-full animate-pulse" />
          <div className="flex-1">
            <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-3/4 animate-pulse mb-2" />
            <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded w-1/2 animate-pulse" />
          </div>
        </div>
      )}
      
      {showTitle && (
        <div className="h-6 bg-gray-200 dark:bg-gray-700 rounded w-2/3 animate-pulse mb-3" />
      )}
      
      {showDescription && (
        <div className="space-y-2">
          {Array.from({ length: lines }).map((_, index) => (
            <div
              key={index}
              className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse"
              style={{
                width: index === lines - 1 ? '80%' : '100%',
              }}
            />
          ))}
        </div>
      )}
    </div>
  );
};

/**
 * Skeleton table component
 */
interface SkeletonTableProps {
  rows?: number;
  columns?: number;
  showHeader?: boolean;
  className?: string;
}

export const SkeletonTable: React.FC<SkeletonTableProps> = ({
  rows = 5,
  columns = 4,
  showHeader = true,
  className = '',
}) => {
  return (
    <div className={`w-full ${className}`} aria-hidden="true">
      {showHeader && (
        <div className="flex border-b border-gray-200 dark:border-gray-700 pb-3 mb-3">
          {Array.from({ length: columns }).map((_, index) => (
            <div
              key={`header-${index}`}
              className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse"
              style={{
                width: `${100 / columns}%`,
                marginLeft: index > 0 ? '1rem' : '0',
              }}
            />
          ))}
        </div>
      )}
      
      <div className="space-y-3">
        {Array.from({ length: rows }).map((_, rowIndex) => (
          <div key={`row-${rowIndex}`} className="flex">
            {Array.from({ length: columns }).map((_, colIndex) => (
              <div
                key={`cell-${rowIndex}-${colIndex}`}
                className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse"
                style={{
                  width: `${100 / columns}%`,
                  marginLeft: colIndex > 0 ? '1rem' : '0',
                }}
              />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
};

/**
 * Skeleton file tree component
 */
interface SkeletonFileTreeProps {
  files?: number;
  folders?: number;
  depth?: number;
  className?: string;
}

export const SkeletonFileTree: React.FC<SkeletonFileTreeProps> = ({
  files = 5,
  folders = 2,
  className = '',
}) => {
  const renderSkeletonItem = (level: number, isFolder: boolean, index: number) => {
    const indent = level * 16;
    const width = isFolder ? '60%' : `${80 + Math.random() * 20}%`;
    
    return (
      <div
        key={`${level}-${index}`}
        className="flex items-center py-1"
        style={{ paddingLeft: `${indent}px` }}
      >
        <div
          className={`
            h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse
            ${isFolder ? 'w-4 h-4 mr-2' : 'w-3 h-3 mr-2'}
          `}
        />
        <div
          className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse"
          style={{ width }}
        />
      </div>
    );
  };

  return (
    <div className={`space-y-1 ${className}`} aria-hidden="true">
      {Array.from({ length: folders }).map((_, index) => (
        <React.Fragment key={`folder-${index}`}>
          {renderSkeletonItem(0, true, index)}
          {Array.from({ length: Math.floor(files / folders) }).map((_, fileIndex) => (
            renderSkeletonItem(1, false, fileIndex)
          ))}
        </React.Fragment>
      ))}
    </div>
  );
};

/**
 * Loading overlay component
 */
interface LoadingOverlayProps {
  isLoading: boolean;
  message?: string;
  spinner?: boolean;
  blur?: boolean;
  className?: string;
}

export const LoadingOverlay: React.FC<LoadingOverlayProps> = ({
  isLoading,
  message = 'Loading...',
  spinner = true,
  className = '',
}) => {
  if (!isLoading) return null;

  return (
    <div
      className={`
        fixed inset-0 z-50 flex items-center justify-center
        bg-black/50 backdrop-blur-sm
        ${className}
      `}
      role="dialog"
      aria-modal="true"
      aria-labelledby="loading-message"
    >
      <div className="bg-white dark:bg-gray-800 rounded-lg p-6 shadow-xl">
        <div className="flex flex-col items-center space-y-4">
          {spinner && <Spinner size="lg" />}
          <div id="loading-message" className="text-gray-700 dark:text-gray-300 text-center">
            {message}
          </div>
        </div>
      </div>
    </div>
  );
};

/**
 * Progress loading component
 */
interface ProgressLoaderProps {
  progress: number;
  message?: string;
  showPercentage?: boolean;
  color?: 'blue' | 'green' | 'yellow' | 'red';
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export const ProgressLoader: React.FC<ProgressLoaderProps> = ({
  progress,
  message,
  showPercentage = true,
  color = 'blue',
  size = 'md',
  className = '',
}) => {
  const sizeClasses = {
    sm: 'h-1',
    md: 'h-2',
    lg: 'h-3',
  };

  const colorClasses = {
    blue: 'bg-blue-500',
    green: 'bg-green-500',
    yellow: 'bg-yellow-500',
    red: 'bg-red-500',
  };

  return (
    <div className={`w-full ${className}`}>
      {(message || showPercentage) && (
        <div className="flex justify-between items-center mb-2">
          {message && (
            <span className="text-sm text-gray-600 dark:text-gray-400">
              {message}
            </span>
          )}
          {showPercentage && (
            <span className="text-sm text-gray-600 dark:text-gray-400">
              {Math.round(progress)}%
            </span>
          )}
        </div>
      )}
      <div className={`w-full bg-gray-200 dark:bg-gray-700 rounded-full ${sizeClasses[size]}`}>
        <div
          className={`
            ${colorClasses[color]} ${sizeClasses[size]} rounded-full
            transition-all duration-300 ease-out
          `}
          style={{ width: `${Math.min(Math.max(progress, 0), 100)}%` }}
        />
      </div>
    </div>
  );
};

/**
 * Staggered loading animation
 */
interface StaggeredLoaderProps {
  items: number;
  component: React.FC<{ index: number; delay: number }>;
  stagger?: number;
  className?: string;
}

export const StaggeredLoader: React.FC<StaggeredLoaderProps> = ({
  items,
  component: Component,
  stagger = 100,
  className = '',
}) => {
  return (
    <div className={className}>
      {Array.from({ length: items }).map((_, index) => (
        <Component
          key={index}
          index={index}
          delay={index * stagger}
        />
      ))}
    </div>
  );
};

/**
 * Loading states for specific IDE components
 */
export const IDELoadingStates = {
  /**
   * File explorer loading state
   */
  FileExplorer: () => (
    <div className="p-4 space-y-2">
      <div className="flex items-center justify-between mb-4">
        <div className="h-6 bg-gray-200 dark:bg-gray-700 rounded w-1/3 animate-pulse" />
        <div className="h-8 bg-gray-200 dark:bg-gray-700 rounded w-8 animate-pulse" />
      </div>
      <SkeletonFileTree files={8} folders={3} />
    </div>
  ),

  /**
   * Editor loading state
   */
  Editor: () => (
    <div className="h-full bg-white dark:bg-gray-900 p-4">
      <div className="space-y-2">
        {Array.from({ length: 20 }).map((_, index) => (
          <div
            key={index}
            className="h-5 bg-gray-200 dark:bg-gray-800 rounded animate-pulse"
            style={{
              width: `${60 + Math.random() * 40}%`,
            }}
          />
        ))}
      </div>
    </div>
  ),

  /**
   * Tab bar loading state
   */
  TabBar: () => (
    <div className="flex border-b border-gray-200 dark:border-gray-700">
      {Array.from({ length: 4 }).map((_, index) => (
        <div
          key={index}
          className="h-10 bg-gray-200 dark:bg-gray-700 animate-pulse"
          style={{
            width: `${80 + Math.random() * 40}px`,
            marginRight: index < 3 ? '2px' : '0',
          }}
        />
      ))}
    </div>
  ),

  /**
   * Status bar loading state
   */
  StatusBar: () => (
    <div className="flex items-center justify-between px-4 py-2 bg-gray-100 dark:bg-gray-800">
      <div className="flex space-x-4">
        <div className="h-4 bg-gray-300 dark:bg-gray-700 rounded w-20 animate-pulse" />
        <div className="h-4 bg-gray-300 dark:bg-gray-700 rounded w-16 animate-pulse" />
      </div>
      <div className="flex space-x-4">
        <div className="h-4 bg-gray-300 dark:bg-gray-700 rounded w-24 animate-pulse" />
        <div className="h-4 bg-gray-300 dark:bg-gray-700 rounded w-16 animate-pulse" />
      </div>
    </div>
  ),
};

export default {
  Spinner,
  DotsLoader,
  PulseLoader,
  SkeletonText,
  SkeletonCard,
  SkeletonTable,
  SkeletonFileTree,
  LoadingOverlay,
  ProgressLoader,
  StaggeredLoader,
  IDELoadingStates,
};
