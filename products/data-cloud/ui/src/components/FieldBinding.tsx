import React, { useState, useMemo } from 'react';
import type { MetaField } from '@/types/schema.types';

/**
 * Field binding component for mapping workflow fields to collection fields.
 *
 * <p><b>Purpose</b><br>
 * Provides UI for mapping workflow fields to collection schema fields.
 * Validates type compatibility and shows field suggestions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { FieldBinding } from '@/components/FieldBinding';
 *
 * function FieldMapper() {
 *   const [bindings, setBindings] = useState({});
 *   const availableFields = [...];
 *
 *   return (
 *     <FieldBinding
 *       workflowFieldName="userId"
 *       availableFields={availableFields}
 *       currentBinding={bindings['userId']}
 *       onBindingChange={(field) => setBindings({...bindings, userId: field})}
 *     />
 *   );
 * }
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Type validation
 * - Field suggestions
 * - Required field indicators
 * - Nested field support
 * - Array field handling
 * - Visual feedback
 *
 * @doc.type component
 * @doc.purpose Field mapping and binding UI
 * @doc.layer frontend
 */

export interface FieldBindingProps {
  /** Workflow field name */
  workflowFieldName: string;
  /** Expected field type */
  expectedType?: string;
  /** Available fields from schema */
  availableFields: MetaField[];
  /** Current binding */
  currentBinding?: MetaField | null;
  /** Callback when binding changes */
  onBindingChange?: (field: MetaField | null) => void;
  /** Is this field required */
  isRequired?: boolean;
  /** Show type validation errors */
  showErrors?: boolean;
  /** Custom CSS class */
  className?: string;
}

/**
 * Check if field types are compatible.
 *
 * @param sourceType the source type
 * @param targetType the target type
 * @returns true if types are compatible
 */
function areTypesCompatible(sourceType: string, targetType: string): boolean {
  // Exact match
  if (sourceType === targetType) return true;

  // Numeric types are compatible
  if (['number', 'integer'].includes(sourceType) && ['number', 'integer'].includes(targetType)) {
    return true;
  }

  // String types are compatible
  if (['string', 'text'].includes(sourceType) && ['string', 'text'].includes(targetType)) {
    return true;
  }

  return false;
}

