import React from 'react';

import { cn } from '../../utils/cn';

/**
 * TimePicker component props
 */
export interface TimePickerProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /**
   * Current selected time (Date object with time set)
   */
  value?: Date | null;

  /**
   * Callback when time changes
   */
  onChange?: (time: Date | null) => void;

  /**
   * Time format (12-hour or 24-hour)
   * @default '12h'
   */
  format?: '12h' | '24h';

  /**
   * Minute step interval
   * @default 1
   */
  minuteStep?: number;

  /**
   * Placeholder text when no time selected
   */
  placeholder?: string;

  /**
   * Whether the picker is disabled
   */
  disabled?: boolean;

  /**
   * Whether the picker is read-only
   */
  readOnly?: boolean;

  /**
   * Size variant
   */
  size?: 'small' | 'medium' | 'large';
}

// Icons
const ClockIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={2}
  >
    <circle cx="12" cy="12" r="10" />
    <polyline points="12 6 12 12 16 14" />
  </svg>
);

const ChevronUpIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={2}
  >
    <path strokeLinecap="round" strokeLinejoin="round" d="M5 15l7-7 7 7" />
  </svg>
);

const ChevronDownIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={2}
  >
    <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
  </svg>
);

const formatTime = (date: Date | null, format: '12h' | '24h'): string => {
  if (!date) return '';

  let hours = date.getHours();
  const minutes = String(date.getMinutes()).padStart(2, '0');

  if (format === '12h') {
    const period = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12 || 12;
    return `${hours}:${minutes} ${period}`;
  }

  return `${String(hours).padStart(2, '0')}:${minutes}`;
};

/**
 * TimePicker component - Time selection with spinners
 */
