import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 *
 */
export interface PaginationProps {
  /** Current page (1-indexed) */
  currentPage: number;
  /** Total number of pages */
  totalPages: number;
  /** Callback when page changes */
  onPageChange: (page: number) => void;
  /** Number of page buttons to show on each side of current */
  siblingCount?: number;
  /** Show first/last buttons */
  showFirstLast?: boolean;
  /** Size variant */
  size?: 'small' | 'medium' | 'large';
  /** Variant style */
  variant?: 'default' | 'outlined' | 'rounded';
  /** Disabled state */
  disabled?: boolean;
  /** className for root element */
  className?: string;
}

/**
 * Pagination component for navigating through pages
 * 
 * Pure Tailwind CSS implementation with customizable appearance.
 * 
 * @example
 * ```tsx
 * <Pagination
 *   currentPage={page}
 *   totalPages={10}
 *   onPageChange={setPage}
 *   showFirstLast
 * />
 * ```
 */
export const Pagination = React.forwardRef<HTMLElement, PaginationProps>(
  (
    {
      currentPage,
      totalPages,
      onPageChange,
      siblingCount = 1,
      showFirstLast = false,
      size = 'medium',
      variant = 'default',
      disabled = false,
      className,
    },
    ref
  ) => {
    // Generate page numbers with ellipsis
    const pages = React.useMemo(() => {
      const range = (start: number, end: number) =>
        Array.from({ length: end - start + 1 }, (_, i) => start + i);

      const totalNumbers = siblingCount * 2 + 3;
      const totalBlocks = totalNumbers + 2;

      if (totalPages <= totalBlocks) {
        return range(1, totalPages);
      }

      const leftSiblingIndex = Math.max(currentPage - siblingCount, 1);
      const rightSiblingIndex = Math.min(currentPage + siblingCount, totalPages);

      const shouldShowLeftDots = leftSiblingIndex > 2;
      const shouldShowRightDots = rightSiblingIndex < totalPages - 1;

      if (!shouldShowLeftDots && shouldShowRightDots) {
        const leftItemCount = 3 + 2 * siblingCount;
        const leftRange = range(1, leftItemCount);
        return [...leftRange, 'dots', totalPages];
      }

      if (shouldShowLeftDots && !shouldShowRightDots) {
        const rightItemCount = 3 + 2 * siblingCount;
        const rightRange = range(totalPages - rightItemCount + 1, totalPages);
        return [1, 'dots', ...rightRange];
      }

      const middleRange = range(leftSiblingIndex, rightSiblingIndex);
      return [1, 'dots', ...middleRange, 'dots', totalPages];
    }, [currentPage, totalPages, siblingCount]);

    // Size-based classes
    const sizeClasses = {
      small: 'w-7 h-7 text-xs',
      medium: 'w-9 h-9 text-sm',
      large: 'w-11 h-11 text-base',
    };

    // Variant-based classes
    const getButtonClasses = (isActive: boolean) => {
      const baseClasses = cn(
        'inline-flex items-center justify-center',
        'transition-colors font-medium',
        'focus:outline-none focus:ring-2 focus:ring-primary-500/20',
        'disabled:opacity-50 disabled:cursor-not-allowed',
        sizeClasses[size]
      );

      if (variant === 'outlined') {
        return cn(
          baseClasses,
          'border',
          isActive
            ? 'bg-primary-500 border-primary-500 text-white'
            : 'border-grey-300 dark:border-grey-700 text-grey-700 dark:text-grey-300 hover:bg-grey-50 dark:hover:bg-grey-800'
        );
      }

      if (variant === 'rounded') {
        return cn(
          baseClasses,
          'rounded-full',
          isActive
            ? 'bg-primary-500 text-white'
            : 'bg-grey-100 dark:bg-grey-800 text-grey-700 dark:text-grey-300 hover:bg-grey-200 dark:hover:bg-grey-700'
        );
      }

      return cn(
        baseClasses,
        'rounded-md',
        isActive
          ? 'bg-primary-500 text-white'
          : 'text-grey-700 dark:text-grey-300 hover:bg-grey-100 dark:hover:bg-grey-800'
      );
    };

    const handlePageChange = (page: number) => {
      if (!disabled && page >= 1 && page <= totalPages && page !== currentPage) {
        onPageChange(page);
      }
    };

    const ArrowIcon = ({ direction }: { direction: 'left' | 'right' }) => (
      <svg
        width="16"
        height="16"
        viewBox="0 0 16 16"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className={cn(direction === 'left' && 'rotate-180')}
      >
        <path
          d="M6 12L10 8L6 4"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    );

    const DoubleArrowIcon = ({ direction }: { direction: 'left' | 'right' }) => (
      <svg
        width="16"
        height="16"
        viewBox="0 0 16 16"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className={cn(direction === 'left' && 'rotate-180')}
      >
        <path
          d="M9 12L13 8L9 4M3 12L7 8L3 4"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    );

    return (
      <nav ref={ref} aria-label="Pagination" className={cn('flex items-center gap-1', className)}>
        {/* First page button */}
        {showFirstLast && (
          <button
            type="button"
            onClick={() => handlePageChange(1)}
            disabled={disabled || currentPage === 1}
            className={getButtonClasses(false)}
            aria-label="First page"
          >
            <DoubleArrowIcon direction="left" />
          </button>
        )}

        {/* Previous button */}
        <button
          type="button"
          onClick={() => handlePageChange(currentPage - 1)}
          disabled={disabled || currentPage === 1}
          className={getButtonClasses(false)}
          aria-label="Previous page"
        >
          <ArrowIcon direction="left" />
        </button>

        {/* Page numbers */}
        {pages.map((page, index) => {
          if (page === 'dots') {
            return (
              <span
                key={`dots-${index}`}
                className={cn(
                  'inline-flex items-center justify-center',
                  sizeClasses[size],
                  'text-grey-500 dark:text-grey-400'
                )}
              >
                ...
              </span>
            );
          }

          const pageNumber = page as number;
          const isActive = pageNumber === currentPage;

          return (
            <button
              key={pageNumber}
              type="button"
              onClick={() => handlePageChange(pageNumber)}
              disabled={disabled}
              className={getButtonClasses(isActive)}
              aria-label={`Page ${pageNumber}`}
              aria-current={isActive ? 'page' : undefined}
            >
              {pageNumber}
            </button>
          );
        })}

        {/* Next button */}
        <button
          type="button"
          onClick={() => handlePageChange(currentPage + 1)}
          disabled={disabled || currentPage === totalPages}
          className={getButtonClasses(false)}
          aria-label="Next page"
        >
          <ArrowIcon direction="right" />
        </button>

        {/* Last page button */}
        {showFirstLast && (
          <button
            type="button"
            onClick={() => handlePageChange(totalPages)}
            disabled={disabled || currentPage === totalPages}
            className={getButtonClasses(false)}
            aria-label="Last page"
          >
            <DoubleArrowIcon direction="right" />
          </button>
        )}
      </nav>
    );
  }
);

Pagination.displayName = 'Pagination';
