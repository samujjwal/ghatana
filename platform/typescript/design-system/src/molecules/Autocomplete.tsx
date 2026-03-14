import * as React from 'react';

export interface AutocompleteOption {
  label: string;
  value: string;
  disabled?: boolean;
  group?: string;
}

export interface AutocompleteProps<T extends AutocompleteOption = AutocompleteOption> {
  /** Available options */
  options: T[];
  /** Current value (controlled) */
  value?: T | T[] | null;
  /** Default value (uncontrolled) */
  defaultValue?: T | T[] | null;
  /** Called when selection changes */
  onChange?: (event: React.SyntheticEvent, value: T | T[] | null) => void;
  /** Called when input text changes */
  onInputChange?: (event: React.SyntheticEvent, value: string) => void;
  /** Input value (controlled) */
  inputValue?: string;
  /** Whether multiple selections are allowed */
  multiple?: boolean;
  /** Whether the user can type to filter */
  freeSolo?: boolean;
  /** Placeholder text */
  placeholder?: string;
  /** Label text */
  label?: string;
  /** Whether the autocomplete is disabled */
  disabled?: boolean;
  /** Whether to show a loading indicator */
  loading?: boolean;
  /** Loading text */
  loadingText?: string;
  /** Text when no options match */
  noOptionsText?: string;
  /** Custom filter function */
  filterOptions?: (options: T[], state: { inputValue: string }) => T[];
  /** Custom option renderer */
  renderOption?: (props: React.HTMLAttributes<HTMLLIElement>, option: T) => React.ReactNode;
  /** Custom input renderer */
  renderInput?: (params: { ref: React.Ref<HTMLInputElement>; value: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void; placeholder?: string; disabled?: boolean }) => React.ReactNode;
  /** Get the label for an option */
  getOptionLabel?: (option: T) => string;
  /** Whether the field takes full width */
  fullWidth?: boolean;
  /** Size */
  size?: 'small' | 'medium';
  /** Additional CSS classes */
  className?: string;
  /** Additional inline styles */
  style?: React.CSSProperties;
  /** sx compatibility (converted to style) */
  sx?: React.CSSProperties;
}

/**
 * Autocomplete component — provides suggestions as the user types.
 * Lightweight Tailwind implementation replacing MUI Autocomplete.
 */
export function Autocomplete<T extends AutocompleteOption = AutocompleteOption>({
  options,
  value: controlledValue,
  onChange,
  onInputChange,
  inputValue: controlledInputValue,
  multiple = false,
  placeholder,
  label,
  disabled = false,
  loading = false,
  loadingText = 'Loading…',
  noOptionsText = 'No options',
  filterOptions,
  getOptionLabel = (o: T) => o.label,
  fullWidth = false,
  size = 'medium',
  className,
  style,
  sx,
}: AutocompleteProps<T>) {
  const [internalInput, setInternalInput] = React.useState('');
  const [open, setOpen] = React.useState(false);
  const [highlightedIndex, setHighlightedIndex] = React.useState(-1);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const listRef = React.useRef<HTMLUListElement>(null);

  const inputValue = controlledInputValue ?? internalInput;

  const filteredOptions = React.useMemo(() => {
    if (filterOptions) {
      return filterOptions(options, { inputValue });
    }
    if (!inputValue) return options;
    const lower = inputValue.toLowerCase();
    return options.filter(o => getOptionLabel(o).toLowerCase().includes(lower));
  }, [options, inputValue, filterOptions, getOptionLabel]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value;
    setInternalInput(v);
    onInputChange?.(e, v);
    setOpen(true);
    setHighlightedIndex(-1);
  };

  const handleSelect = (e: React.SyntheticEvent, option: T) => {
    if (multiple) {
      const current = (controlledValue as T[] | null) ?? [];
      const exists = current.find(c => c.value === option.value);
      const next = exists ? current.filter(c => c.value !== option.value) : [...current, option];
      onChange?.(e, next);
    } else {
      onChange?.(e, option);
      setInternalInput(getOptionLabel(option));
      setOpen(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedIndex(i => Math.min(i + 1, filteredOptions.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedIndex(i => Math.max(i - 1, 0));
    } else if (e.key === 'Enter' && highlightedIndex >= 0) {
      e.preventDefault();
      const option = filteredOptions[highlightedIndex];
      if (option && !option.disabled) handleSelect(e, option);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  const sizeClass = size === 'small' ? 'px-2 py-1 text-sm' : 'px-3 py-2 text-base';

  return (
    <div
      className={`relative ${fullWidth ? 'w-full' : 'inline-block'} ${className ?? ''}`}
      style={{ ...style, ...sx }}
    >
      {label && (
        <label className="mb-1 block text-sm font-medium text-neutral-700 dark:text-neutral-300">
          {label}
        </label>
      )}
      <input
        ref={inputRef}
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-autocomplete="list"
        aria-haspopup="listbox"
        value={inputValue}
        onChange={handleInputChange}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        disabled={disabled}
        className={`w-full rounded-md border border-neutral-300 bg-white ${sizeClass} text-neutral-900 transition-colors focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 disabled:opacity-50 dark:border-neutral-600 dark:bg-neutral-800 dark:text-neutral-100`}
      />
      {open && (
        <ul
          ref={listRef}
          role="listbox"
          className="absolute z-50 mt-1 max-h-60 w-full overflow-auto rounded-md border border-neutral-200 bg-white py-1 shadow-lg dark:border-neutral-700 dark:bg-neutral-800"
        >
          {loading ? (
            <li className="px-3 py-2 text-sm text-neutral-500">{loadingText}</li>
          ) : filteredOptions.length === 0 ? (
            <li className="px-3 py-2 text-sm text-neutral-500">{noOptionsText}</li>
          ) : (
            filteredOptions.map((option, index) => (
              <li
                key={option.value}
                role="option"
                aria-selected={index === highlightedIndex}
                aria-disabled={option.disabled}
                onClick={(e) => !option.disabled && handleSelect(e, option)}
                className={`cursor-pointer ${sizeClass} transition-colors ${
                  index === highlightedIndex
                    ? 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                    : 'text-neutral-700 hover:bg-neutral-100 dark:text-neutral-200 dark:hover:bg-neutral-700'
                } ${option.disabled ? 'pointer-events-none opacity-50' : ''}`}
              >
                {getOptionLabel(option)}
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}

Autocomplete.displayName = 'Autocomplete';
