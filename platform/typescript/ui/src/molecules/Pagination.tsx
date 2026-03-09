import React from 'react';
import { tokens } from '@ghatana/tokens';

export interface PaginationProps {
  /** Current page (1-indexed) */
  currentPage: number;
  /** Total number of pages */
  totalPages: number;
  /** Page change handler */
  onPageChange: (page: number) => void;
  /** Number of page buttons to show */
  siblingCount?: number;
  /** Show first/last buttons */
  showFirstLast?: boolean;
  /** Show previous/next buttons */
  showPrevNext?: boolean;
  /** Size */
  size?: 'sm' | 'md' | 'lg';
  /** Disabled state */
  disabled?: boolean;
  /** Additional class name */
  className?: string;
}

export const Pagination: React.FC<PaginationProps> = ({
  currentPage,
  totalPages,
  onPageChange,
  siblingCount = 1,
  showFirstLast = true,
  showPrevNext = true,
  size = 'md',
  disabled = false,
  className,
}) => {
  const sizeConfig = {
    sm: { padding: `${tokens.spacing[1]} ${tokens.spacing[2]}`, fontSize: tokens.typography.fontSize.sm, minWidth: '28px' },
    md: { padding: `${tokens.spacing[2]} ${tokens.spacing[3]}`, fontSize: tokens.typography.fontSize.base, minWidth: '36px' },
    lg: { padding: `${tokens.spacing[3]} ${tokens.spacing[4]}`, fontSize: tokens.typography.fontSize.lg, minWidth: '44px' },
  };

  const config = sizeConfig[size];

  const generatePageNumbers = (): (number | string)[] => {
    const totalNumbers = siblingCount * 2 + 3; // siblings + current + first + last
    const totalBlocks = totalNumbers + 2; // + 2 ellipsis

    if (totalPages <= totalBlocks) {
      return Array.from({ length: totalPages }, (_, i) => i + 1);
    }

    const leftSiblingIndex = Math.max(currentPage - siblingCount, 1);
    const rightSiblingIndex = Math.min(currentPage + siblingCount, totalPages);

    const shouldShowLeftEllipsis = leftSiblingIndex > 2;
    const shouldShowRightEllipsis = rightSiblingIndex < totalPages - 1;

    if (!shouldShowLeftEllipsis && shouldShowRightEllipsis) {
      const leftItemCount = 3 + 2 * siblingCount;
      const leftRange = Array.from({ length: leftItemCount }, (_, i) => i + 1);
      return [...leftRange, '...', totalPages];
    }

    if (shouldShowLeftEllipsis && !shouldShowRightEllipsis) {
      const rightItemCount = 3 + 2 * siblingCount;
      const rightRange = Array.from({ length: rightItemCount }, (_, i) => totalPages - rightItemCount + i + 1);
      return [1, '...', ...rightRange];
    }

    if (shouldShowLeftEllipsis && shouldShowRightEllipsis) {
      const middleRange = Array.from(
        { length: rightSiblingIndex - leftSiblingIndex + 1 },
        (_, i) => leftSiblingIndex + i
      );
      return [1, '...', ...middleRange, '...', totalPages];
    }

    return [];
  };

  const pages = generatePageNumbers();

  const buttonBaseStyles: React.CSSProperties = {
    ...config,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontFamily: tokens.typography.fontFamily.sans,
    fontWeight: tokens.typography.fontWeight.medium,
    border: `${tokens.borderWidth[1]} solid ${tokens.colors.neutral[300]}`,
    borderRadius: tokens.borderRadius.md,
    backgroundColor: tokens.colors.white,
    color: tokens.colors.neutral[700],
    cursor: disabled ? 'not-allowed' : 'pointer',
    transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
    userSelect: 'none',
  };

  const activeButtonStyles: React.CSSProperties = {
    ...buttonBaseStyles,
    backgroundColor: tokens.colors.primary[600],
    color: tokens.colors.white,
    borderColor: tokens.colors.primary[600],
  };

  const disabledButtonStyles: React.CSSProperties = {
    ...buttonBaseStyles,
    opacity: 0.5,
    cursor: 'not-allowed',
  };

  const ellipsisStyles: React.CSSProperties = {
    ...config,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: tokens.colors.neutral[500],
  };

  const containerStyles: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: tokens.spacing[1],
    flexWrap: 'wrap',
  };

  const handlePageClick = (page: number) => {
    if (disabled || page === currentPage || page < 1 || page > totalPages) return;
    onPageChange(page);
  };

  return (
    <nav aria-label="Pagination" style={containerStyles} className={className}>
      {showFirstLast && (
        <button
          style={currentPage === 1 || disabled ? disabledButtonStyles : buttonBaseStyles}
          onClick={() => handlePageClick(1)}
          disabled={currentPage === 1 || disabled}
          aria-label="Go to first page"
          onMouseEnter={(e) => {
            if (!disabled && currentPage !== 1) {
              e.currentTarget.style.backgroundColor = tokens.colors.neutral[50];
            }
          }}
          onMouseLeave={(e) => {
            if (!disabled && currentPage !== 1) {
              e.currentTarget.style.backgroundColor = tokens.colors.white;
            }
          }}
        >
          «
        </button>
      )}

      {showPrevNext && (
        <button
          style={currentPage === 1 || disabled ? disabledButtonStyles : buttonBaseStyles}
          onClick={() => handlePageClick(currentPage - 1)}
          disabled={currentPage === 1 || disabled}
          aria-label="Go to previous page"
          onMouseEnter={(e) => {
            if (!disabled && currentPage !== 1) {
              e.currentTarget.style.backgroundColor = tokens.colors.neutral[50];
            }
          }}
          onMouseLeave={(e) => {
            if (!disabled && currentPage !== 1) {
              e.currentTarget.style.backgroundColor = tokens.colors.white;
            }
          }}
        >
          ‹
        </button>
      )}

      {pages.map((page, index) => {
        if (page === '...') {
          return (
            <span key={`ellipsis-${index}`} style={ellipsisStyles}>
              …
            </span>
          );
        }

        const pageNumber = page as number;
        const isActive = pageNumber === currentPage;

        return (
          <button
            key={pageNumber}
            style={isActive ? activeButtonStyles : disabled ? disabledButtonStyles : buttonBaseStyles}
            onClick={() => handlePageClick(pageNumber)}
            disabled={disabled}
            aria-label={`Go to page ${pageNumber}`}
            aria-current={isActive ? 'page' : undefined}
            onMouseEnter={(e) => {
              if (!disabled && !isActive) {
                e.currentTarget.style.backgroundColor = tokens.colors.neutral[50];
              }
            }}
            onMouseLeave={(e) => {
              if (!disabled && !isActive) {
                e.currentTarget.style.backgroundColor = tokens.colors.white;
              }
            }}
          >
            {pageNumber}
          </button>
        );
      })}

      {showPrevNext && (
        <button
          style={currentPage === totalPages || disabled ? disabledButtonStyles : buttonBaseStyles}
          onClick={() => handlePageClick(currentPage + 1)}
          disabled={currentPage === totalPages || disabled}
          aria-label="Go to next page"
          onMouseEnter={(e) => {
            if (!disabled && currentPage !== totalPages) {
              e.currentTarget.style.backgroundColor = tokens.colors.neutral[50];
            }
          }}
          onMouseLeave={(e) => {
            if (!disabled && currentPage !== totalPages) {
              e.currentTarget.style.backgroundColor = tokens.colors.white;
            }
          }}
        >
          ›
        </button>
      )}

      {showFirstLast && (
        <button
          style={currentPage === totalPages || disabled ? disabledButtonStyles : buttonBaseStyles}
          onClick={() => handlePageClick(totalPages)}
          disabled={currentPage === totalPages || disabled}
          aria-label="Go to last page"
          onMouseEnter={(e) => {
            if (!disabled && currentPage !== totalPages) {
              e.currentTarget.style.backgroundColor = tokens.colors.neutral[50];
            }
          }}
          onMouseLeave={(e) => {
            if (!disabled && currentPage !== totalPages) {
              e.currentTarget.style.backgroundColor = tokens.colors.white;
            }
          }}
        >
          »
        </button>
      )}
    </nav>
  );
};

Pagination.displayName = 'Pagination';
