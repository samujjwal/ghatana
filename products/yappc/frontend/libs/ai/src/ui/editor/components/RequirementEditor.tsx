/**
 * Requirement editor component.
 *
 * @doc.type component
 * @doc.purpose Requirement editor
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback } from 'react';
import type { RequirementEditorProps, RequirementData, EditorState } from '../types';
import { RequirementForm } from './RequirementForm';
import { RequirementDetail } from './RequirementDetail';

/**
 * Requirement editor component.
 *
 * <p><b>Purpose</b><br>
 * Main requirement editor combining form and detail views.
 *
 * @param props - Component props
 * @returns React component
 *
 * @doc.type function
 * @doc.purpose Requirement editor
 * @doc.layer product
 * @doc.pattern Component
 */
export function RequirementEditor({
  requirementId,
  initialData,
  onSave,
  onCancel,
  readOnly = false,
  darkMode = false,
  className = '',
}: RequirementEditorProps): JSX.Element {
  const [state, setState] = useState<EditorState>({
    current: initialData,
    isEditing: !initialData,
    isSaving: false,
  });

  /**
   * Handle form submit.
   *
   * @param data - Requirement data
   */
  const handleSubmit = useCallback(
    (data: RequirementData) => {
      setState((prev) => ({ ...prev, isSaving: true }));

      try {
        onSave?.(data);
        setState((prev) => ({
          ...prev,
          current: data,
          isEditing: false,
          isSaving: false,
          success: 'Requirement saved successfully',
        }));

        // Clear success message after 3 seconds
        setTimeout(() => {
          setState((prev) => ({ ...prev, success: undefined }));
        }, 3000);
      } catch (error) {
        setState((prev) => ({
          ...prev,
          isSaving: false,
          error: error instanceof Error ? error.message : 'Failed to save',
        }));
      }
    },
    [onSave]
  );

  /**
   * Handle cancel.
   */
  const handleCancel = useCallback(() => {
    setState((prev) => ({
      ...prev,
      isEditing: false,
      error: undefined,
    }));
    onCancel?.();
  }, [onCancel]);

  /**
   * Handle edit.
   */
  const handleEdit = useCallback(() => {
    setState((prev) => ({ ...prev, isEditing: true }));
  }, []);

  /**
   * Handle delete.
   */
  const handleDelete = useCallback(() => {
    if (confirm('Are you sure you want to delete this requirement?')) {
      setState((prev) => ({
        ...prev,
        current: undefined,
        isEditing: false,
      }));
      onCancel?.();
    }
  }, [onCancel]);

  const modeClass = darkMode ? 'dark' : 'light';

  return (
    <div className={`requirement-editor requirement-editor--${modeClass} ${className}`}>
      {/* Header */}
      <div className="requirement-editor__header">
        <h2 className="requirement-editor__title">
          {state.isEditing ? 'Edit Requirement' : 'Requirement Details'}
        </h2>
      </div>

      {/* Messages */}
      {state.error && (
        <div className="requirement-editor__error">
          <p>{state.error}</p>
        </div>
      )}

      {state.success && (
        <div className="requirement-editor__success">
          <p>{state.success}</p>
        </div>
      )}

      {/* Content */}
      <div className="requirement-editor__content">
        {state.isEditing ? (
          <RequirementForm
            initialData={state.current}
            onSubmit={handleSubmit}
            onCancel={handleCancel}
            readOnly={readOnly}
            darkMode={darkMode}
          />
        ) : state.current ? (
          <RequirementDetail
            requirement={state.current}
            onEdit={handleEdit}
            onDelete={handleDelete}
            readOnly={readOnly}
            darkMode={darkMode}
          />
        ) : (
          <div className="requirement-editor__empty">
            <p>No requirement selected</p>
          </div>
        )}
      </div>
    </div>
  );
}
