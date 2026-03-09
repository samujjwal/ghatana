import React from 'react';

import { cn } from '../../utils/cn';

/**
 * Represents a calendar date
 */
export interface CalendarDate {
  year: number;
  month: number; // 0-11
  day: number;
}

/**
 * DatePicker component props
 */
export interface DatePickerProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /**
   * Current selected date
   */
  value?: Date | null;

  /**
   * Callback when date changes
   */
  onChange?: (date: Date | null) => void;

  /**
   * Minimum selectable date
   */
  minDate?: Date;

  /**
   * Maximum selectable date
   */
  maxDate?: Date;

  /**
   * Dates that should be disabled
   */
  disabledDates?: Date[];

  /**
   * Custom function to determine if a date is disabled
   */
  shouldDisableDate?: (date: Date) => boolean;

  /**
   * Format for displaying the selected date
   * @default 'MM/DD/YYYY'
   */
  format?: string;

  /**
   * Placeholder text when no date selected
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

  /**
   * Whether to show week numbers
   */
  showWeekNumbers?: boolean;

  /**
   * First day of week (0 = Sunday, 1 = Monday, etc.)
   * @default 0
   */
  firstDayOfWeek?: number;
}

// Helper functions
const MONTHS = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

const DAYS_SHORT = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

const isSameDay = (date1: Date | null, date2: Date | null): boolean => {
  if (!date1 || !date2) return false;
  return (
    date1.getFullYear() === date2.getFullYear() &&
    date1.getMonth() === date2.getMonth() &&
    date1.getDate() === date2.getDate()
  );
};

const isDateDisabled = (
  date: Date,
  minDate?: Date,
  maxDate?: Date,
  disabledDates?: Date[],
  shouldDisableDate?: (date: Date) => boolean
): boolean => {
  if (shouldDisableDate?.(date)) return true;
  if (minDate && date < minDate) return true;
  if (maxDate && date > maxDate) return true;
  if (disabledDates?.some((d) => isSameDay(d, date))) return true;
  return false;
};

const formatDate = (date: Date | null, format: string): string => {
  if (!date) return '';

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('M', String(date.getMonth() + 1))
    .replace('D', String(date.getDate()));
};

const getDaysInMonth = (year: number, month: number): number => {
  return new Date(year, month + 1, 0).getDate();
};

const getFirstDayOfMonth = (year: number, month: number): number => {
  return new Date(year, month, 1).getDay();
};

const getWeekNumber = (date: Date): number => {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
};

// Icons
const ChevronLeftIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={2}
  >
    <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
  </svg>
);

const ChevronRightIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={2}
  >
    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
  </svg>
);

const CalendarIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={2}
  >
    <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
    <line x1="16" y1="2" x2="16" y2="6" />
    <line x1="8" y1="2" x2="8" y2="6" />
    <line x1="3" y1="10" x2="21" y2="10" />
  </svg>
);

/**
 * DatePicker component - Calendar-based date selection
 */
