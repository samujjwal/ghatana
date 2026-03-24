import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * Transfer list item
 */
export interface TransferListItem {
  /**
   * Unique identifier
   */
  id: string | number;

  /**
   * Display label
   */
  label: string;

  /**
   * Optional description
   */
  description?: string;

  /**
   * Whether the item is disabled
   */
  disabled?: boolean;

  /**
   * Any additional data
   */
  data?: unknown;
}

/**
 * TransferList component props
 */
export interface TransferListProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /**
   * Items in the left list
   */
  leftItems: TransferListItem[];

  /**
   * Items in the right list
   */
  rightItems: TransferListItem[];

  /**
   * Called when items are transferred
   */
  onChange?: (leftItems: TransferListItem[], rightItems: TransferListItem[]) => void;

  /**
   * Left list title
   * @default 'Available'
   */
  leftTitle?: string;

  /**
   * Right list title
   * @default 'Selected'
   */
  rightTitle?: string;

  /**
   * Enable search functionality
   * @default false
   */
  searchable?: boolean;

  /**
   * Custom render function for items
   */
  renderItem?: (item: TransferListItem) => React.ReactNode;

  /**
   * Height of each list
   * @default '400px'
   */
  height?: string | number;

  /**
   * Whether the component is disabled
   * @default false
   */
  disabled?: boolean;
}

/**
 * Search icon
 */
const SearchIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={2}
  >
    <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
  </svg>
);

/**
 * Arrow icons for transfer buttons
 */
const ArrowRightIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
  </svg>
);

const ArrowLeftIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
  </svg>
);

/**
 * TransferList component for moving items between two lists
 *
 * @example
 * ```tsx
 * const [left, setLeft] = React.useState(leftItems);
 * const [right, setRight] = React.useState(rightItems);
 *
 * <TransferList
 *   leftItems={left}
 *   rightItems={right}
 *   onChange={(newLeft, newRight) => {
 *     setLeft(newLeft);
 *     setRight(newRight);
 *   }}
 * />
 * ```
 */
