/**
 * Template Library Component (Mobile)
 * Browse and use moment templates
 *
 * @doc.type component
 * @doc.purpose Template selection and customization
 * @doc.layer product
 * @doc.pattern TemplateLibrary
 */

import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  ScrollView,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Platform,
} from 'react-native';
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
  onBack?: () => void;
}

export default function TemplateLibrary({ onTemplateSelect, onTemplateUse, onBack }: TemplateLibraryProps) {
  const [view, setView] = useState<'categories' | 'list' | 'form'>('categories');
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
    enabled: view === 'list',
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
      setView('categories');
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

    switch (field.type) {
      case 'text':
        return (
          <View style={styles.fieldContainer}>
            <Text style={styles.fieldLabel}>
              {field.label}
              {field.required && <Text style={styles.required}> *</Text>}
            </Text>
            <TextInput
              style={[styles.input, error && styles.inputError]}
              placeholder={field.placeholder}
              value={String(value || '')}
              onChangeText={(text) => handleFieldChange(field.id, text)}
            />
            {error && <Text style={styles.errorText}>{error.message}</Text>}
          </View>
        );

      case 'textarea':
        return (
          <View style={styles.fieldContainer}>
            <Text style={styles.fieldLabel}>
              {field.label}
              {field.required && <Text style={styles.required}> *</Text>}
            </Text>
            <TextInput
              style={[styles.textArea, error && styles.inputError]}
              placeholder={field.placeholder}
              value={String(value || '')}
              onChangeText={(text) => handleFieldChange(field.id, text)}
              multiline
              numberOfLines={4}
            />
            {error && <Text style={styles.errorText}>{error.message}</Text>}
          </View>
        );

      case 'number':
        return (
          <View style={styles.fieldContainer}>
            <Text style={styles.fieldLabel}>
              {field.label}
              {field.required && <Text style={styles.required}> *</Text>}
            </Text>
            <TextInput
              style={[styles.input, error && styles.inputError]}
              placeholder={field.placeholder}
              value={String(value || '')}
              onChangeText={(text) => handleFieldChange(field.id, parseFloat(text) || 0)}
              keyboardType="numeric"
            />
            {error && <Text style={styles.errorText}>{error.message}</Text>}
          </View>
        );

      case 'rating':
        return (
          <View style={styles.fieldContainer}>
            <Text style={styles.fieldLabel}>
              {field.label}
              {field.required && <Text style={styles.required}> *</Text>}
            </Text>
            <View style={styles.ratingContainer}>
              {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((rating) => (
                <TouchableOpacity
                  key={rating}
                  style={[
                    styles.ratingButton,
                    value === rating && styles.ratingButtonActive,
                  ]}
                  onPress={() => handleFieldChange(field.id, rating)}
                >
                  <Text
                    style={[
                      styles.ratingText,
                      value === rating && styles.ratingTextActive,
                    ]}
                  >
                    {rating}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
            {error && <Text style={styles.errorText}>{error.message}</Text>}
          </View>
        );

      case 'emotion':
        const emotions = ['😊', '😔', '😠', '😰', '😴', '🤩', '😌', '😢'];
        return (
          <View style={styles.fieldContainer}>
            <Text style={styles.fieldLabel}>
              {field.label}
              {field.required && <Text style={styles.required}> *</Text>}
            </Text>
            <View style={styles.emotionContainer}>
              {emotions.map((emoji) => (
                <TouchableOpacity
                  key={emoji}
                  style={[
                    styles.emotionButton,
                    value === emoji && styles.emotionButtonActive,
                  ]}
                  onPress={() => handleFieldChange(field.id, emoji)}
                >
                  <Text style={styles.emotionText}>{emoji}</Text>
                </TouchableOpacity>
              ))}
            </View>
            {error && <Text style={styles.errorText}>{error.message}</Text>}
          </View>
        );

      default:
        return (
          <View style={styles.fieldContainer}>
            <Text>Unsupported field type: {field.type}</Text>
          </View>
        );
    }
  };

  // Categories View
  if (view === 'categories') {
    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Templates</Text>
          {onBack && (
            <TouchableOpacity onPress={onBack} style={styles.backButton}>
              <Text style={styles.backText}>Back</Text>
            </TouchableOpacity>
          )}
        </View>

        <ScrollView style={styles.content}>
          <TouchableOpacity
            style={styles.categoryCard}
            onPress={() => {
              setSelectedCategory('all');
              setView('list');
            }}
          >
            <Text style={styles.categoryIcon}>📚</Text>
            <View style={styles.categoryInfo}>
              <Text style={styles.categoryName}>All Templates</Text>
              <Text style={styles.categoryDesc}>Browse all available templates</Text>
            </View>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.categoryCard}
            onPress={() => {
              setSelectedCategory('journal');
              setView('list');
            }}
          >
            <Text style={styles.categoryIcon}>📔</Text>
            <View style={styles.categoryInfo}>
              <Text style={styles.categoryName}>Journal</Text>
              <Text style={styles.categoryDesc}>Daily journal templates</Text>
            </View>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.categoryCard}
            onPress={() => {
              setSelectedCategory('gratitude');
              setView('list');
            }}
          >
            <Text style={styles.categoryIcon}>🙏</Text>
            <View style={styles.categoryInfo}>
              <Text style={styles.categoryName}>Gratitude</Text>
              <Text style={styles.categoryDesc}>Practice gratitude daily</Text>
            </View>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.categoryCard}
            onPress={() => {
              setSelectedCategory('reflection');
              setView('list');
            }}
          >
            <Text style={styles.categoryIcon}>💭</Text>
            <View style={styles.categoryInfo}>
              <Text style={styles.categoryName}>Reflection</Text>
              <Text style={styles.categoryDesc}>Reflect on your experiences</Text>
            </View>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.categoryCard}
            onPress={() => {
              setSelectedCategory('goal');
              setView('list');
            }}
          >
            <Text style={styles.categoryIcon}>🎯</Text>
            <View style={styles.categoryInfo}>
              <Text style={styles.categoryName}>Goals</Text>
              <Text style={styles.categoryDesc}>Track your goals and progress</Text>
            </View>
          </TouchableOpacity>
        </ScrollView>
      </View>
    );
  }

  // Templates List View
  if (view === 'list') {
    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => setView('categories')}>
            <Text style={styles.backText}>← Back</Text>
          </TouchableOpacity>
          <Text style={styles.headerTitle}>
            {selectedCategory === 'all' ? 'All Templates' : `${selectedCategory} Templates`}
          </Text>
        </View>

        {isLoading ? (
          <View style={styles.centered}>
            <ActivityIndicator size="large" color="#3b82f6" />
          </View>
        ) : (
          <ScrollView style={styles.content}>
            {templates?.map((template) => (
              <TouchableOpacity
                key={template.id}
                style={styles.templateCard}
                onPress={() => {
                  setSelectedTemplate(template);
                  setView('form');
                }}
              >
                <Text style={styles.templateIcon}>{template.icon}</Text>
                <View style={styles.templateInfo}>
                  <Text style={styles.templateName}>{template.name}</Text>
                  <Text style={styles.templateDesc}>{template.description}</Text>
                  <Text style={styles.templateUsage}>Used {template.usageCount} times</Text>
                </View>
              </TouchableOpacity>
            ))}
          </ScrollView>
        )}
      </View>
    );
  }

  // Template Form View
  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => {
            setSelectedTemplate(null);
            setFormValues({});
            setErrors([]);
            setView('list');
          }}
        >
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{selectedTemplate?.name}</Text>
      </View>

      <ScrollView style={styles.content}>
        <View style={styles.formHeader}>
          <Text style={styles.formIcon}>{selectedTemplate?.icon}</Text>
          <Text style={styles.formDesc}>{selectedTemplate?.description}</Text>
        </View>

        {selectedTemplate?.fields.map((field) => (
          <View key={field.id}>{renderField(field)}</View>
        ))}

        <View style={styles.formActions}>
          <TouchableOpacity
            style={[styles.button, styles.buttonPrimary]}
            onPress={handleUseTemplate}
            disabled={renderMutation.isPending}
          >
            <Text style={styles.buttonText}>
              {renderMutation.isPending ? 'Creating...' : 'Use Template'}
            </Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  );
}

