/**
 * SkeletonLoaders
 * 
 * Consistent skeleton loading states for all data views.
 * Replaces spinners with content-aware placeholders.
 * 
 * @doc.type component
 * @doc.purpose Loading state UI
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';

interface SkeletonProps {
  className?: string;
  animate?: boolean;
}

/**
 * Base Skeleton - Animated placeholder
 */
export function Skeleton({ className = '', animate = true }: SkeletonProps) {
  return (
    <div
      className={`
        bg-grey-200 dark:bg-grey-700 rounded
        ${animate ? 'animate-pulse' : ''}
        ${className}
      `}
    />
  );
}

/**
 * Text Skeleton - For text content
 */
export function TextSkeleton({
  lines = 1,
  className = ''
}: {
  lines?: number;
  className?: string;
}) {
  return (
    <div className={`space-y-2 ${className}`}>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton
          key={i}
          className={`h-4 ${i === lines - 1 && lines > 1 ? 'w-3/4' : 'w-full'}`}
        />
      ))}
    </div>
  );
}

/**
 * Avatar Skeleton - For user avatars
 */
export function AvatarSkeleton({
  size = 'medium'
}: {
  size?: 'small' | 'medium' | 'large';
}) {
  const sizeClasses = {
    small: 'w-8 h-8',
    medium: 'w-10 h-10',
    large: 'w-12 h-12',
  };

  return <Skeleton className={`${sizeClasses[size]} rounded-full`} />;
}

/**
 * Card Skeleton - For card components
 */
export function CardSkeleton({ className = '' }: { className?: string }) {
  return (
    <div className={`p-4 rounded-lg border border-divider bg-bg-paper ${className}`}>
      <div className="flex items-start gap-3">
        <Skeleton className="w-10 h-10 rounded-lg flex-shrink-0" />
        <div className="flex-1 space-y-2">
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-3 w-1/2" />
        </div>
      </div>
      <div className="mt-4 space-y-2">
        <Skeleton className="h-3 w-full" />
        <Skeleton className="h-3 w-5/6" />
      </div>
    </div>
  );
}

/**
 * Project Card Skeleton
 */
export function ProjectCardSkeleton() {
  return (
    <div className="p-4 rounded-lg border border-divider bg-bg-paper">
      <div className="flex items-center gap-3 mb-3">
        <Skeleton className="w-10 h-10 rounded-lg" />
        <div className="flex-1">
          <Skeleton className="h-5 w-2/3 mb-1" />
          <Skeleton className="h-3 w-1/3" />
        </div>
      </div>
      <Skeleton className="h-3 w-full mb-2" />
      <Skeleton className="h-3 w-4/5 mb-4" />
      <div className="flex items-center gap-2">
        <Skeleton className="h-6 w-16 rounded-full" />
        <Skeleton className="h-6 w-20 rounded-full" />
      </div>
    </div>
  );
}

/**
 * Project List Skeleton
 */
export function ProjectListSkeleton({
  count = 5
}: {
  count?: number;
}) {
  return (
    <div className="space-y-4">
      {Array.from({ length: count }).map((_, i) => (
        <ProjectCardSkeleton key={i} />
      ))}
    </div>
  );
}

/**
 * Canvas Skeleton - For canvas loading state
 */
export function CanvasSkeleton() {
  return (
    <div className="w-full h-full bg-bg-default p-4">
      {/* Toolbar skeleton */}
      <div className="flex items-center gap-2 mb-4 p-2 bg-bg-paper rounded-lg border border-divider">
        <Skeleton className="w-8 h-8 rounded" />
        <Skeleton className="w-8 h-8 rounded" />
        <Skeleton className="w-8 h-8 rounded" />
        <div className="flex-1" />
        <Skeleton className="w-24 h-8 rounded" />
      </div>

      {/* Canvas area with placeholder nodes */}
      <div className="relative w-full h-[calc(100%-80px)] bg-grey-50 dark:bg-grey-900 rounded-lg border border-divider overflow-hidden">
        {/* Grid pattern */}
        <div
          className="absolute inset-0 opacity-30"
          style={{
            backgroundImage: 'radial-gradient(circle, #ccc 1px, transparent 1px)',
            backgroundSize: '20px 20px',
          }}
        />

        {/* Placeholder nodes */}
        <div className="absolute top-20 left-20">
          <Skeleton className="w-40 h-24 rounded-lg" />
        </div>
        <div className="absolute top-40 left-80">
          <Skeleton className="w-48 h-28 rounded-lg" />
        </div>
        <div className="absolute top-20 right-40">
          <Skeleton className="w-36 h-20 rounded-lg" />
        </div>
        <div className="absolute bottom-40 left-40">
          <Skeleton className="w-44 h-26 rounded-lg" />
        </div>

        {/* Connection lines placeholder */}
        <svg className="absolute inset-0 w-full h-full pointer-events-none opacity-20">
          <line x1="180" y1="100" x2="320" y2="180" stroke="#ccc" strokeWidth="2" strokeDasharray="5,5" />
          <line x1="400" y1="200" x2="600" y2="100" stroke="#ccc" strokeWidth="2" strokeDasharray="5,5" />
        </svg>
      </div>
    </div>
  );
}

