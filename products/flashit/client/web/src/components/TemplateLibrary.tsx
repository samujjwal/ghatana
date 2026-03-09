/**
 * Template Library Component (Web)
 * Browse and use moment templates
 *
 * @doc.type component
 * @doc.purpose Template selection and customization
 * @doc.layer product
 * @doc.pattern TemplateLibrary
 */

import React, { useState, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';

// ============================================================================
// Types
// ============================================================================

type TemplateCategory = 'journal' | 'gratitude' | 'reflection' | 'goal' | 'custom';
type FieldType = 'text' | 'textarea' | 'number' | 'date' | 'time' | 'select' | 'multiselect' | 'checkbox' | 'rating' | 'emotion';

interface TemplateField {
  id: string;
  type: FieldType;
  label: string;
  placeholder?: string;
  required: boolean;
  options?: string[];
  defaultValue?: unknown;
}

interface Template {
  id: string;
  name: string;
  description: string;
  category: TemplateCategory;
  icon?: string;
  color?: string;
  fields: TemplateField[];
  tags: string[];
  isPublic: boolean;
  usageCount: number;
}

interface TemplateError {
  fieldId: string;
  message: string;
}

// ============================================================================
// API Functions
// ============================================================================

async function fetchTemplates(): Promise<Template[]> {
  const response = await fetch('/api/templates');
  const data = await response.json();
  if (!data.success) throw new Error(data.error);
  return data.data;
}

async function fetchTemplatesByCategory(category: TemplateCategory): Promise<Template[]> {
  const response = await fetch(`/api/templates/category/${category}`);
  const data = await response.json();
  if (!data.success) throw new Error(data.error);
  return data.data;
}

async function validateTemplate(templateId: string, values: Record<string, unknown>): Promise<{ valid: boolean; errors: TemplateError[] }> {
  const response = await fetch('/api/templates/validate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ templateId, values }),
  });
  const data = await response.json();
  return { valid: data.valid, errors: data.errors || [] };
}

async function renderTemplate(templateId: string, values: Record<string, unknown>): Promise<string> {
  const response = await fetch('/api/templates/render', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ templateId, values }),
  });
  const data = await response.json();
  if (!data.success) throw new Error(data.error);
  return data.data.markdown;
}

// ============================================================================
// Component
// ============================================================================

interface TemplateLibraryProps {
  onTemplateSelect?: (template: Template) => void;
  onTemplateUse?: (markdown: string) => void;
}

