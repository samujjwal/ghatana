/**
 * Question Options Group Component
 *
 * @description Renders different types of question options (radio, checkbox,
 * multi-select, text input) for AI-driven conversations during bootstrapping.
 *
 * @doc.type component
 * @doc.purpose Question response options
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useState, useCallback, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Check,
  Circle,
  ChevronDown,
  ChevronUp,
  X,
  AlertCircle,
  Sparkles,
  Lightbulb,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Textarea } from '@ghatana/yappc-ui';
import { Badge } from '@ghatana/ui';

// =============================================================================
// Types
// =============================================================================

export type QuestionType = 'single' | 'multiple' | 'text' | 'scale' | 'confirm';

export interface QuestionOption {
  id: string;
  label: string;
  value: string;
  description?: string;
  icon?: React.ReactNode;
  disabled?: boolean;
  recommended?: boolean;
  aiSuggested?: boolean;
  metadata?: Record<string, unknown>;
}

export interface QuestionValidation {
  required?: boolean;
  minSelections?: number;
  maxSelections?: number;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  patternMessage?: string;
}

export interface QuestionOptionsGroupProps {
  /** Unique question ID */
  questionId: string;
  /** Question type determines rendering */
  type: QuestionType;
  /** Available options (for single/multiple choice) */
  options?: QuestionOption[];
  /** Current selected value(s) */
  value?: string | string[];
  /** Called when selection changes */
  onChange: (value: string | string[]) => void;
  /** Called when user submits answer */
  onSubmit?: () => void;
  /** Validation rules */
  validation?: QuestionValidation;
  /** Show validation errors */
  showValidation?: boolean;
  /** Scale range (for scale type) */
  scaleRange?: { min: number; max: number; labels?: string[] };
  /** Placeholder for text input */
  placeholder?: string;
  /** Show AI suggestions */
  showAiSuggestions?: boolean;
  /** Allow custom "Other" option */
  allowOther?: boolean;
  /** Layout orientation */
  layout?: 'vertical' | 'horizontal' | 'grid';
  /** Show option descriptions */
  showDescriptions?: boolean;
  /** Disabled state */
  disabled?: boolean;
  /** Loading state */
  loading?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Animation Variants
// =============================================================================

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.05,
    },
  },
} as const;

const itemVariants = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0 },
} as const;

const checkVariants = {
  unchecked: { scale: 0, opacity: 0 },
  checked: { scale: 1, opacity: 1 },
} as const;

// =============================================================================
// Single Choice Option
// =============================================================================

interface SingleOptionProps {
  option: QuestionOption;
  selected: boolean;
  onSelect: () => void;
  showDescription: boolean;
  disabled: boolean;
}

const SingleOption: React.FC<SingleOptionProps> = ({
  option,
  selected,
  onSelect,
  showDescription,
  disabled,
}) => {
  const isDisabled = disabled || option.disabled;

  return (
    <motion.button
      variants={itemVariants}
      type="button"
      onClick={onSelect}
      disabled={isDisabled}
      className={cn(
        'group relative flex w-full items-start gap-3 rounded-lg border p-4 text-left transition-all',
        'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2',
        selected
          ? 'border-primary-500 bg-primary-50 dark:bg-primary-950/30'
          : 'border-neutral-200 bg-white hover:border-neutral-300 hover:bg-neutral-50 dark:border-neutral-700 dark:bg-neutral-800 dark:hover:border-neutral-600 dark:hover:bg-neutral-750',
        isDisabled && 'cursor-not-allowed opacity-50'
      )}
      aria-pressed={selected}
    >
      {/* Radio indicator */}
      <div
        className={cn(
          'mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full border-2 transition-colors',
          selected
            ? 'border-primary-500 bg-primary-500'
            : 'border-neutral-300 dark:border-neutral-600'
        )}
      >
        <motion.div
          initial="unchecked"
          animate={selected ? 'checked' : 'unchecked'}
          variants={checkVariants}
          className="h-2 w-2 rounded-full bg-white"
        />
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          {option.icon && (
            <span className="text-neutral-600 dark:text-neutral-400">
              {option.icon}
            </span>
          )}
          <span
            className={cn(
              'font-medium',
              selected
                ? 'text-primary-700 dark:text-primary-300'
                : 'text-neutral-900 dark:text-neutral-100'
            )}
          >
            {option.label}
          </span>
          {option.recommended && (
            <Badge variant="outline" className="text-xs">
              <Sparkles className="mr-1 h-3 w-3" />
              Recommended
            </Badge>
          )}
          {option.aiSuggested && (
            <Badge variant="outline" className="text-xs text-purple-600">
              <Lightbulb className="mr-1 h-3 w-3" />
              AI Suggested
            </Badge>
          )}
        </div>
        {showDescription && option.description && (
          <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
            {option.description}
          </p>
        )}
      </div>

      {/* Selected indicator */}
      <AnimatePresence>
        {selected && (
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            exit={{ scale: 0 }}
            className="absolute right-3 top-3"
          >
            <Check className="h-5 w-5 text-primary-500" />
          </motion.div>
        )}
      </AnimatePresence>
    </motion.button>
  );
};