export function FieldBinding({
  workflowFieldName,
  expectedType,
  availableFields,
  currentBinding,
  onBindingChange,
  isRequired = false,
  showErrors = true,
  className = '',
}: FieldBindingProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Filter and sort suggestions
  const suggestions = useMemo(() => {
    let filtered = availableFields.filter((field) =>
      field.name.toLowerCase().includes(searchQuery.toLowerCase())
    );

    // Sort by compatibility
    if (expectedType) {
      filtered.sort((a, b) => {
        const aCompatible = areTypesCompatible(expectedType, a.type) ? 0 : 1;
        const bCompatible = areTypesCompatible(expectedType, b.type) ? 0 : 1;
        return aCompatible - bCompatible;
      });
    }

    return filtered.slice(0, 10); // Limit to 10 suggestions
  }, [availableFields, searchQuery, expectedType]);

  const isTypeCompatible = currentBinding
    ? !expectedType || areTypesCompatible(expectedType, currentBinding.type)
    : true;

  const hasError = isRequired && !currentBinding;

  return (
    <div className={`flex flex-col gap-2 ${className}`}>
      {/* Label */}
      <label className="text-sm font-medium text-gray-900 dark:text-gray-100">
        {workflowFieldName}
        {isRequired && <span className="text-red-500 ml-1">*</span>}
        {expectedType && (
          <span className="ml-2 text-xs text-gray-500 dark:text-gray-400">
            ({expectedType})
          </span>
        )}
      </label>

      {/* Binding Dropdown */}
      <div className="relative">
        <button
          onClick={() => setIsOpen(!isOpen)}
          className={`
            w-full px-3 py-2 text-sm text-left rounded-md border
            transition-colors duration-200
            flex items-center justify-between
            ${
              hasError
                ? 'border-red-500 bg-red-50 dark:bg-red-900/20'
                : isTypeCompatible
                  ? 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800'
                  : 'border-yellow-500 bg-yellow-50 dark:bg-yellow-900/20'
            }
            hover:border-gray-400 dark:hover:border-gray-500
            focus:outline-none focus:ring-2 focus:ring-blue-500
          `}
        >
          <span className="text-gray-900 dark:text-gray-100">
            {currentBinding ? (
              <div className="flex items-center gap-2">
                <span className="font-medium">{currentBinding.name}</span>
                <span className="text-xs text-gray-500 dark:text-gray-400">
                  {currentBinding.type}
                </span>
              </div>
            ) : (
              <span className="text-gray-500 dark:text-gray-400">Select a field...</span>
            )}
          </span>

          <svg
            className={`w-4 h-4 text-gray-400 transition-transform ${isOpen ? 'rotate-180' : ''}`}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 14l-7 7m0 0l-7-7m7 7V3" />
          </svg>
        </button>

        {/* Dropdown Menu */}
        {isOpen && (
          <div className="absolute top-full left-0 right-0 mt-1 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-md shadow-lg z-10">
            {/* Search Input */}
            <input
              type="text"
              placeholder="Search fields..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full px-3 py-2 text-sm border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none"
            />

            {/* Suggestions */}
            <div className="max-h-48 overflow-y-auto">
              {suggestions.length === 0 ? (
                <div className="px-3 py-4 text-sm text-gray-500 dark:text-gray-400 text-center">
                  No fields found
                </div>
              ) : (
                suggestions.map((field) => {
                  const compatible = !expectedType || areTypesCompatible(expectedType, field.type);
                  return (
                    <button
                      key={field.id}
                      onClick={() => {
                        onBindingChange?.(field);
                        setIsOpen(false);
                        setSearchQuery('');
                      }}
                      className={`
                        w-full px-3 py-2 text-left text-sm
                        transition-colors duration-200
                        flex items-center justify-between gap-2
                        ${
                          compatible
                            ? 'hover:bg-blue-50 dark:hover:bg-blue-900/20'
                            : 'hover:bg-yellow-50 dark:hover:bg-yellow-900/20 opacity-75'
                        }
                      `}
                    >
                      <div className="flex-1 min-w-0">
                        <p className="font-medium text-gray-900 dark:text-gray-100 truncate">
                          {field.name}
                        </p>
                        <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
                          {field.type}
                        </p>
                      </div>

                      {/* Compatibility Indicator */}
                      {!compatible && (
                        <svg
                          className="w-4 h-4 text-yellow-500 flex-shrink-0"
                          fill="currentColor"
                          viewBox="0 0 20 20"
                        >
                          <path
                            fillRule="evenodd"
                            d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                            clipRule="evenodd"
                          />
                        </svg>
                      )}
                    </button>
                  );
                })
              )}
            </div>

            {/* Clear Button */}
            {currentBinding && (
              <button
                onClick={() => {
                  onBindingChange?.(null);
                  setIsOpen(false);
                  setSearchQuery('');
                }}
                className="w-full px-3 py-2 text-sm text-left text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 border-t border-gray-200 dark:border-gray-700 transition-colors"
              >
                Clear binding
              </button>
            )}
          </div>
        )}
      </div>

      {/* Error Messages */}
      {showErrors && hasError && (
        <p className="text-sm text-red-600 dark:text-red-400">
          This field is required
        </p>
      )}

      {showErrors && !isTypeCompatible && currentBinding && (
        <p className="text-sm text-yellow-600 dark:text-yellow-400">
          Type mismatch: expected {expectedType}, got {currentBinding.type}
        </p>
      )}

      {/* Field Info */}
      {currentBinding && (
        <div className="px-3 py-2 bg-blue-50 dark:bg-blue-900/20 rounded-md text-sm">
          <p className="text-gray-700 dark:text-gray-300">
            <span className="font-medium">Bound to:</span> {currentBinding.name}
          </p>
          {currentBinding.description && (
            <p className="text-xs text-gray-600 dark:text-gray-400 mt-1">
              {currentBinding.description}
            </p>
          )}
        </div>
      )}
    </div>
  );
}

export default FieldBinding;
