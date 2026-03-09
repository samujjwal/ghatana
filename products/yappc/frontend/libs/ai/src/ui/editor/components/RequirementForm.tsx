/**
 * Requirement form component.
 *
 * @doc.type component
 * @doc.purpose Requirement form
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState } from 'react';
import type { RequirementFormProps, RequirementData } from '../types';

/**
 * Requirement form component.
 *
 * <p><b>Purpose</b><br>
 * Form for creating and editing requirements.
 *
 * @param props - Component props
 * @returns React component
 *
 * @doc.type function
 * @doc.purpose Requirement form
 * @doc.layer product
 * @doc.pattern Component
 */
export function RequirementForm({
  initialData,
  onSubmit,
  onCancel,
  readOnly = false,
  darkMode = false,
  className = '',
}: RequirementFormProps): JSX.Element {
  const [formData, setFormData] = useState<RequirementData>(
    initialData || {
      id: `req-${Date.now()}`,
      title: '',
      description: '',
      priority: 'medium',
      status: 'draft',
      tags: [],
      componentId: '',
    }
  );

  const [tagInput, setTagInput] = useState('');

  /**
   * Handle input change.
   *
   * @param field - Field name
   * @param value - New value
   */
  const handleChange = (field: keyof RequirementData, value: unknown) => {
    setFormData((prev) => ({
      ...prev,
      [field]: value,
    }));
  };

  /**
   * Handle tag addition.
   */
  const handleAddTag = () => {
    if (tagInput.trim() && !formData.tags.includes(tagInput.trim())) {
      setFormData((prev) => ({
        ...prev,
        tags: [...prev.tags, tagInput.trim()],
      }));
      setTagInput('');
    }
  };

  /**
   * Handle tag removal.
   *
   * @param tag - Tag to remove
   */
  const handleRemoveTag = (tag: string) => {
    setFormData((prev) => ({
      ...prev,
      tags: prev.tags.filter((t) => t !== tag),
    }));
  };

  /**
   * Handle form submit.
   */
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit?.(formData);
  };

  const modeClass = darkMode ? 'dark' : 'light';

  return (
    <form
      className={`requirement-form requirement-form--${modeClass} ${className}`}
      onSubmit={handleSubmit}
    >
      {/* ID Field */}
      <div className="requirement-form__field">
        <label className="requirement-form__label">ID</label>
        <input
          type="text"
          className="requirement-form__input"
          value={formData.id}
          onChange={(e) => handleChange('id', e.target.value)}
          disabled={readOnly}
          required
        />
      </div>

      {/* Title Field */}
      <div className="requirement-form__field">
        <label className="requirement-form__label">Title</label>
        <input
          type="text"
          className="requirement-form__input"
          value={formData.title}
          onChange={(e) => handleChange('title', e.target.value)}
          disabled={readOnly}
          required
        />
      </div>

      {/* Description Field */}
      <div className="requirement-form__field">
        <label className="requirement-form__label">Description</label>
        <textarea
          className="requirement-form__textarea"
          value={formData.description}
          onChange={(e) => handleChange('description', e.target.value)}
          disabled={readOnly}
          rows={4}
        />
      </div>

      {/* Priority Field */}
      <div className="requirement-form__field">
        <label className="requirement-form__label">Priority</label>
        <select
          className="requirement-form__select"
          value={formData.priority}
          onChange={(e) => handleChange('priority', e.target.value)}
          disabled={readOnly}
        >
          <option value="critical">Critical</option>
          <option value="high">High</option>
          <option value="medium">Medium</option>
          <option value="low">Low</option>
        </select>
      </div>

      {/* Status Field */}
      <div className="requirement-form__field">
        <label className="requirement-form__label">Status</label>
        <select
          className="requirement-form__select"
          value={formData.status}
          onChange={(e) => handleChange('status', e.target.value)}
          disabled={readOnly}
        >
          <option value="draft">Draft</option>
          <option value="active">Active</option>
          <option value="deprecated">Deprecated</option>
          <option value="archived">Archived</option>
        </select>
      </div>

      {/* Component ID Field */}
      <div className="requirement-form__field">
        <label className="requirement-form__label">Component ID</label>
        <input
          type="text"
          className="requirement-form__input"
          value={formData.componentId || ''}
          onChange={(e) => handleChange('componentId', e.target.value)}
          disabled={readOnly}
          placeholder="Optional"
        />
      </div>

      {/* Tags Field */}
      <div className="requirement-form__field">
        <label className="requirement-form__label">Tags</label>
        <div className="requirement-form__tag-input">
          <input
            type="text"
            className="requirement-form__input"
            value={tagInput}
            onChange={(e) => setTagInput(e.target.value)}
            onKeyPress={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                handleAddTag();
              }
            }}
            disabled={readOnly}
            placeholder="Add tag and press Enter"
          />
          <button
            type="button"
            className="requirement-form__tag-button"
            onClick={handleAddTag}
            disabled={readOnly}
          >
            Add
          </button>
        </div>

        {/* Tags Display */}
        <div className="requirement-form__tags">
          {formData.tags.map((tag) => (
            <span key={tag} className="requirement-form__tag">
              {tag}
              {!readOnly && (
                <button
                  type="button"
                  className="requirement-form__tag-remove"
                  onClick={() => handleRemoveTag(tag)}
                >
                  ×
                </button>
              )}
            </span>
          ))}
        </div>
      </div>

      {/* Actions */}
      {!readOnly && (
        <div className="requirement-form__actions">
          <button type="submit" className="requirement-form__button requirement-form__button--primary">
            Save
          </button>
          <button
            type="button"
            className="requirement-form__button requirement-form__button--secondary"
            onClick={onCancel}
          >
            Cancel
          </button>
        </div>
      )}
    </form>
  );
}