export const DatePicker = React.forwardRef<HTMLDivElement, DatePickerProps>((props, ref) => {
  const {
    value,
    onChange,
    minDate,
    maxDate,
    disabledDates,
    shouldDisableDate,
    format = 'MM/DD/YYYY',
    placeholder = 'Select date',
    disabled = false,
    readOnly = false,
    size = 'medium',
    showWeekNumbers = false,
    firstDayOfWeek = 0,
    className,
    ...rest
  } = props;

  const [isOpen, setIsOpen] = React.useState(false);
  const [viewDate, setViewDate] = React.useState<Date>(() => value || new Date());
  const containerRef = React.useRef<HTMLDivElement>(null);

  // combine forwarded ref and local containerRef
  const setRefs = (node: HTMLDivElement | null) => {
    containerRef.current = node;
    if (!ref) return;
    if (typeof ref === 'function') {
      try {
        ref(node);
      } catch {
        // ignore
      }
    } else {
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore - assign to forwarded ref object
      (ref as React.MutableRefObject<HTMLDivElement | null>).current = node;
    }
  };

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

    return undefined;
  }, [isOpen]);

  // Size classes
  const sizeClasses = {
    small: 'text-sm px-2 py-1',
    medium: 'text-base px-3 py-2',
    large: 'text-lg px-4 py-3',
  };

  // Generate calendar days
  const generateCalendarDays = (): (Date | null)[] => {
    const year = viewDate.getFullYear();
    const month = viewDate.getMonth();
    const daysInMonth = getDaysInMonth(year, month);
    const firstDay = (getFirstDayOfMonth(year, month) - firstDayOfWeek + 7) % 7;

    const days: (Date | null)[] = [];

    // Empty cells before month starts
    for (let i = 0; i < firstDay; i++) {
      days.push(null);
    }

    // Days in month
    for (let day = 1; day <= daysInMonth; day++) {
      days.push(new Date(year, month, day));
    }

    return days;
  };

  const handlePrevMonth = () => {
    setViewDate(new Date(viewDate.getFullYear(), viewDate.getMonth() - 1, 1));
  };

  const handleNextMonth = () => {
    setViewDate(new Date(viewDate.getFullYear(), viewDate.getMonth() + 1, 1));
  };

  const handlePrevYear = () => {
    setViewDate(new Date(viewDate.getFullYear() - 1, viewDate.getMonth(), 1));
  };

  const handleNextYear = () => {
    setViewDate(new Date(viewDate.getFullYear() + 1, viewDate.getMonth(), 1));
  };

  const handleDateSelect = (date: Date) => {
    if (isDateDisabled(date, minDate, maxDate, disabledDates, shouldDisableDate)) {
      return;
    }
    onChange?.(date);
    setIsOpen(false);
  };

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation();
    onChange?.(null);
  };

  const calendarDays = generateCalendarDays();
  const weekDays = [...Array(7)].map((_, i) => DAYS_SHORT[(i + firstDayOfWeek) % 7]);

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
        <CalendarIcon className="w-5 h-5 flex-shrink-0 text-grey-400" />
        <span className="flex-1 text-left">
          {value ? formatDate(value, format) : placeholder}
        </span>
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
            aria-label="Clear date"
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

      {/* Calendar Popup */}
      {isOpen && (
        <div className="absolute z-50 mt-2 bg-white border border-grey-300 rounded-lg shadow-lg p-4 min-w-[280px]">
          {/* Header - Month/Year Navigation */}
          <div className="flex items-center justify-between mb-4">
            <div className="flex gap-1">
              <button
                type="button"
                onClick={handlePrevYear}
                className="p-1 rounded hover:bg-grey-100 text-grey-600"
                aria-label="Previous year"
              >
                <ChevronLeftIcon className="w-4 h-4" />
                <ChevronLeftIcon className="w-4 h-4 -ml-3" />
              </button>
              <button
                type="button"
                onClick={handlePrevMonth}
                className="p-1 rounded hover:bg-grey-100 text-grey-600"
                aria-label="Previous month"
              >
                <ChevronLeftIcon className="w-4 h-4" />
              </button>
            </div>

            <div className="font-semibold text-grey-900">
              {MONTHS[viewDate.getMonth()]} {viewDate.getFullYear()}
            </div>

            <div className="flex gap-1">
              <button
                type="button"
                onClick={handleNextMonth}
                className="p-1 rounded hover:bg-grey-100 text-grey-600"
                aria-label="Next month"
              >
                <ChevronRightIcon className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={handleNextYear}
                className="p-1 rounded hover:bg-grey-100 text-grey-600"
                aria-label="Next year"
              >
                <ChevronRightIcon className="w-4 h-4" />
                <ChevronRightIcon className="w-4 h-4 -ml-3" />
              </button>
            </div>
          </div>

          {/* Weekday Headers */}
          <div className={cn('grid gap-1 mb-1', showWeekNumbers ? 'grid-cols-8' : 'grid-cols-7')}>
            {showWeekNumbers && (
              <div className="text-xs font-medium text-grey-500 text-center p-1">Wk</div>
            )}
            {weekDays.map((day) => (
              <div key={day} className="text-xs font-medium text-grey-500 text-center p-1">
                {day}
              </div>
            ))}
          </div>

          {/* Calendar Grid */}
          <div className={cn('grid gap-1', showWeekNumbers ? 'grid-cols-8' : 'grid-cols-7')}>
            {calendarDays.map((date, index) => {
              // Week number column
              if (showWeekNumbers && index % 7 === 0) {
                const weekDate = date || new Date();
                return (
                  <div
                    key={`week-${index}`}
                    className="text-xs text-grey-400 text-center p-1 flex items-center justify-center"
                  >
                    {getWeekNumber(weekDate)}
                  </div>
                );
              }

              if (!date) {
                return <div key={`empty-${index}`} className="p-1" />;
              }

              const isSelected = isSameDay(date, value ?? null);
              const isToday = isSameDay(date, new Date());
              const isDisabled = isDateDisabled(
                date,
                minDate,
                maxDate,
                disabledDates,
                shouldDisableDate
              );

              return (
                <button
                  key={date.toISOString()}
                  type="button"
                  onClick={() => handleDateSelect(date)}
                  disabled={isDisabled}
                  className={cn(
                    'p-1 text-sm rounded transition-colors',
                    'hover:bg-grey-100 focus:outline-none focus:ring-2 focus:ring-primary-500',
                    isSelected && 'bg-primary-500 text-white hover:bg-primary-600',
                    isToday && !isSelected && 'border border-primary-500',
                    isDisabled && 'text-grey-300 cursor-not-allowed hover:bg-transparent'
                  )}
                >
                  {date.getDate()}
                </button>
              );
            })}
          </div>

          {/* Today Button */}
          <div className="mt-4 pt-4 border-t border-grey-200">
            <button
              type="button"
              onClick={() => {
                const today = new Date();
                setViewDate(today);
                handleDateSelect(today);
              }}
              className="w-full px-3 py-1.5 text-sm text-primary-600 hover:text-primary-700 hover:bg-primary-50 rounded transition-colors"
            >
              Today
            </button>
          </div>
        </div>
      )}
    </div>
  );
});

DatePicker.displayName = 'DatePicker';