// =============================================================================
// Multiple Choice Option
// =============================================================================

interface MultipleOptionProps {
  option: QuestionOption;
  selected: boolean;
  onToggle: () => void;
  showDescription: boolean;
  disabled: boolean;
}

const MultipleOption: React.FC<MultipleOptionProps> = ({
  option,
  selected,
  onToggle,
  showDescription,
  disabled,
}) => {
  const isDisabled = disabled || option.disabled;

  return (
    <motion.button
      variants={itemVariants}
      type="button"
      onClick={onToggle}
      disabled={isDisabled}
      className={cn(
        'group relative flex w-full items-start gap-3 rounded-lg border p-4 text-left transition-all',
        'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2',
        selected
          ? 'border-primary-500 bg-primary-50 dark:bg-primary-950/30'
          : 'border-neutral-200 bg-white hover:border-neutral-300 hover:bg-neutral-50 dark:border-neutral-700 dark:bg-neutral-800 dark:hover:border-neutral-600 dark:hover:bg-neutral-750',
        isDisabled && 'cursor-not-allowed opacity-50'
      )}
      aria-pressed={selected}
    >
      {/* Checkbox indicator */}
      <div
        className={cn(
          'mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded border-2 transition-colors',
          selected
            ? 'border-primary-500 bg-primary-500'
            : 'border-neutral-300 dark:border-neutral-600'
        )}
      >
        <motion.div
          initial="unchecked"
          animate={selected ? 'checked' : 'unchecked'}
          variants={checkVariants}
        >
          <Check className="h-3 w-3 text-white" />
        </motion.div>
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          {option.icon && (
            <span className="text-neutral-600 dark:text-neutral-400">
              {option.icon}
            </span>
          )}
          <span
            className={cn(
              'font-medium',
              selected
                ? 'text-primary-700 dark:text-primary-300'
                : 'text-neutral-900 dark:text-neutral-100'
            )}
          >
            {option.label}
          </span>
          {option.recommended && (
            <Badge variant="outline" className="text-xs">
              <Sparkles className="mr-1 h-3 w-3" />
              Recommended
            </Badge>
          )}
          {option.aiSuggested && (
            <Badge variant="outline" className="text-xs text-purple-600">
              <Lightbulb className="mr-1 h-3 w-3" />
              AI Suggested
            </Badge>
          )}
        </div>
        {showDescription && option.description && (
          <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
            {option.description}
          </p>
        )}
      </div>
    </motion.button>
  );
};

// =============================================================================
// Scale Option
// =============================================================================

interface ScaleOptionProps {
  value: number;
  range: { min: number; max: number; labels?: string[] };
  onChange: (value: number) => void;
  disabled: boolean;
}

const ScaleOption: React.FC<ScaleOptionProps> = ({
  value,
  range,
  onChange,
  disabled,
}) => {
  const steps = Array.from(
    { length: range.max - range.min + 1 },
    (_, i) => range.min + i
  );

  return (
    <motion.div variants={itemVariants} className="space-y-3">
      {/* Labels */}
      {range.labels && range.labels.length >= 2 && (
        <div className="flex items-center justify-between text-sm text-neutral-600 dark:text-neutral-400">
          <span>{range.labels[0]}</span>
          <span>{range.labels[range.labels.length - 1]}</span>
        </div>
      )}

      {/* Scale buttons */}
      <div className="flex items-center gap-2">
        {steps.map((step) => (
          <button
            key={step}
            type="button"
            onClick={() => onChange(step)}
            disabled={disabled}
            className={cn(
              'flex h-10 w-10 items-center justify-center rounded-full border-2 font-medium transition-all',
              'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2',
              value === step
                ? 'border-primary-500 bg-primary-500 text-white'
                : 'border-neutral-300 hover:border-neutral-400 dark:border-neutral-600 dark:hover:border-neutral-500',
              disabled && 'cursor-not-allowed opacity-50'
            )}
          >
            {step}
          </button>
        ))}
      </div>

      {/* Middle labels */}
      {range.labels && range.labels.length > 2 && (
        <div className="flex items-center justify-between text-xs text-neutral-500">
          {range.labels.map((label, i) => (
            <span key={i} className="text-center" style={{ width: `${100 / range.labels!.length}%` }}>
              {label}
            </span>
          ))}
        </div>
      )}
    </motion.div>
  );
};

// =============================================================================
// Confirm Option
// =============================================================================

