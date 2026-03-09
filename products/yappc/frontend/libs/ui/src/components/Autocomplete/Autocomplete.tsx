import * as React from 'react';

import { cn } from '../../utils/cn';
import type { AutocompleteOption } from '../Input/Input';
export type { AutocompleteOption } from '../Input/Input';

/**
 *
 */
export interface AutocompleteProps {
  /** Options to display in the dropdown */
  options: AutocompleteOption[];
  /** Current selected value(s) */
  value?: string | number | (string | number)[];
  /** Callback when value changes */
  onChange?: (value: string | number | (string | number)[]) => void;
  /** Placeholder text */
  placeholder?: string;
  /** Enable multiple selection */
  multiple?: boolean;
  /** Disable the autocomplete */
  disabled?: boolean;
  /** Loading state */
  loading?: boolean;
  /** Size variant */
  size?: 'small' | 'medium' | 'large';
  /** Error state */
  error?: boolean;
  /** Helper text */
  helperText?: string;
  /** Label */
  label?: string;
  /** Clear button */
  clearable?: boolean;
  /** Custom filter function */
  filterFunction?: (option: AutocompleteOption, query: string) => boolean;
  /** No options message */
  noOptionsText?: string;
  /** Loading text */
  loadingText?: string;
  /** className for root element */
  className?: string;
  /** Open dropdown on focus */
  openOnFocus?: boolean;
  /** Auto-highlight first option */
  autoHighlight?: boolean;
  /** Max height for dropdown */
  maxHeight?: string | number;
}

/**
 * Autocomplete component with search and filtering
 * 
 * Pure Tailwind CSS implementation with keyboard navigation.
 * Supports single/multiple selection, async loading, custom filtering.
 * 
 * @example
 * ```tsx
 * <Autocomplete
 *   options={[
 *     { value: 1, label: 'Apple' },
 *     { value: 2, label: 'Banana' },
 *     { value: 3, label: 'Cherry' }
 *   ]}
 *   value={selectedValue}
 *   onChange={setSelectedValue}
 *   placeholder="Select fruit..."
 * />
 * ```
 */
