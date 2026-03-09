import { useMemo } from 'react';

/**
 *
 */
export interface PaginationProps {
  /**
   * Current page (1-indexed)
   */
  currentPage: number;
  
  /**
   * Total number of pages
   */
  totalPages: number;
  
  /**
   * Callback when page changes
   */
  onPageChange: (page: number) => void;
  
  /**
   * Number of page buttons to show
   */
  siblingCount?: number;
  
  /**
   * Show first/last buttons
   */
  showFirstLast?: boolean;
  
  /**
   * Disabled state
   */
  disabled?: boolean;
  
  /**
   * Additional class name
   */
  className?: string;
}

/**
 * Pagination component for navigating through pages
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
export function Pagination({
  currentPage,
  totalPages,
  onPageChange,
  siblingCount = 1,
  showFirstLast = false,
  disabled = false,
  className = '',
}: PaginationProps) {
  const pages = useMemo(() => {
    const range = (start: number, end: number) => {
      return Array.from({ length: end - start + 1 }, (_, i) => start + i);
    };

    const totalNumbers = siblingCount * 2 + 3; // siblings + current + first + last
    const totalBlocks = totalNumbers + 2; // + 2 ellipsis

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

    if (shouldShowLeftDots && shouldShowRightDots) {
      const middleRange = range(leftSiblingIndex, rightSiblingIndex);
      return [1, 'dots', ...middleRange, 'dots', totalPages];
    }

    return range(1, totalPages);
  }, [currentPage, totalPages, siblingCount]);

  const containerStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: '0.25rem',
    listStyle: 'none',
    padding: 0,
    margin: 0,
  };

  const buttonBaseStyle: React.CSSProperties = {
    minWidth: '2.5rem',
    height: '2.5rem',
    padding: '0.5rem',
    border: '1px solid #e0e0e0',
    borderRadius: '0.375rem',
    backgroundColor: '#ffffff',
    color: '#424242',
    cursor: disabled ? 'not-allowed' : 'pointer',
    fontSize: '0.875rem',
    fontWeight: 500,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    transition: 'all 0.2s',
    opacity: disabled ? 0.5 : 1,
  };

  const activeButtonStyle: React.CSSProperties = {
    ...buttonBaseStyle,
    backgroundColor: '#2196f3',
    borderColor: '#2196f3',
    color: '#ffffff',
  };

  const dotsStyle: React.CSSProperties = {
    ...buttonBaseStyle,
    cursor: 'default',
    border: 'none',
  };

  const handlePageChange = (page: number) => {
    if (!disabled && page >= 1 && page <= totalPages && page !== currentPage) {
      onPageChange(page);
    }
  };

  return (
    <nav aria-label="Pagination" className={className}>
      <ul style={containerStyle}>
        {showFirstLast && (
          <li>
            <button
              style={buttonBaseStyle}
              onClick={() => handlePageChange(1)}
              disabled={disabled || currentPage === 1}
              aria-label="First page"
            >
              ««
            </button>
          </li>
        )}

        <li>
          <button
            style={buttonBaseStyle}
            onClick={() => handlePageChange(currentPage - 1)}
            disabled={disabled || currentPage === 1}
            aria-label="Previous page"
          >
            ‹
          </button>
        </li>

        {pages.map((page, index) => {
          if (page === 'dots') {
            return (
              <li key={`dots-${index}`}>
                <span style={dotsStyle}>...</span>
              </li>
            );
          }

          const pageNumber = page as number;
          const isActive = pageNumber === currentPage;

          return (
            <li key={pageNumber}>
              <button
                style={isActive ? activeButtonStyle : buttonBaseStyle}
                onClick={() => handlePageChange(pageNumber)}
                disabled={disabled}
                aria-label={`Page ${pageNumber}`}
                aria-current={isActive ? 'page' : undefined}
              >
                {pageNumber}
              </button>
            </li>
          );
        })}

        <li>
          <button
            style={buttonBaseStyle}
            onClick={() => handlePageChange(currentPage + 1)}
            disabled={disabled || currentPage === totalPages}
            aria-label="Next page"
          >
            ›
          </button>
        </li>

        {showFirstLast && (
          <li>
            <button
              style={buttonBaseStyle}
              onClick={() => handlePageChange(totalPages)}
              disabled={disabled || currentPage === totalPages}
              aria-label="Last page"
            >
              »»
            </button>
          </li>
        )}
      </ul>
    </nav>
  );
}