export default function TemplateLibrary({ onTemplateSelect, onTemplateUse }: TemplateLibraryProps) {
  const [selectedCategory, setSelectedCategory] = useState<TemplateCategory | 'all'>('all');
  const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null);
  const [formValues, setFormValues] = useState<Record<string, unknown>>({});
  const [errors, setErrors] = useState<TemplateError[]>([]);

  // Fetch templates
  const { data: templates, isLoading } = useQuery({
    queryKey: ['templates', selectedCategory],
    queryFn: () =>
      selectedCategory === 'all'
        ? fetchTemplates()
        : fetchTemplatesByCategory(selectedCategory),
  });

  // Validate mutation
  const validateMutation = useMutation({
    mutationFn: ({ templateId, values }: { templateId: string; values: Record<string, unknown> }) =>
      validateTemplate(templateId, values),
    onSuccess: (data) => {
      setErrors(data.errors);
    },
  });

  // Render mutation
  const renderMutation = useMutation({
    mutationFn: ({ templateId, values }: { templateId: string; values: Record<string, unknown> }) =>
      renderTemplate(templateId, values),
    onSuccess: (markdown) => {
      onTemplateUse?.(markdown);
      setSelectedTemplate(null);
      setFormValues({});
      setErrors([]);
    },
  });

  // Initialize form values when template is selected
  useEffect(() => {
    if (selectedTemplate) {
      const initialValues: Record<string, unknown> = {};
      for (const field of selectedTemplate.fields) {
        if (field.defaultValue !== undefined) {
          initialValues[field.id] = field.defaultValue;
        }
      }
      setFormValues(initialValues);
      onTemplateSelect?.(selectedTemplate);
    }
  }, [selectedTemplate, onTemplateSelect]);

  const handleFieldChange = (fieldId: string, value: unknown) => {
    setFormValues((prev) => ({ ...prev, [fieldId]: value }));
    setErrors((prev) => prev.filter((e) => e.fieldId !== fieldId));
  };

  const handleValidate = () => {
    if (!selectedTemplate) return;
    validateMutation.mutate({
      templateId: selectedTemplate.id,
      values: formValues,
    });
  };

  const handleUseTemplate = () => {
    if (!selectedTemplate) return;
    
    // Validate first
    validateMutation.mutate(
      {
        templateId: selectedTemplate.id,
        values: formValues,
      },
      {
        onSuccess: (data) => {
          if (data.valid) {
            // Render and use
            renderMutation.mutate({
              templateId: selectedTemplate.id,
              values: formValues,
            });
          }
        },
      }
    );
  };

  const renderField = (field: TemplateField) => {
    const value = formValues[field.id];
    const error = errors.find((e) => e.fieldId === field.id);

    const fieldClass = `w-full p-2 border rounded ${error ? 'border-red-500' : 'border-gray-300'}`;

    switch (field.type) {
      case 'text':
        return (
          <input
            type="text"
            className={fieldClass}
            placeholder={field.placeholder}
            value={String(value || '')}
            onChange={(e) => handleFieldChange(field.id, e.target.value)}
          />
        );

      case 'textarea':
        return (
          <textarea
            className={fieldClass}
            placeholder={field.placeholder}
            rows={4}
            value={String(value || '')}
            onChange={(e) => handleFieldChange(field.id, e.target.value)}
          />
        );

      case 'number':
        return (
          <input
            type="number"
            className={fieldClass}
            placeholder={field.placeholder}
            value={Number(value || 0)}
            onChange={(e) => handleFieldChange(field.id, parseFloat(e.target.value))}
          />
        );

      case 'date':
        return (
          <input
            type="date"
            className={fieldClass}
            value={String(value || '')}
            onChange={(e) => handleFieldChange(field.id, e.target.value)}
          />
        );

      case 'time':
        return (
          <input
            type="time"
            className={fieldClass}
            value={String(value || '')}
            onChange={(e) => handleFieldChange(field.id, e.target.value)}
          />
        );

      case 'select':
        return (
          <select
            className={fieldClass}
            value={String(value || '')}
            onChange={(e) => handleFieldChange(field.id, e.target.value)}
          >
            <option value="">Select...</option>
            {field.options?.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        );

      case 'checkbox':
        return (
          <input
            type="checkbox"
            className="w-4 h-4"
            checked={Boolean(value)}
            onChange={(e) => handleFieldChange(field.id, e.target.checked)}
          />
        );

      case 'rating':
        return (
          <div className="flex gap-2">
            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((rating) => (
              <button
                key={rating}
                type="button"
                className={`w-10 h-10 rounded ${
                  value === rating
                    ? 'bg-blue-500 text-white'
                    : 'bg-gray-200 hover:bg-gray-300'
                }`}
                onClick={() => handleFieldChange(field.id, rating)}
              >
                {rating}
              </button>
            ))}
          </div>
        );

      case 'emotion':
        const emotions = ['😊', '😔', '😠', '😰', '😴', '🤩', '😌', '😢'];
        return (
          <div className="flex gap-2">
            {emotions.map((emoji) => (
              <button
                key={emoji}
                type="button"
                className={`text-3xl p-2 rounded ${
                  value === emoji ? 'bg-blue-100' : 'hover:bg-gray-100'
                }`}
                onClick={() => handleFieldChange(field.id, emoji)}
              >
                {emoji}
              </button>
            ))}
          </div>
        );

      default:
        return <div>Unsupported field type: {field.type}</div>;
    }
  };

  if (isLoading) {
    return <div className="p-4">Loading templates...</div>;
  }

  return (
    <div className="flex h-screen">
      {/* Sidebar */}
      <div className="w-64 bg-gray-50 border-r overflow-y-auto">
        <div className="p-4">
          <h2 className="text-xl font-bold mb-4">Templates</h2>

          {/* Category Filter */}
          <div className="space-y-2">
            <button
              className={`w-full text-left p-2 rounded ${
                selectedCategory === 'all' ? 'bg-blue-500 text-white' : 'hover:bg-gray-200'
              }`}
              onClick={() => setSelectedCategory('all')}
            >
              All Templates
            </button>
            <button
              className={`w-full text-left p-2 rounded ${
                selectedCategory === 'journal' ? 'bg-blue-500 text-white' : 'hover:bg-gray-200'
              }`}
              onClick={() => setSelectedCategory('journal')}
            >
              📔 Journal
            </button>
            <button
              className={`w-full text-left p-2 rounded ${
                selectedCategory === 'gratitude' ? 'bg-blue-500 text-white' : 'hover:bg-gray-200'
              }`}
              onClick={() => setSelectedCategory('gratitude')}
            >
              🙏 Gratitude
            </button>
            <button
              className={`w-full text-left p-2 rounded ${
                selectedCategory === 'reflection' ? 'bg-blue-500 text-white' : 'hover:bg-gray-200'
              }`}
              onClick={() => setSelectedCategory('reflection')}
            >
              💭 Reflection
            </button>
            <button
              className={`w-full text-left p-2 rounded ${
                selectedCategory === 'goal' ? 'bg-blue-500 text-white' : 'hover:bg-gray-200'
              }`}
              onClick={() => setSelectedCategory('goal')}
            >
              🎯 Goals
            </button>
          </div>

          {/* Template List */}
          <div className="mt-4 space-y-2">
            {templates?.map((template) => (
              <button
                key={template.id}
                className={`w-full text-left p-3 rounded border ${
                  selectedTemplate?.id === template.id
                    ? 'border-blue-500 bg-blue-50'
                    : 'border-gray-200 hover:border-gray-300'
                }`}
                onClick={() => setSelectedTemplate(template)}
              >
                <div className="flex items-center gap-2">
                  <span className="text-2xl">{template.icon}</span>
                  <div>
                    <div className="font-medium">{template.name}</div>
                    <div className="text-sm text-gray-500">{template.description}</div>
                  </div>
                </div>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 overflow-y-auto">
        {selectedTemplate ? (
          <div className="p-6">
            <div className="max-w-2xl mx-auto">
              <div className="flex items-center gap-3 mb-6">
                <span className="text-4xl">{selectedTemplate.icon}</span>
                <div>
                  <h1 className="text-2xl font-bold">{selectedTemplate.name}</h1>
                  <p className="text-gray-600">{selectedTemplate.description}</p>
                </div>
              </div>

              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  handleUseTemplate();
                }}
              >
                <div className="space-y-6">
                  {selectedTemplate.fields.map((field) => {
                    const error = errors.find((e) => e.fieldId === field.id);
                    return (
                      <div key={field.id}>
                        <label className="block mb-2">
                          <span className="font-medium">
                            {field.label}
                            {field.required && <span className="text-red-500 ml-1">*</span>}
                          </span>
                        </label>
                        {renderField(field)}
                        {error && <div className="text-red-500 text-sm mt-1">{error.message}</div>}
                      </div>
                    );
                  })}
                </div>

                <div className="mt-8 flex gap-4">
                  <button
                    type="button"
                    onClick={handleValidate}
                    disabled={validateMutation.isPending}
                    className="px-4 py-2 bg-gray-200 rounded hover:bg-gray-300 disabled:opacity-50"
                  >
                    {validateMutation.isPending ? 'Validating...' : 'Validate'}
                  </button>
                  <button
                    type="submit"
                    disabled={renderMutation.isPending}
                    className="px-6 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:opacity-50"
                  >
                    {renderMutation.isPending ? 'Creating...' : 'Use Template'}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedTemplate(null);
                      setFormValues({});
                      setErrors([]);
                    }}
                    className="px-4 py-2 border rounded hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          </div>
        ) : (
          <div className="flex items-center justify-center h-full text-gray-400">
            <div className="text-center">
              <div className="text-6xl mb-4">📝</div>
              <div className="text-xl">Select a template to get started</div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
