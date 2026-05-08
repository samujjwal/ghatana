/**
 * YAPPC Task Input Form
 *
 * Dynamic form component for task execution input.
 * Integrates with FormGenerator to render JSON Schema-based forms.
 *
 * @module ui/tasks/TaskInputForm
 */

import React, { useMemo, useCallback, useState, useEffect } from 'react';

import type { TaskDefinition, TaskExecution } from 'yappc-core/types/tasks';

type FieldType =
  | 'text'
  | 'textarea'
  | 'email'
  | 'url'
  | 'date'
  | 'datetime'
  | 'time'
  | 'number'
  | 'checkbox'
  | 'select'
  | 'multiselect';

interface FormField {
  id: string;
  type: FieldType;
  label: string;
  helpText?: string;
  defaultValue?: unknown;
  options?: Array<{ value: string; label: string }>;
  validation?: {
    required?: boolean;
    minLength?: number;
    maxLength?: number;
    min?: number;
    max?: number;
    pattern?: string;
  };
}

interface FormSchema {
  id: string;
  title: string;
  description?: string;
  fields: FormField[];
  submitButton: { label: string; variant: 'primary' | 'secondary' };
  cancelButton: { label: string; variant: 'primary' | 'secondary' };
}

interface FormGeneratorProps {
  schema: FormSchema;
  initialValues?: Record<string, unknown>;
  isSubmitting?: boolean;
  onSubmit: (data: Record<string, unknown>) => void | Promise<void>;
  onCancel?: () => void;
  debug?: boolean;
}

function toInputValue(value: unknown): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  return '';
}

