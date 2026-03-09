import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * FormGroup component props
 */
export interface FormGroupProps {
  /**
   * Group title/legend
   */
  title?: React.ReactNode;
  /**
   * Group description
   */
  description?: string;
  /**
   * Show title as legend (for fieldset)
   * @default true
   */
  asFieldset?: boolean;
  /**
   * Disabled state for all children
   * @default false
   */
  disabled?: boolean;
  /**
   * Custom className for wrapper
   */
  className?: string;
  /**
   * Custom className for title
   */
  titleClassName?: string;
  /**
   * Custom className for content area
   */
  contentClassName?: string;
  /**
   * Form fields or content
   */
  children: React.ReactNode;
}

/**
 * FormGroup - Group related form fields with optional title
 *
 * Provides semantic grouping for form fields with optional
 * title and description.
 *
 * @example Basic usage
 * ```tsx
 * <FormGroup title="Personal Information">
 *   <FormField label="First Name">
 *     <input type="text" />
 *   </FormField>
 *   <FormField label="Last Name">
 *     <input type="text" />
 *   </FormField>
 * </FormGroup>
 * ```
 *
 * @example With description
 * ```tsx
 * <FormGroup
 *   title="Contact Details"
 *   description="How can we reach you?"
 * >
 *   <FormField label="Email">
 *     <input type="email" />
 *   </FormField>
 *   <FormField label="Phone">
 *     <input type="tel" />
 *   </FormField>
 * </FormGroup>
 * ```
 */
export const FormGroup = React.forwardRef<HTMLDivElement | HTMLFieldSetElement, FormGroupProps>(
  (
    {
      title,
      description,
      asFieldset = true,
      disabled = false,
      className,
      titleClassName,
      contentClassName,
      children,
    },
    ref
  ) => {
    const content = (
      <>
        {/* Title */}
        {title && (
          asFieldset ? (
            <legend
              className={cn(
                'text-base font-semibold text-grey-900 dark:text-grey-100 mb-1',
                disabled && 'opacity-50',
                titleClassName
              )}
            >
              {title}
            </legend>
          ) : (
            <div
              className={cn(
                'text-base font-semibold text-grey-900 dark:text-grey-100 mb-1',
                disabled && 'opacity-50',
                titleClassName
              )}
            >
              {title}
            </div>
          )
        )}

        {/* Description */}
        {description && (
          <div className="text-sm text-grey-600 dark:text-grey-400 mb-4">
            {description}
          </div>
        )}

        {/* Content */}
        <div className={cn('space-y-4', contentClassName)}>
          {children}
        </div>
      </>
    );

    if (asFieldset) {
      return (
        <fieldset
          ref={ref as React.Ref<HTMLFieldSetElement>}
          disabled={disabled}
          className={cn(
            'border border-grey-200 dark:border-grey-700 rounded-lg p-6',
            disabled && 'opacity-60 cursor-not-allowed',
            className
          )}
        >
          {content}
        </fieldset>
      );
    }

    return (
      <div
        ref={ref as React.Ref<HTMLDivElement>}
        className={cn(
          'border border-grey-200 dark:border-grey-700 rounded-lg p-6',
          disabled && 'opacity-60 cursor-not-allowed',
          className
        )}
      >
        {content}
      </div>
    );
  }
);

FormGroup.displayName = 'FormGroup';