export const TimePicker = React.forwardRef<HTMLDivElement, TimePickerProps>((props, ref) => {
  const {
    value,
    onChange,
    format = '12h',
    minuteStep = 1,
    placeholder = 'Select time',
    disabled = false,
    readOnly = false,
    size = 'medium',
    className,
    ...rest
  } = props;

  const [isOpen, setIsOpen] = React.useState(false);
  const containerRef = React.useRef<HTMLDivElement>(null);

  // combine forwarded ref and local containerRef
  const setRefs = (node: HTMLDivElement | null) => {
    containerRef.current = node;
    if (!ref) return;
    if (typeof ref === 'function') {
      try {
        ref(node);
      } catch {}
    } else {
      // @ts-ignore assign to forwarded ref object
      (ref as React.MutableRefObject<HTMLDivElement | null>).current = node;
    }
  };

  const [localHours, setLocalHours] = React.useState(() =>
    value ? (format === '12h' ? value.getHours() % 12 || 12 : value.getHours()) : 12
  );
  const [localMinutes, setLocalMinutes] = React.useState(() => (value ? value.getMinutes() : 0));
  const [localPeriod, setLocalPeriod] = React.useState<'AM' | 'PM'>(() =>
    value && value.getHours() >= 12 ? 'PM' : 'AM'
  );

  // Close on outside click
  React.useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isOpen]);

  // Update local state when value changes
  React.useEffect(() => {
    if (value) {
      setLocalHours(format === '12h' ? value.getHours() % 12 || 12 : value.getHours());
      setLocalMinutes(value.getMinutes());
      setLocalPeriod(value.getHours() >= 12 ? 'PM' : 'AM');
    }
  }, [value, format]);

  const sizeClasses = {
    small: 'text-sm px-2 py-1',
    medium: 'text-base px-3 py-2',
    large: 'text-lg px-4 py-3',
  };

  const handleApply = () => {
    let hours24 = localHours;
    if (format === '12h') {
      if (localPeriod === 'PM' && localHours !== 12) {
        hours24 = localHours + 12;
      } else if (localPeriod === 'AM' && localHours === 12) {
        hours24 = 0;
      }
    }

    const newTime = new Date();
    newTime.setHours(hours24, localMinutes, 0, 0);
    onChange?.(newTime);
    setIsOpen(false);
  };

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation();
    onChange?.(null);
  };

  const incrementHours = () => {
    setLocalHours((prev) => {
      const max = format === '12h' ? 12 : 23;
      const min = format === '12h' ? 1 : 0;
      return prev >= max ? min : prev + 1;
    });
  };

  const decrementHours = () => {
    setLocalHours((prev) => {
      const max = format === '12h' ? 12 : 23;
      const min = format === '12h' ? 1 : 0;
      return prev <= min ? max : prev - 1;
    });
  };

  const incrementMinutes = () => {
    setLocalMinutes((prev) => {
      const next = prev + minuteStep;
      return next >= 60 ? 0 : next;
    });
  };

  const decrementMinutes = () => {
    setLocalMinutes((prev) => {
      const next = prev - minuteStep;
      return next < 0 ? 60 - minuteStep : next;
    });
  };

  const togglePeriod = () => {
    setLocalPeriod((prev) => (prev === 'AM' ? 'PM' : 'AM'));
  };

  return (
    <div ref={setRefs} className={cn('relative inline-block', className)} {...rest}>
      {/* Input */}
      <button
        type="button"
        onClick={() => !disabled && !readOnly && setIsOpen(!isOpen)}
        disabled={disabled}
        className={cn(
          'flex items-center gap-2 w-full border rounded transition-colors',
          'focus:outline-none focus:ring-2 focus:ring-primary-500',
          sizeClasses[size],
          disabled
            ? 'bg-grey-100 text-grey-400 cursor-not-allowed'
            : readOnly
              ? 'bg-grey-50 cursor-default'
              : 'bg-white hover:border-grey-400 cursor-pointer',
          value ? 'text-grey-900 border-grey-300' : 'text-grey-500 border-grey-300'
        )}
      >
        <ClockIcon className="w-5 h-5 flex-shrink-0 text-grey-400" />
        <span className="flex-1 text-left">{value ? formatTime(value, format) : placeholder}</span>
        {value && !disabled && !readOnly && (
          <span
            role="button"
            tabIndex={0}
            onClick={handleClear}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                handleClear(e as unknown as React.MouseEvent);
              }
            }}
            className="flex-shrink-0 text-grey-400 hover:text-grey-600"
            aria-label="Clear time"
          >
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
              <path
                fillRule="evenodd"
                d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
                clipRule="evenodd"
              />
            </svg>
          </span>
        )}
      </button>

      {/* Time Picker Popup */}
      {isOpen && (
        <div className="absolute z-50 mt-2 bg-white border border-grey-300 rounded-lg shadow-lg p-4">
          <div className="flex gap-2 items-center">
            {/* Hours Column */}
            <div className="flex flex-col items-center gap-1">
              <button
                type="button"
                onClick={incrementHours}
                className="p-1 rounded hover:bg-grey-100 text-grey-600"
              >
                <ChevronUpIcon className="w-4 h-4" />
              </button>
              <div className="w-16 py-2 text-center font-semibold text-lg bg-grey-50 rounded">
                {String(localHours).padStart(2, '0')}
              </div>
              <button
                type="button"
                onClick={decrementHours}
                className="p-1 rounded hover:bg-grey-100 text-grey-600"
              >
                <ChevronDownIcon className="w-4 h-4" />
              </button>
              <div className="text-xs text-grey-500 mt-1">Hours</div>
            </div>

            <div className="text-2xl font-bold text-grey-400">:</div>

            {/* Minutes Column */}
            <div className="flex flex-col items-center gap-1">
              <button
                type="button"
                onClick={incrementMinutes}
                className="p-1 rounded hover:bg-grey-100 text-grey-600"
              >
                <ChevronUpIcon className="w-4 h-4" />
              </button>
              <div className="w-16 py-2 text-center font-semibold text-lg bg-grey-50 rounded">
                {String(localMinutes).padStart(2, '0')}
              </div>
              <button
                type="button"
                onClick={decrementMinutes}
                className="p-1 rounded hover:bg-grey-100 text-grey-600"
              >
                <ChevronDownIcon className="w-4 h-4" />
              </button>
              <div className="text-xs text-grey-500 mt-1">Minutes</div>
            </div>

            {/* AM/PM Column (12h format only) */}
            {format === '12h' && (
              <>
                <div className="w-px h-24 bg-grey-300 mx-1" />
                <div className="flex flex-col items-center gap-1">
                  <button
                    type="button"
                    onClick={togglePeriod}
                    className="p-1 rounded hover:bg-grey-100 text-grey-600"
                  >
                    <ChevronUpIcon className="w-4 h-4" />
                  </button>
                  <div className="w-16 py-2 text-center font-semibold text-lg bg-grey-50 rounded">
                    {localPeriod}
                  </div>
                  <button
                    type="button"
                    onClick={togglePeriod}
                    className="p-1 rounded hover:bg-grey-100 text-grey-600"
                  >
                    <ChevronDownIcon className="w-4 h-4" />
                  </button>
                  <div className="text-xs text-grey-500 mt-1">Period</div>
                </div>
              </>
            )}
          </div>

          {/* Actions */}
          <div className="flex gap-2 mt-4 pt-4 border-t border-grey-200">
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              className="flex-1 px-3 py-1.5 text-sm text-grey-700 hover:bg-grey-100 rounded transition-colors"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleApply}
              className="flex-1 px-3 py-1.5 text-sm bg-primary-500 text-white hover:bg-primary-600 rounded transition-colors"
            >
              Apply
            </button>
          </div>
        </div>
      )}
    </div>
  );
});

TimePicker.displayName = 'TimePicker';