function FormGenerator({
  schema,
  initialValues,
  isSubmitting,
  onSubmit,
  onCancel,
  debug,
}: FormGeneratorProps): React.ReactElement {
  const [values, setValues] = useState<Record<string, unknown>>(() => {
    const defaults: Record<string, unknown> = {};
    schema.fields.forEach((field) => {
      defaults[field.id] = initialValues?.[field.id] ?? field.defaultValue ?? '';
    });
    return defaults;
  });

  const updateValue = useCallback((fieldId: string, value: unknown): void => {
    setValues((current) => ({ ...current, [fieldId]: value }));
  }, []);

  const handleSubmit = useCallback(
    (event: React.FormEvent<HTMLFormElement>): void => {
      event.preventDefault();
      void onSubmit(values);
    },
    [onSubmit, values]
  );

  return (
    <form className="space-y-4" onSubmit={handleSubmit}>
      {schema.description && (
        <p className="text-sm text-gray-500">{schema.description}</p>
      )}
      {schema.fields.map((field) => {
        const value = values[field.id];
        const baseClassName =
          'mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20';

        return (
          <label key={field.id} className="block text-sm font-medium text-gray-700">
            {field.label}
            {field.validation?.required && (
              <span className="ml-1 text-red-500" aria-hidden="true">
                *
              </span>
            )}
            {field.type === 'textarea' ? (
              <textarea
                className={baseClassName}
                value={toInputValue(value)}
                onChange={(event) => updateValue(field.id, event.target.value)}
                required={field.validation?.required}
              />
            ) : field.type === 'checkbox' ? (
              <input
                className="ml-2 h-4 w-4 rounded border-gray-300 text-blue-600"
                type="checkbox"
                checked={Boolean(value)}
                onChange={(event) => updateValue(field.id, event.target.checked)}
              />
            ) : field.type === 'select' || field.type === 'multiselect' ? (
              <select
                className={baseClassName}
                value={toInputValue(value)}
                onChange={(event) => updateValue(field.id, event.target.value)}
                required={field.validation?.required}
              >
                <option value="">Select {field.label}</option>
                {field.options?.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            ) : (
              <input
                className={baseClassName}
                type={field.type === 'datetime' ? 'datetime-local' : field.type}
                value={toInputValue(value)}
                onChange={(event) => updateValue(field.id, event.target.value)}
                required={field.validation?.required}
                minLength={field.validation?.minLength}
                maxLength={field.validation?.maxLength}
                min={field.validation?.min}
                max={field.validation?.max}
                pattern={field.validation?.pattern}
              />
            )}
            {field.helpText && (
              <span className="mt-1 block text-xs text-gray-500">
                {field.helpText}
              </span>
            )}
          </label>
        );
      })}
      {debug && (
        <pre className="rounded bg-gray-100 p-3 text-xs">
          {JSON.stringify(values, null, 2)}
        </pre>
      )}
      <div className="flex justify-end gap-2">
        {onCancel && (
          <button
            type="button"
            className="rounded-md border border-gray-300 px-4 py-2 text-sm"
            onClick={onCancel}
          >
            {schema.cancelButton.label}
          </button>
        )}
        <button
          type="submit"
          className="rounded-md bg-blue-600 px-4 py-2 text-sm text-white disabled:opacity-50"
          disabled={isSubmitting}
        >
          {isSubmitting ? 'Submitting...' : schema.submitButton.label}
        </button>
      </div>
    </form>
  );
}

// ============================================================================
// Types
// ============================================================================

export interface TaskInputFormProps {
  /** Task definition containing input schema */
  task: TaskDefinition;
  /** Initial values for the form */
  initialValues?: Record<string, unknown>;
  /** Form submission handler */
  onSubmit: (data: Record<string, unknown>) => void | Promise<void>;
  /** Cancel handler */
  onCancel?: () => void;
  /** Form is submitting */
  isSubmitting?: boolean;
  /** Show debug panel */
  debug?: boolean;
  /** Custom CSS classes */
  className?: string;
}

export interface TaskInputSchemaProperty {
  type: string;
  title?: string;
  description?: string;
  default?: unknown;
  enum?: string[];
  items?: TaskInputSchemaProperty;
  properties?: Record<string, TaskInputSchemaProperty>;
  required?: string[];
  format?: string;
  minLength?: number;
  maxLength?: number;
  minimum?: number;
  maximum?: number;
  pattern?: string;
}

export interface TaskInputSchema {
  type: 'object';
  properties: Record<string, TaskInputSchemaProperty>;
  required?: string[];
  title?: string;
  description?: string;
  hints?: string[];
}

function parseTaskInputSchema(value: unknown): TaskInputSchema {
  const fallback: TaskInputSchema = { type: 'object', properties: {} };
  if (typeof value === 'string') {
    try {
      const parsed: unknown = JSON.parse(value);
      return parseTaskInputSchema(parsed);
    } catch {
      return fallback;
    }
  }
  if (
    typeof value === 'object' &&
    value !== null &&
    'type' in value &&
    value.type === 'object' &&
    'properties' in value &&
    typeof value.properties === 'object' &&
    value.properties !== null
  ) {
    return value as TaskInputSchema;
  }
  return fallback;
}

// ============================================================================
// Schema Conversion Helpers
// ============================================================================

/**
 * Maps JSON Schema types to FormField types
 */
function mapJsonSchemaTypeToFieldType(
  jsonType: string,
  format?: string,
  enumValues?: string[]
): FieldType {
  if (enumValues && enumValues.length > 0) {
    return 'select';
  }

  switch (jsonType) {
    case 'string':
      switch (format) {
        case 'date':
          return 'date';
        case 'date-time':
          return 'datetime';
        case 'time':
          return 'time';
        case 'email':
          return 'email';
        case 'uri':
        case 'url':
          return 'url';
        case 'textarea':
          return 'textarea';
        default:
          return 'text';
      }
    case 'number':
    case 'integer':
      return 'number';
    case 'boolean':
      return 'checkbox';
    case 'array':
      return 'multiselect';
    default:
      return 'text';
  }
}

/**
 * Converts a JSON Schema property to a FormField
 */
function jsonSchemaPropertyToFormField(
  id: string,
  property: TaskInputSchemaProperty,
  isRequired: boolean
): FormField {
  const fieldType = mapJsonSchemaTypeToFieldType(
    property.type,
    property.format,
    property.enum
  );

  const field: FormField = {
    id,
    type: fieldType,
    label: property.title || formatLabel(id),
    helpText: property.description,
    defaultValue: property.default,
    validation: {
      required: isRequired,
      ...(property.minLength !== undefined && {
        minLength: property.minLength,
      }),
      ...(property.maxLength !== undefined && {
        maxLength: property.maxLength,
      }),
      ...(property.minimum !== undefined && { min: property.minimum }),
      ...(property.maximum !== undefined && { max: property.maximum }),
      ...(property.pattern && { pattern: property.pattern }),
    },
  };

  // Add options for enum types
  if (property.enum) {
    field.options = property.enum.map((value) => ({
      value,
      label: formatLabel(value),
    }));
  }

  return field;
}

/**
 * Formats a camelCase or snake_case ID to a human-readable label
 */
function formatLabel(id: string): string {
  return id
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

/**
 * Converts a JSON Schema to FormSchema for FormGenerator
 */
function convertJsonSchemaToFormSchema(
  taskId: string,
  taskName: string,
  inputSchema: TaskInputSchema
): FormSchema {
  const fields: FormField[] = [];
  const requiredFields = new Set(inputSchema.required || []);

  for (const [propertyId, property] of Object.entries(inputSchema.properties)) {
    const isRequired = requiredFields.has(propertyId);
    const field = jsonSchemaPropertyToFormField(
      propertyId,
      property,
      isRequired
    );
    fields.push(field);
  }

  return {
    id: `task-input-${taskId}`,
    title: `${taskName} Input`,
    description: inputSchema.description,
    fields,
    submitButton: {
      label: 'Execute Task',
      variant: 'primary',
    },
    cancelButton: {
      label: 'Cancel',
      variant: 'secondary',
    },
  };
}

// ============================================================================
// Task Input Form Component
// ============================================================================

/**
 * Task Input Form Component
 *
 * Renders a dynamic form based on the task's input schema.
 * Uses FormGenerator for form rendering and validation.
 */
export function TaskInputForm({
  task,
  initialValues,
  onSubmit,
  onCancel,
  isSubmitting,
  debug,
  className,
}: TaskInputFormProps): React.ReactElement {
  // Convert JSON Schema to FormSchema
  const inputSchema = useMemo(
    () => parseTaskInputSchema(task.inputSchema),
    [task.inputSchema]
  );

  const formSchema = useMemo(() => {
    return convertJsonSchemaToFormSchema(
      task.id,
      task.name,
      inputSchema
    );
  }, [task.id, task.name, inputSchema]);

  const handleSubmit = useCallback(
    async (data: Record<string, unknown>) => {
      await onSubmit(data);
    },
    [onSubmit]
  );

  return (
    <div className={`task-input-form ${className || ''}`}>
      {/* Task Header */}
      <div className="mb-6">
        <div className="flex items-center gap-3 mb-2">
          <div
            className="w-10 h-10 rounded-lg flex items-center justify-center"
            style={{ backgroundColor: `${task.ui.color}20` }}
          >
            <span
              className="material-icons text-xl"
              style={{ color: task.ui.color }}
            >
              {task.ui.icon}
            </span>
          </div>
          <div>
            <h2 className="text-lg font-semibold text-gray-900">{task.name}</h2>
            <p className="text-sm text-gray-500">{task.description}</p>
          </div>
        </div>

        {/* Task metadata badges */}
        <div className="flex flex-wrap gap-2 mt-3">
          <AutomationBadge level={task.automationLevel} />
          {task.lifecycleStages.map((stage) => (
            <LifecycleStageBadge key={stage} stage={stage} />
          ))}
        </div>
      </div>

      {/* Required Capabilities */}
      {task.requiredCapabilities.length > 0 && (
        <div className="mb-4 p-3 bg-blue-50 rounded-lg border border-blue-100">
          <h4 className="text-sm font-medium text-blue-800 mb-2">
            Required Capabilities
          </h4>
          <div className="flex flex-wrap gap-1">
            {task.requiredCapabilities.map((cap) => (
              <span
                key={cap}
                className="px-2 py-0.5 text-xs bg-blue-100 text-blue-700 rounded"
              >
                {cap}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Input Hints */}
      {inputSchema.hints && <InputHints hints={inputSchema.hints} />}

      {/* Form */}
      <FormGenerator
        schema={formSchema}
        initialValues={initialValues}
        isSubmitting={isSubmitting}
        onSubmit={handleSubmit}
        onCancel={onCancel}
        debug={debug}
      />
    </div>
  );
}

// ============================================================================
// Supporting Components
// ============================================================================

interface AutomationBadgeProps {
  level: string;
}

function AutomationBadge({ level }: AutomationBadgeProps) {
  const colors: Record<string, string> = {
    manual: 'bg-gray-100 text-gray-700',
    assisted: 'bg-blue-100 text-blue-700',
    automated: 'bg-green-100 text-green-700',
  };

  const icons: Record<string, string> = {
    manual: 'person',
    assisted: 'smart_toy',
    automated: 'auto_fix_high',
  };

  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full ${colors[level] || colors.manual}`}
    >
      <span className="material-icons text-sm">{icons[level] || 'help'}</span>
      {level}
    </span>
  );
}

interface LifecycleStageBadgeProps {
  stage: string;
}

function LifecycleStageBadge({ stage }: LifecycleStageBadgeProps) {
  const stageColors: Record<string, string> = {
    intent: 'bg-purple-100 text-purple-700',
    context: 'bg-indigo-100 text-indigo-700',
    plan: 'bg-blue-100 text-blue-700',
    execute: 'bg-green-100 text-green-700',
    verify: 'bg-yellow-100 text-yellow-700',
    observe: 'bg-orange-100 text-orange-700',
    learn: 'bg-red-100 text-red-700',
    institutionalize: 'bg-pink-100 text-pink-700',
  };

  return (
    <span
      className={`px-2 py-0.5 text-xs rounded-full ${stageColors[stage] || 'bg-gray-100 text-gray-700'}`}
    >
      {stage}
    </span>
  );
}

interface InputHintsProps {
  hints: string[];
}

function InputHints({ hints }: InputHintsProps) {
  const [expanded, setExpanded] = useState(false);

  if (!hints || hints.length === 0) return null;

  return (
    <div className="mb-4 p-3 bg-amber-50 rounded-lg border border-amber-100">
      <button
        type="button"
        className="flex items-center gap-2 text-sm font-medium text-amber-800 w-full"
        onClick={() => setExpanded(!expanded)}
      >
        <span className="material-icons text-sm">tips_and_updates</span>
        Input Hints
        <span className="material-icons text-sm ml-auto">
          {expanded ? 'expand_less' : 'expand_more'}
        </span>
      </button>
      {expanded && (
        <ul className="mt-2 space-y-1">
          {hints.map((hint, index) => (
            <li
              key={index}
              className="text-sm text-amber-700 flex items-start gap-2"
            >
              <span className="material-icons text-xs mt-0.5">arrow_right</span>
              {hint}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ============================================================================
// Task Execution Modal
// ============================================================================

export interface TaskExecutionModalProps {
  /** Task to execute */
  task: TaskDefinition;
  /** Whether modal is open */
  isOpen: boolean;
  /** Close modal handler */
  onClose: () => void;
  /** Submit handler */
  onExecute: (input: Record<string, unknown>) => Promise<void>;
  /** Current execution (if any) */
  execution?: TaskExecution;
}

/**
 * Modal for task execution with tabs for Input, Output, Artifacts, Audit
 */
export function TaskExecutionModal({
  task,
  isOpen,
  onClose,
  onExecute,
  execution,
}: TaskExecutionModalProps): React.ReactElement | null {
  const [activeTab, setActiveTab] = useState<
    'input' | 'output' | 'artifacts' | 'audit'
  >('input');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reset when modal opens
  useEffect(() => {
    if (isOpen) {
      setActiveTab('input');
      setError(null);
    }
  }, [isOpen]);

  const handleExecute = useCallback(
    async (input: Record<string, unknown>) => {
      setIsSubmitting(true);
      setError(null);
      try {
        await onExecute(input);
        setActiveTab('output');
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Execution failed');
      } finally {
        setIsSubmitting(false);
      }
    },
    [onExecute]
  );

  if (!isOpen) return null;

  const tabs: Array<{
    id: 'input' | 'output' | 'artifacts' | 'audit';
    label: string;
    icon: string;
    disabled?: boolean;
  }> = [
    { id: 'input', label: 'Input', icon: 'input' },
    { id: 'output', label: 'Output', icon: 'output', disabled: !execution },
    {
      id: 'artifacts',
      label: 'Artifacts',
      icon: 'folder',
      disabled: !execution,
    },
    { id: 'audit', label: 'Audit', icon: 'history', disabled: !execution },
  ] as const;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-3xl max-h-[90vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b">
          <div className="flex items-center gap-3">
            <div
              className="w-10 h-10 rounded-lg flex items-center justify-center"
              style={{ backgroundColor: `${task.ui.color}20` }}
            >
              <span className="material-icons" style={{ color: task.ui.color }}>
                {task.ui.icon}
              </span>
            </div>
            <div>
              <h2 className="font-semibold text-gray-900">{task.name}</h2>
              <p className="text-sm text-gray-500">
                {execution?.status || 'Ready to execute'}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
          >
            <span className="material-icons">close</span>
          </button>
        </div>

        {/* Tabs */}
        <div className="flex border-b px-6">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => !tab.disabled && setActiveTab(tab.id)}
              disabled={tab.disabled}
              className={`
                                flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 -mb-px
                                ${
                                  activeTab === tab.id
                                    ? 'border-blue-500 text-blue-600'
                                    : tab.disabled
                                      ? 'border-transparent text-gray-300 cursor-not-allowed'
                                      : 'border-transparent text-gray-500 hover:text-gray-700'
                                }
                            `}
            >
              <span className="material-icons text-lg">{tab.icon}</span>
              {tab.label}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="flex-1 overflow-auto p-6">
          {error && (
            <div className="mb-4 p-3 bg-red-50 text-red-700 rounded-lg flex items-center gap-2">
              <span className="material-icons">error</span>
              {error}
            </div>
          )}

          {activeTab === 'input' && (
            <TaskInputForm
              task={task}
              initialValues={execution?.input}
              onSubmit={handleExecute}
              onCancel={onClose}
              isSubmitting={isSubmitting}
            />
          )}

          {activeTab === 'output' && execution && (
            <TaskOutputDisplay output={execution.output} />
          )}

          {activeTab === 'artifacts' && execution && (
            <TaskArtifactsDisplay artifacts={execution.auditArtifacts} />
          )}

          {activeTab === 'audit' && execution && (
            <TaskAuditDisplay execution={execution} />
          )}
        </div>
      </div>
    </div>
  );
}

// ============================================================================
// Output Display Components
// ============================================================================

interface TaskOutputDisplayProps {
  output: Record<string, unknown> | undefined;
}

function TaskOutputDisplay({ output }: TaskOutputDisplayProps) {
  if (!output) {
    return (
      <div className="text-center py-12 text-gray-500">
        <span className="material-icons text-4xl mb-2">hourglass_empty</span>
        <p>No output yet</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h3 className="font-medium text-gray-900">Task Output</h3>
      <pre className="p-4 bg-gray-50 rounded-lg overflow-auto text-sm font-mono">
        {JSON.stringify(output, null, 2)}
      </pre>
    </div>
  );
}

interface TaskArtifactsDisplayProps {
  artifacts:
    | Array<{
        type: string;
        capturedAt: Date;
        hash?: string;
      }>
    | undefined;
}

function TaskArtifactsDisplay({ artifacts }: TaskArtifactsDisplayProps) {
  if (!artifacts || artifacts.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        <span className="material-icons text-4xl mb-2">folder_off</span>
        <p>No artifacts generated</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h3 className="font-medium text-gray-900">Generated Artifacts</h3>
      <div className="space-y-2">
        {artifacts.map((artifact, index) => (
          <div
            key={index}
            className="p-3 bg-gray-50 rounded-lg flex items-center gap-3"
          >
            <span className="material-icons text-gray-400">description</span>
            <div className="flex-1">
              <p className="font-medium text-sm">{artifact.type}</p>
              {artifact.hash && (
                <p className="text-xs text-gray-500">{artifact.hash}</p>
              )}
            </div>
            <span className="text-xs text-gray-400">
              {new Date(artifact.capturedAt).toLocaleString()}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

interface TaskAuditDisplayProps {
  execution: TaskExecution;
}

function TaskAuditDisplay({ execution }: TaskAuditDisplayProps) {
  const auditEvents = [
    {
      event: 'Task Started',
      timestamp: execution.startedAt,
      icon: 'play_arrow',
    },
    ...(execution.completedAt
      ? [
          {
            event: 'Task Completed',
            timestamp: execution.completedAt,
            icon: 'check_circle',
          },
        ]
      : []),
  ];

  return (
    <div className="space-y-4">
      <h3 className="font-medium text-gray-900">Audit Trail</h3>
      <div className="space-y-2">
        {auditEvents.map((event, index) => (
          <div key={index} className="flex items-center gap-3 p-2">
            <span className="material-icons text-gray-400">{event.icon}</span>
            <span className="flex-1 text-sm">{event.event}</span>
            <span className="text-xs text-gray-400">
              {new Date(event.timestamp ?? Date.now()).toLocaleString()}
            </span>
          </div>
        ))}
      </div>

      {/* Execution Metadata */}
      <div className="mt-4 p-4 bg-gray-50 rounded-lg">
        <h4 className="text-sm font-medium text-gray-700 mb-2">
          Execution Details
        </h4>
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <dt className="text-gray-500">Execution ID</dt>
          <dd className="font-mono">{execution.id}</dd>
          <dt className="text-gray-500">Task ID</dt>
          <dd className="font-mono">{execution.taskId}</dd>
          <dt className="text-gray-500">Status</dt>
          <dd>{execution.status}</dd>
          <dt className="text-gray-500">User</dt>
          <dd>{execution.assignee ?? 'Unassigned'}</dd>
        </dl>
      </div>
    </div>
  );
}

export default TaskInputForm;