interface ConfirmOptionProps {
  value: boolean | null;
  onChange: (value: boolean) => void;
  disabled: boolean;
}

const ConfirmOption: React.FC<ConfirmOptionProps> = ({
  value,
  onChange,
  disabled,
}) => {
  return (
    <motion.div variants={itemVariants} className="flex items-center gap-4">
      <Button
        variant={value === true ? 'solid' : 'outline'}
        colorScheme={value === true ? 'primary' : 'neutral'}
        onClick={() => onChange(true)}
        disabled={disabled}
        className="flex-1"
      >
        <Check className="mr-2 h-4 w-4" />
        Yes
      </Button>
      <Button
        variant={value === false ? 'solid' : 'outline'}
        colorScheme={value === false ? 'error' : 'neutral'}
        onClick={() => onChange(false)}
        disabled={disabled}
        className="flex-1"
      >
        <X className="mr-2 h-4 w-4" />
        No
      </Button>
    </motion.div>
  );
};

// =============================================================================
// Text Input Option
// =============================================================================

interface TextOptionProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  validation?: QuestionValidation;
  disabled: boolean;
}

const TextOption: React.FC<TextOptionProps> = ({
  value,
  onChange,
  placeholder,
  validation,
  disabled,
}) => {
  const charCount = value.length;
  const maxLength = validation?.maxLength;
  const isNearLimit = maxLength && charCount >= maxLength * 0.9;

  return (
    <motion.div variants={itemVariants} className="space-y-2">
      <Textarea
        value={value}
        onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => onChange(e.target.value)}
        placeholder={placeholder || 'Type your response...'}
        disabled={disabled}
        className="min-h-[120px] resize-y"
        maxLength={maxLength}
      />
      {maxLength && (
        <div
          className={cn(
            'text-right text-xs',
            isNearLimit
              ? 'text-amber-600 dark:text-amber-400'
              : 'text-neutral-500'
          )}
        >
          {charCount}/{maxLength} characters
        </div>
      )}
    </motion.div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const QuestionOptionsGroup: React.FC<QuestionOptionsGroupProps> = ({
  questionId,
  type,
  options = [],
  value,
  onChange,
  onSubmit,
  validation,
  showValidation = false,
  scaleRange = { min: 1, max: 5 },
  placeholder,
  showAiSuggestions = true,
  allowOther = false,
  layout = 'vertical',
  showDescriptions = true,
  disabled = false,
  loading = false,
  className,
}) => {
  // State for "Other" option
  const [otherValue, setOtherValue] = useState('');
  const [showOtherInput, setShowOtherInput] = useState(false);

  // Memoized values
  const selectedValues = useMemo(() => {
    if (!value) return [];
    return Array.isArray(value) ? value : [value];
  }, [value]);

  const isSelected = useCallback(
    (optionId: string) => selectedValues.includes(optionId),
    [selectedValues]
  );

  // Validation
  const validationErrors = useMemo(() => {
    const errors: string[] = [];
    if (!validation) return errors;

    if (validation.required && selectedValues.length === 0) {
      errors.push('This question is required');
    }

    if (validation.minSelections && selectedValues.length < validation.minSelections) {
      errors.push(`Please select at least ${validation.minSelections} option(s)`);
    }

    if (validation.maxSelections && selectedValues.length > validation.maxSelections) {
      errors.push(`Please select at most ${validation.maxSelections} option(s)`);
    }

    if (type === 'text' && typeof value === 'string') {
      if (validation.minLength && value.length < validation.minLength) {
        errors.push(`Minimum ${validation.minLength} characters required`);
      }
      if (validation.pattern) {
        const regex = new RegExp(validation.pattern);
        if (!regex.test(value)) {
          errors.push(validation.patternMessage || 'Invalid format');
        }
      }
    }

    return errors;
  }, [validation, selectedValues, type, value]);

  const isValid = validationErrors.length === 0;

  // Handlers
  const handleSingleSelect = useCallback(
    (optionId: string) => {
      if (disabled) return;
      onChange(optionId);
    },
    [disabled, onChange]
  );

  const handleMultipleToggle = useCallback(
    (optionId: string) => {
      if (disabled) return;
      const currentValues = Array.isArray(value) ? value : [];
      if (currentValues.includes(optionId)) {
        onChange(currentValues.filter((v) => v !== optionId));
      } else {
        onChange([...currentValues, optionId]);
      }
    },
    [disabled, value, onChange]
  );

  const handleScaleChange = useCallback(
    (scaleValue: number) => {
      if (disabled) return;
      onChange(scaleValue.toString());
    },
    [disabled, onChange]
  );

  const handleConfirmChange = useCallback(
    (confirmed: boolean) => {
      if (disabled) return;
      onChange(confirmed ? 'yes' : 'no');
    },
    [disabled, onChange]
  );

  const handleTextChange = useCallback(
    (text: string) => {
      if (disabled) return;
      onChange(text);
    },
    [disabled, onChange]
  );

  // Layout styles
  const layoutStyles = useMemo(() => {
    switch (layout) {
      case 'horizontal':
        return 'flex flex-row flex-wrap gap-3';
      case 'grid':
        return 'grid grid-cols-2 gap-3';
      default:
        return 'flex flex-col gap-3';
    }
  }, [layout]);

  // AI suggested options first
  const sortedOptions = useMemo(() => {
    if (!showAiSuggestions) return options;
    return [...options].sort((a, b) => {
      if (a.aiSuggested && !b.aiSuggested) return -1;
      if (!a.aiSuggested && b.aiSuggested) return 1;
      if (a.recommended && !b.recommended) return -1;
      if (!a.recommended && b.recommended) return 1;
      return 0;
    });
  }, [options, showAiSuggestions]);

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="visible"
      className={cn('space-y-4', className)}
      role="group"
      aria-labelledby={`question-${questionId}`}
    >
      {/* Options based on type */}
      {type === 'single' && (
        <div className={layoutStyles}>
          {sortedOptions.map((option) => (
            <SingleOption
              key={option.id}
              option={option}
              selected={isSelected(option.id)}
              onSelect={() => handleSingleSelect(option.id)}
              showDescription={showDescriptions}
              disabled={disabled || loading}
            />
          ))}
        </div>
      )}

      {type === 'multiple' && (
        <div className={layoutStyles}>
          {sortedOptions.map((option) => (
            <MultipleOption
              key={option.id}
              option={option}
              selected={isSelected(option.id)}
              onToggle={() => handleMultipleToggle(option.id)}
              showDescription={showDescriptions}
              disabled={disabled || loading}
            />
          ))}
        </div>
      )}

      {type === 'scale' && (
        <ScaleOption
          value={parseInt(value as string, 10) || scaleRange.min}
          range={scaleRange}
          onChange={handleScaleChange}
          disabled={disabled || loading}
        />
      )}

      {type === 'confirm' && (
        <ConfirmOption
          value={value === 'yes' ? true : value === 'no' ? false : null}
          onChange={handleConfirmChange}
          disabled={disabled || loading}
        />
      )}

      {type === 'text' && (
        <TextOption
          value={(value as string) || ''}
          onChange={handleTextChange}
          placeholder={placeholder}
          validation={validation}
          disabled={disabled || loading}
        />
      )}

      {/* "Other" option for single/multiple */}
      {allowOther && (type === 'single' || type === 'multiple') && (
        <motion.div variants={itemVariants} className="space-y-2">
          <button
            type="button"
            onClick={() => setShowOtherInput(!showOtherInput)}
            className={cn(
              'flex w-full items-center justify-between rounded-lg border border-dashed p-4 text-left transition-colors',
              'border-neutral-300 hover:border-neutral-400 dark:border-neutral-600 dark:hover:border-neutral-500',
              disabled && 'cursor-not-allowed opacity-50'
            )}
            disabled={disabled}
          >
            <span className="text-neutral-600 dark:text-neutral-400">
              Other (specify)
            </span>
            {showOtherInput ? (
              <ChevronUp className="h-4 w-4" />
            ) : (
              <ChevronDown className="h-4 w-4" />
            )}
          </button>
          <AnimatePresence>
            {showOtherInput && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                className="overflow-hidden"
              >
                <Textarea
                  value={otherValue}
                  onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setOtherValue(e.target.value)}
                  placeholder="Please specify..."
                  disabled={disabled}
                  className="mt-2"
                />
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      )}

      {/* Validation errors */}
      <AnimatePresence>
        {showValidation && validationErrors.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="flex items-start gap-2 rounded-lg bg-error-50 p-3 dark:bg-error-950/30"
          >
            <AlertCircle className="h-5 w-5 flex-shrink-0 text-error-500" />
            <ul className="space-y-1 text-sm text-error-700 dark:text-error-300">
              {validationErrors.map((error, i) => (
                <li key={i}>{error}</li>
              ))}
            </ul>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Submit button */}
      {onSubmit && (
        <motion.div variants={itemVariants} className="flex justify-end pt-2">
          <Button
            variant="solid"
            colorScheme="primary"
            onClick={onSubmit}
            disabled={disabled || loading || (!isValid && showValidation)}
          >
            {loading ? (
              <>
                <motion.div
                  animate={{ rotate: 360 }}
                  transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                  className="mr-2"
                >
                  <Circle className="h-4 w-4" />
                </motion.div>
                Processing...
              </>
            ) : (
              <>
                Continue
                <Check className="ml-2 h-4 w-4" />
              </>
            )}
          </Button>
        </motion.div>
      )}
    </motion.div>
  );
};

export default QuestionOptionsGroup;