// ============================================================================
// Styles
// ============================================================================

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#e5e7eb',
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  backButton: {
    padding: 8,
  },
  backText: {
    fontSize: 16,
    color: '#3b82f6',
  },
  content: {
    flex: 1,
    padding: 16,
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  categoryCard: {
    flexDirection: 'row',
    padding: 16,
    marginBottom: 12,
    backgroundColor: '#f9fafb',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  categoryIcon: {
    fontSize: 36,
    marginRight: 16,
  },
  categoryInfo: {
    flex: 1,
  },
  categoryName: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 4,
  },
  categoryDesc: {
    fontSize: 14,
    color: '#6b7280',
  },
  templateCard: {
    flexDirection: 'row',
    padding: 16,
    marginBottom: 12,
    backgroundColor: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  templateIcon: {
    fontSize: 32,
    marginRight: 16,
  },
  templateInfo: {
    flex: 1,
  },
  templateName: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 4,
  },
  templateDesc: {
    fontSize: 14,
    color: '#6b7280',
    marginBottom: 4,
  },
  templateUsage: {
    fontSize: 12,
    color: '#9ca3af',
  },
  formHeader: {
    alignItems: 'center',
    marginBottom: 24,
  },
  formIcon: {
    fontSize: 48,
    marginBottom: 8,
  },
  formDesc: {
    fontSize: 16,
    color: '#6b7280',
    textAlign: 'center',
  },
  fieldContainer: {
    marginBottom: 24,
  },
  fieldLabel: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
  },
  required: {
    color: '#ef4444',
  },
  input: {
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
  },
  textArea: {
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    minHeight: 100,
    textAlignVertical: 'top',
  },
  inputError: {
    borderColor: '#ef4444',
  },
  errorText: {
    color: '#ef4444',
    fontSize: 14,
    marginTop: 4,
  },
  ratingContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  ratingButton: {
    width: 40,
    height: 40,
    borderRadius: 8,
    backgroundColor: '#f3f4f6',
    justifyContent: 'center',
    alignItems: 'center',
  },
  ratingButtonActive: {
    backgroundColor: '#3b82f6',
  },
  ratingText: {
    fontSize: 16,
    color: '#374151',
  },
  ratingTextActive: {
    color: '#fff',
  },
  emotionContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  emotionButton: {
    width: 56,
    height: 56,
    borderRadius: 12,
    backgroundColor: '#f3f4f6',
    justifyContent: 'center',
    alignItems: 'center',
  },
  emotionButtonActive: {
    backgroundColor: '#dbeafe',
  },
  emotionText: {
    fontSize: 32,
  },
  formActions: {
    marginTop: 24,
    marginBottom: 40,
  },
  button: {
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonPrimary: {
    backgroundColor: '#3b82f6',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
