/**
 * Skeleton Component
 * 
 * Production-grade skeleton loading placeholders
 * 
 * @module ui/components/Loading/Skeleton
 * @doc.type component
 * @doc.purpose Content loading placeholder
 * @doc.layer ui
 */

import React from 'react';
import './Skeleton.css';

export type SkeletonVariant = 'text' | 'circular' | 'rectangular' | 'rounded';
export type SkeletonAnimation = 'pulse' | 'wave' | 'none';

export interface SkeletonProps {
  /** Variant of the skeleton */
  variant?: SkeletonVariant;
  /** Width of the skeleton (CSS value) */
  width?: string | number;
  /** Height of the skeleton (CSS value) */
  height?: string | number;
  /** Animation type */
  animation?: SkeletonAnimation;
  /** Additional CSS class names */
  className?: string;
  /** Number of skeleton items to render */
  count?: number;
}

/**
 * Skeleton loading placeholder component
 * 
 * @example Basic text skeleton
 * ```tsx
 * <Skeleton variant="text" width="200px" />
 * ```
 * 
 * @example Circular avatar skeleton
 * ```tsx
 * <Skeleton variant="circular" width={48} height={48} />
 * ```
 * 
 * @example Multiple text lines
 * ```tsx
 * <Skeleton variant="text" count={3} />
 * ```
 */
export function Skeleton({
  variant = 'text',
  width,
  height,
  animation = 'pulse',
  className = '',
  count = 1,
}: SkeletonProps): React.JSX.Element {
  const style: React.CSSProperties = {};

  if (width !== undefined) {
    style.width = typeof width === 'number' ? `${width}px` : width;
  }

  if (height !== undefined) {
    style.height = typeof height === 'number' ? `${height}px` : height;
  }

  const skeletonClasses = [
    'skeleton',
    `skeleton--${variant}`,
    animation !== 'none' ? `skeleton--animation-${animation}` : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  if (count > 1) {
    return (
      <>
        {Array.from({ length: count }).map((_, index) => (
          <div
            key={index}
            className={skeletonClasses}
            style={style}
            aria-busy="true"
            aria-live="polite"
          />
        ))}
      </>
    );
  }

  return (
    <div
      className={skeletonClasses}
      style={style}
      aria-busy="true"
      aria-live="polite"
    />
  );
}

/**
 * Skeleton card component for common card layouts
 */
export interface SkeletonCardProps {
  /** Whether to show avatar */
  showAvatar?: boolean;
  /** Number of text lines */
  lines?: number;
  /** Animation type */
  animation?: SkeletonAnimation;
  /** Additional CSS class names */
  className?: string;
}

export function SkeletonCard({
  showAvatar = true,
  lines = 3,
  animation = 'pulse',
  className = '',
}: SkeletonCardProps): React.JSX.Element {
  return (
    <div className={`skeleton-card ${className}`}>
      {showAvatar && (
        <Skeleton
          variant="circular"
          width={48}
          height={48}
          animation={animation}
        />
      )}
      <div className="skeleton-card__content">
        <Skeleton
          variant="text"
          width="60%"
          height="20px"
          animation={animation}
        />
        {Array.from({ length: lines }).map((_, index) => (
          <Skeleton
            key={index}
            variant="text"
            width={index === lines - 1 ? '80%' : '100%'}
            height="16px"
            animation={animation}
          />
        ))}
      </div>
    </div>
  );
}

/**
 * Skeleton table component for table loading states
 */
export interface SkeletonTableProps {
  /** Number of rows */
  rows?: number;
  /** Number of columns */
  columns?: number;
  /** Whether to show header */
  showHeader?: boolean;
  /** Animation type */
  animation?: SkeletonAnimation;
  /** Additional CSS class names */
  className?: string;
}

export function SkeletonTable({
  rows = 5,
  columns = 4,
  showHeader = true,
  animation = 'pulse',
  className = '',
}: SkeletonTableProps): React.JSX.Element {
  return (
    <div className={`skeleton-table ${className}`}>
      {showHeader && (
        <div className="skeleton-table__header">
          {Array.from({ length: columns }).map((_, index) => (
            <Skeleton
              key={index}
              variant="text"
              width="100%"
              height="20px"
              animation={animation}
            />
          ))}
        </div>
      )}
      <div className="skeleton-table__body">
        {Array.from({ length: rows }).map((_, rowIndex) => (
          <div key={rowIndex} className="skeleton-table__row">
            {Array.from({ length: columns }).map((_, colIndex) => (
              <Skeleton
                key={colIndex}
                variant="text"
                width="100%"
                height="16px"
                animation={animation}
              />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Skeleton list component for list loading states
 */
export interface SkeletonListProps {
  /** Number of items */
  items?: number;
  /** Whether to show avatar */
  showAvatar?: boolean;
  /** Number of text lines per item */
  lines?: number;
  /** Animation type */
  animation?: SkeletonAnimation;
  /** Additional CSS class names */
  className?: string;
}

export function SkeletonList({
  items = 5,
  showAvatar = true,
  lines = 2,
  animation = 'pulse',
  className = '',
}: SkeletonListProps): React.JSX.Element {
  return (
    <div className={`skeleton-list ${className}`}>
      {Array.from({ length: items }).map((_, index) => (
        <div key={index} className="skeleton-list__item">
          {showAvatar && (
            <Skeleton
              variant="circular"
              width={40}
              height={40}
              animation={animation}
            />
          )}
          <div className="skeleton-list__content">
            {Array.from({ length: lines }).map((_, lineIndex) => (
              <Skeleton
                key={lineIndex}
                variant="text"
                width={lineIndex === lines - 1 ? '70%' : '100%'}
                height="16px"
                animation={animation}
              />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