/**
 * Dashboard Skeleton
 */
export function DashboardSkeleton({
  widgets = 4
}: {
  widgets?: number;
}) {
  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <Skeleton className="h-8 w-48 mb-2" />
          <Skeleton className="h-4 w-32" />
        </div>
        <Skeleton className="h-10 w-32 rounded-lg" />
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-4 gap-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="p-4 rounded-lg border border-divider bg-bg-paper">
            <Skeleton className="h-4 w-20 mb-2" />
            <Skeleton className="h-8 w-16 mb-1" />
            <Skeleton className="h-3 w-24" />
          </div>
        ))}
      </div>

      {/* Widget grid */}
      <div className="grid grid-cols-2 gap-4">
        {Array.from({ length: widgets }).map((_, i) => (
          <div key={i} className="p-4 rounded-lg border border-divider bg-bg-paper">
            <div className="flex items-center justify-between mb-4">
              <Skeleton className="h-5 w-32" />
              <Skeleton className="h-8 w-8 rounded" />
            </div>
            <div className="space-y-3">
              <Skeleton className="h-32 w-full rounded" />
              <div className="flex gap-2">
                <Skeleton className="h-6 w-16 rounded-full" />
                <Skeleton className="h-6 w-20 rounded-full" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Table Skeleton
 */
export function TableSkeleton({
  rows = 5,
  columns = 4
}: {
  rows?: number;
  columns?: number;
}) {
  return (
    <div className="w-full">
      {/* Header */}
      <div className="flex gap-4 p-3 border-b border-divider bg-grey-50 dark:bg-grey-900">
        {Array.from({ length: columns }).map((_, i) => (
          <Skeleton key={i} className="h-4 flex-1" />
        ))}
      </div>

      {/* Rows */}
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <div key={rowIndex} className="flex gap-4 p-3 border-b border-divider">
          {Array.from({ length: columns }).map((_, colIndex) => (
            <Skeleton
              key={colIndex}
              className={`h-4 flex-1 ${colIndex === 0 ? 'w-1/4' : ''}`}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

/**
 * Sidebar Skeleton
 */
export function SidebarSkeleton() {
  return (
    <div className="w-56 h-full bg-bg-paper border-r border-divider p-4 space-y-4">
      {/* Logo */}
      <div className="flex items-center gap-2 mb-6">
        <Skeleton className="w-8 h-8 rounded-lg" />
        <Skeleton className="h-5 w-20" />
      </div>

      {/* Nav items */}
      <div className="space-y-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="flex items-center gap-3 p-2">
            <Skeleton className="w-5 h-5 rounded" />
            <Skeleton className="h-4 flex-1" />
          </div>
        ))}
      </div>

      {/* Section */}
      <div className="pt-4 border-t border-divider">
        <Skeleton className="h-3 w-16 mb-3" />
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex items-center gap-3 p-2">
              <Skeleton className="w-5 h-5 rounded" />
              <Skeleton className="h-4 flex-1" />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * Task Board Skeleton
 */
export function TaskBoardSkeleton({
  columns = 4
}: {
  columns?: number;
}) {
  return (
    <div className="flex gap-4 p-4 overflow-x-auto">
      {Array.from({ length: columns }).map((_, colIndex) => (
        <div
          key={colIndex}
          className="flex-shrink-0 w-72 bg-grey-50 dark:bg-grey-900 rounded-lg p-3"
        >
          {/* Column header */}
          <div className="flex items-center justify-between mb-3">
            <Skeleton className="h-5 w-24" />
            <Skeleton className="h-5 w-6 rounded-full" />
          </div>

          {/* Cards */}
          <div className="space-y-2">
            {Array.from({ length: 3 - colIndex % 2 }).map((_, cardIndex) => (
              <div
                key={cardIndex}
                className="p-3 bg-bg-paper rounded-lg border border-divider"
              >
                <Skeleton className="h-4 w-full mb-2" />
                <Skeleton className="h-3 w-3/4 mb-3" />
                <div className="flex items-center gap-2">
                  <Skeleton className="w-6 h-6 rounded-full" />
                  <Skeleton className="h-3 w-16" />
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

export default Skeleton;