export const TransferList = React.forwardRef<HTMLDivElement, TransferListProps>(
  (
    {
      leftItems,
      rightItems,
      onChange,
      leftTitle = 'Available',
      rightTitle = 'Selected',
      searchable = false,
      renderItem,
      height = '400px',
      disabled = false,
      className,
      ...props
    },
    ref
  ) => {
    const [leftChecked, setLeftChecked] = React.useState<Set<string | number>>(new Set());
    const [rightChecked, setRightChecked] = React.useState<Set<string | number>>(new Set());
    const [leftSearch, setLeftSearch] = React.useState('');
    const [rightSearch, setRightSearch] = React.useState('');

    // Filter items based on search
    const filterItems = (items: TransferListItem[], search: string) => {
      if (!search) return items;
      return items.filter((item) =>
        item.label.toLowerCase().includes(search.toLowerCase())
      );
    };

    const filteredLeftItems = filterItems(leftItems, leftSearch);
    const filteredRightItems = filterItems(rightItems, rightSearch);

    // Toggle item selection
    const toggleItem = (id: string | number, isLeft: boolean) => {
      if (disabled) return;

      if (isLeft) {
        setLeftChecked((prev) => {
          const newSet = new Set(prev);
          if (newSet.has(id)) {
            newSet.delete(id);
          } else {
            newSet.add(id);
          }
          return newSet;
        });
      } else {
        setRightChecked((prev) => {
          const newSet = new Set(prev);
          if (newSet.has(id)) {
            newSet.delete(id);
          } else {
            newSet.add(id);
          }
          return newSet;
        });
      }
    };

    // Select/deselect all
    const handleToggleAll = (isLeft: boolean) => {
      if (disabled) return;

      const items = isLeft ? filteredLeftItems : filteredRightItems;
      const checkedSet = isLeft ? leftChecked : rightChecked;
      const setChecked = isLeft ? setLeftChecked : setRightChecked;

      const enabledIds = items.filter((item) => !item.disabled).map((item) => item.id);

      if (enabledIds.every((id) => checkedSet.has(id))) {
        // Deselect all
        setChecked(new Set());
      } else {
        // Select all enabled
        setChecked(new Set(enabledIds));
      }
    };

    // Transfer items
    const handleTransferRight = () => {
      if (disabled) return;

      const itemsToTransfer = leftItems.filter((item) => leftChecked.has(item.id));
      const newLeft = leftItems.filter((item) => !leftChecked.has(item.id));
      const newRight = [...rightItems, ...itemsToTransfer];

      setLeftChecked(new Set());
      onChange?.(newLeft, newRight);
    };

    const handleTransferLeft = () => {
      if (disabled) return;

      const itemsToTransfer = rightItems.filter((item) => rightChecked.has(item.id));
      const newRight = rightItems.filter((item) => !rightChecked.has(item.id));
      const newLeft = [...leftItems, ...itemsToTransfer];

      setRightChecked(new Set());
      onChange?.(newLeft, newRight);
    };

    // Render a single list
    const renderList = (
      items: TransferListItem[],
      filteredItems: TransferListItem[],
      checkedSet: Set<string | number>,
      isLeft: boolean,
      title: string,
      search: string,
      setSearch: (value: string) => void
    ) => {
      const allChecked = filteredItems.length > 0 && filteredItems.every((item) => item.disabled || checkedSet.has(item.id));
      const someChecked = filteredItems.some((item) => checkedSet.has(item.id));

      return (
        <div className="flex-1 flex flex-col border border-grey-300 rounded-lg overflow-hidden bg-white">
          {/* Header */}
          <div className="px-4 py-3 bg-grey-50 border-b border-grey-300">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-grey-900">{title}</h3>
              <span className="text-xs text-grey-500">
                {checkedSet.size}/{items.length}
              </span>
            </div>

            {searchable && (
              <div className="relative mt-2">
                <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-grey-400" />
                <input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search..."
                  disabled={disabled}
                  className="w-full pl-9 pr-3 py-1.5 text-sm border border-grey-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed"
                />
              </div>
            )}
          </div>

          {/* Select All */}
          <div className="px-4 py-2 bg-grey-50 border-b border-grey-200">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={allChecked}
                onChange={() => handleToggleAll(isLeft)}
                disabled={disabled || filteredItems.length === 0}
                className="w-4 h-4 text-primary-500 border-grey-300 rounded focus:ring-primary-500 disabled:cursor-not-allowed"
                {...(someChecked && !allChecked && { 'data-indeterminate': 'true' } as unknown)}
              />
              <span className="text-sm text-grey-700">Select all</span>
            </label>
          </div>

          {/* Items List */}
          <div
            className="flex-1 overflow-y-auto"
            style={{ height }}
          >
            {filteredItems.length === 0 ? (
              <div className="flex items-center justify-center h-full text-sm text-grey-500">
                {search ? 'No items found' : 'No items'}
              </div>
            ) : (
              <div className="divide-y divide-grey-200">
                {filteredItems.map((item) => {
                  const isChecked = checkedSet.has(item.id);

                  return (
                    <label
                      key={item.id}
                      className={cn(
                        'flex items-start gap-3 px-4 py-3 hover:bg-grey-50 transition-colors',
                        item.disabled && 'opacity-50 cursor-not-allowed',
                        !item.disabled && 'cursor-pointer'
                      )}
                    >
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={() => toggleItem(item.id, isLeft)}
                        disabled={disabled || item.disabled}
                        className="mt-0.5 w-4 h-4 text-primary-500 border-grey-300 rounded focus:ring-primary-500 disabled:cursor-not-allowed"
                      />
                      <div className="flex-1 min-w-0">
                        {renderItem ? (
                          renderItem(item)
                        ) : (
                          <>
                            <div className="text-sm font-medium text-grey-900 truncate">
                              {item.label}
                            </div>
                            {item.description && (
                              <div className="text-xs text-grey-500 mt-0.5 truncate">
                                {item.description}
                              </div>
                            )}
                          </>
                        )}
                      </div>
                    </label>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      );
    };

    return (
      <div ref={ref} className={cn('flex gap-4 items-center', className)} {...props}>
        {/* Left List */}
        {renderList(
          leftItems,
          filteredLeftItems,
          leftChecked,
          true,
          leftTitle,
          leftSearch,
          setLeftSearch
        )}

        {/* Transfer Buttons */}
        <div className="flex flex-col gap-2">
          <button
            type="button"
            onClick={handleTransferRight}
            disabled={disabled || leftChecked.size === 0}
            className="p-2 bg-primary-500 text-white rounded-md hover:bg-primary-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            aria-label="Transfer selected items to right"
          >
            <ArrowRightIcon className="w-5 h-5" />
          </button>
          <button
            type="button"
            onClick={handleTransferLeft}
            disabled={disabled || rightChecked.size === 0}
            className="p-2 bg-primary-500 text-white rounded-md hover:bg-primary-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            aria-label="Transfer selected items to left"
          >
            <ArrowLeftIcon className="w-5 h-5" />
          </button>
        </div>

        {/* Right List */}
        {renderList(
          rightItems,
          filteredRightItems,
          rightChecked,
          false,
          rightTitle,
          rightSearch,
          setRightSearch
        )}
      </div>
    );
  }
);

TransferList.displayName = 'TransferList';