export const Autocomplete = React.forwardRef<HTMLDivElement, AutocompleteProps>(
  (
    {
      options = [],
      value,
      onChange,
      placeholder = 'Search...',
      multiple = false,
      disabled = false,
      loading = false,
      size = 'medium',
      error = false,
      helperText,
      label,
      clearable = true,
      filterFunction,
      noOptionsText = 'No options',
      loadingText = 'Loading...',
      className,
      openOnFocus = true,
      autoHighlight = true,
      maxHeight = 300,
    },
    ref
  ) => {
    const [open, setOpen] = React.useState(false);
    const [inputValue, setInputValue] = React.useState('');
    const [highlightedIndex, setHighlightedIndex] = React.useState(-1);
    const inputRef = React.useRef<HTMLInputElement>(null);
    const listRef = React.useRef<HTMLDivElement>(null);
    const containerRef = React.useRef<HTMLDivElement>(null);

    // Default filter function
    const defaultFilter = React.useCallback(
      (option: AutocompleteOption, query: string) => {
        if (!query) return true;
        return option.label?.toLowerCase().includes(query.toLowerCase()) ?? false;
      },
      []
    );

    const filter = filterFunction || defaultFilter;

    // Filter options based on input
    const filteredOptions = React.useMemo(() => {
      return options.filter((option) => filter(option, inputValue));
    }, [options, inputValue, filter]);

    // Get selected options
    const selectedOptions = React.useMemo(() => {
      const values = Array.isArray(value) ? value : value !== undefined ? [value] : [];
      return options.filter((opt) => values.includes(opt.value));
    }, [options, value]);

    // Handle selection
    const handleSelect = React.useCallback(
      (option: AutocompleteOption) => {
        if (option.disabled) return;

        if (multiple) {
          const values = Array.isArray(value) ? value : [];
          const isSelected = values.includes(option.value);
          const newValue = isSelected
            ? values.filter((v) => v !== option.value)
            : [...values, option.value];
          onChange?.(newValue);
        } else {
          onChange?.(option.value);
          setOpen(false);
          setInputValue('');
        }
      },
      [multiple, value, onChange]
    );

    // Handle keyboard navigation
    const handleKeyDown = React.useCallback(
      (e: React.KeyboardEvent) => {
        if (disabled) return;

        switch (e.key) {
          case 'ArrowDown':
            e.preventDefault();
            if (!open) {
              setOpen(true);
            } else {
              setHighlightedIndex((prev) =>
                prev < filteredOptions.length - 1 ? prev + 1 : prev
              );
            }
            break;

          case 'ArrowUp':
            e.preventDefault();
            if (open) {
              setHighlightedIndex((prev) => (prev > 0 ? prev - 1 : 0));
            }
            break;

          case 'Enter':
            e.preventDefault();
            if (open && highlightedIndex >= 0 && filteredOptions[highlightedIndex]) {
              handleSelect(filteredOptions[highlightedIndex]);
            }
            break;

          case 'Escape':
            e.preventDefault();
            setOpen(false);
            setHighlightedIndex(-1);
            break;

          case 'Tab':
            setOpen(false);
            setHighlightedIndex(-1);
            break;

          default:
            break;
        }
      },
      [disabled, open, highlightedIndex, filteredOptions, handleSelect]
    );

    // Close on click outside
    React.useEffect(() => {
      const handleClickOutside = (event: MouseEvent) => {
        if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
          setOpen(false);
          setHighlightedIndex(-1);
        }
      };

      if (open) {
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
      }

      return undefined;
    }, [open]);

    // Auto-highlight first option
    React.useEffect(() => {
      if (open && autoHighlight && filteredOptions.length > 0) {
        setHighlightedIndex(0);
      }
    }, [open, autoHighlight, filteredOptions.length]);

    // Scroll highlighted item into view
    React.useEffect(() => {
      if (open && highlightedIndex >= 0 && listRef.current) {
        const highlightedElement = listRef.current.children[highlightedIndex] as HTMLElement;
        if (highlightedElement) {
          highlightedElement.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        }
      }
    }, [highlightedIndex, open]);

    // Size-based classes
    const sizeClasses = {
      small: {
        input: 'px-2 py-1 text-xs',
        chip: 'px-1.5 py-0.5 text-xs gap-1',
        item: 'px-2 py-1 text-xs',
      },
      medium: {
        input: 'px-3 py-2 text-sm',
        chip: 'px-2 py-1 text-sm gap-1.5',
        item: 'px-3 py-2 text-sm',
      },
      large: {
        input: 'px-4 py-3 text-base',
        chip: 'px-2.5 py-1.5 text-base gap-2',
        item: 'px-4 py-3 text-base',
      },
    };

    const currentSize = sizeClasses[size];

    // Clear all values
    const handleClear = () => {
      onChange?.(multiple ? [] : '');
      setInputValue('');
      inputRef.current?.focus();
    };

    // Remove single chip in multiple mode
    const handleRemoveChip = (optionValue: string | number) => {
      if (multiple && Array.isArray(value)) {
        onChange?.(value.filter((v) => v !== optionValue));
      }
    };

    return (
      <div ref={ref} className={cn('w-full', className)}>
        {label && (
          <label className="block text-sm font-medium text-grey-700 dark:text-grey-300 mb-1">
            {label}
          </label>
        )}

        <div ref={containerRef} className="relative">
          {/* Input container */}
          <div
            className={cn(
              'flex items-center flex-wrap gap-1 border rounded-md transition-colors',
              'bg-white dark:bg-grey-900',
              error
                ? 'border-error-500 focus-within:ring-2 focus-within:ring-error-500/20'
                : open
                  ? 'border-primary-500 ring-2 ring-primary-500/20'
                  : 'border-grey-300 dark:border-grey-700 hover:border-grey-400',
              disabled && 'opacity-50 cursor-not-allowed bg-grey-100 dark:bg-grey-800',
              currentSize.input
            )}
          >
            {/* Chips for multiple selection */}
            {multiple && selectedOptions.length > 0 && (
              <div className="flex flex-wrap gap-1">
                {selectedOptions.map((option) => (
                  <span
                    key={option.value}
                    className={cn(
                      'inline-flex items-center rounded bg-primary-100 dark:bg-primary-900/30',
                      'text-primary-900 dark:text-primary-100',
                      currentSize.chip
                    )}
                  >
                    <span>{option.label}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveChip(option.value)}
                      disabled={disabled}
                      className={cn(
                        'inline-flex items-center justify-center w-4 h-4 rounded-full',
                        'hover:bg-primary-200 dark:hover:bg-primary-800/50',
                        'transition-colors',
                        'disabled:opacity-50 disabled:cursor-not-allowed'
                      )}
                    >
                      <svg
                        width="10"
                        height="10"
                        viewBox="0 0 10 10"
                        fill="none"
                        xmlns="http://www.w3.org/2000/svg"
                      >
                        <path
                          d="M8 2L2 8M2 2L8 8"
                          stroke="currentColor"
                          strokeWidth="1.5"
                          strokeLinecap="round"
                        />
                      </svg>
                    </button>
                  </span>
                ))}
              </div>
            )}

            {/* Input field */}
            <input
              ref={inputRef}
              type="text"
              value={inputValue}
              onChange={(e) => {
                setInputValue(e.target.value);
                if (!open) setOpen(true);
              }}
              onFocus={() => {
                if (openOnFocus && !disabled) {
                  setOpen(true);
                }
              }}
              onKeyDown={handleKeyDown}
              placeholder={placeholder}
              disabled={disabled}
              className={cn(
                'flex-1 min-w-[120px] outline-none bg-transparent',
                'text-grey-900 dark:text-grey-100',
                'placeholder:text-grey-400 dark:placeholder:text-grey-500',
                'disabled:cursor-not-allowed'
              )}
              aria-autocomplete="list"
              aria-controls={open ? 'autocomplete-listbox' : undefined}
              aria-expanded={open}
              aria-activedescendant={
                highlightedIndex >= 0 ? `autocomplete-option-${highlightedIndex}` : undefined
              }
              role="combobox"
            />

            {/* Clear button */}
            {clearable && (value || inputValue) && !disabled && (
              <button
                type="button"
                onClick={handleClear}
                className={cn(
                  'inline-flex items-center justify-center w-5 h-5',
                  'text-grey-400 hover:text-grey-600',
                  'dark:text-grey-500 dark:hover:text-grey-300',
                  'transition-colors'
                )}
                aria-label="Clear"
              >
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 14 14"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                    d="M11 3L3 11M3 3L11 11"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                  />
                </svg>
              </button>
            )}

            {/* Dropdown toggle */}
            <button
              type="button"
              onClick={() => !disabled && setOpen(!open)}
              disabled={disabled}
              className={cn(
                'inline-flex items-center justify-center w-5 h-5',
                'text-grey-600 dark:text-grey-400',
                'transition-transform',
                open && 'rotate-180'
              )}
              aria-label="Toggle dropdown"
            >
              <svg
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M3 5L7 9L11 5"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </button>
          </div>

          {/* Dropdown popup */}
          {open && (
            <div
              ref={listRef}
              id="autocomplete-listbox"
              role="listbox"
              aria-multiselectable={multiple}
              className={cn(
                'absolute z-50 w-full mt-1',
                'rounded-md border border-grey-200 dark:border-grey-700',
                'bg-white dark:bg-grey-900',
                'shadow-lg',
                'overflow-auto',
                'animate-in fade-in-0 zoom-in-95'
              )}
              style={{ maxHeight }}
            >
              {/* Loading state */}
              {loading && (
                <div className="flex items-center justify-center py-8 text-grey-500">
                  <svg
                    className="animate-spin h-5 w-5 mr-2"
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                  >
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                    />
                  </svg>
                  <span className="text-sm">{loadingText}</span>
                </div>
              )}

              {/* Empty state */}
              {!loading && filteredOptions.length === 0 && (
                <div className="flex items-center justify-center py-8 text-grey-500 dark:text-grey-400 text-sm">
                  {noOptionsText}
                </div>
              )}

              {/* Options list */}
              {!loading &&
                filteredOptions.map((option, index) => {
                  const isSelected = Array.isArray(value)
                    ? value.includes(option.value)
                    : value === option.value;
                  const isHighlighted = index === highlightedIndex;

                  return (
                    <div
                      key={option.value}
                      id={`autocomplete-option-${index}`}
                      role="option"
                      aria-selected={isSelected}
                      aria-disabled={option.disabled}
                      onClick={() => !option.disabled && handleSelect(option)}
                      onMouseEnter={() => setHighlightedIndex(index)}
                      className={cn(
                        'flex items-center justify-between cursor-pointer',
                        'transition-colors',
                        isHighlighted && 'bg-primary-50 dark:bg-primary-900/20',
                        isSelected && 'bg-primary-100 dark:bg-primary-900/30',
                        option.disabled && 'opacity-50 cursor-not-allowed',
                        currentSize.item
                      )}
                    >
                      <span className="flex-1 text-grey-900 dark:text-grey-100">
                        {option.label}
                      </span>

                      {/* Selected indicator */}
                      {isSelected && (
                        <svg
                          width="16"
                          height="16"
                          viewBox="0 0 16 16"
                          fill="none"
                          xmlns="http://www.w3.org/2000/svg"
                          className="text-primary-600 dark:text-primary-400"
                        >
                          <path
                            d="M13.3333 4L6 11.3333L2.66667 8"
                            stroke="currentColor"
                            strokeWidth="2"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          />
                        </svg>
                      )}
                    </div>
                  );
                })}
            </div>
          )}
        </div>

        {/* Helper text */}
        {helperText && (
          <p
            className={cn(
              'mt-1 text-xs',
              error
                ? 'text-error-600 dark:text-error-400'
                : 'text-grey-500 dark:text-grey-400'
            )}
          >
            {helperText}
          </p>
        )}
      </div>
    );
  }
);

Autocomplete.displayName = 'Autocomplete';
