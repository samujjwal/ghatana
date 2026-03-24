/**
 * Requirement detail component.
 *
 * @doc.type component
 * @doc.purpose Requirement detail
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import type { RequirementDetailProps } from '../types';

/**
 * Requirement detail component.
 *
 * <p><b>Purpose</b><br>
 * Display detailed view of a requirement.
 *
 * @param props - Component props
 * @returns React component
 *
 * @doc.type function
 * @doc.purpose Requirement detail
 * @doc.layer product
 * @doc.pattern Component
 */
export function RequirementDetail({
  requirement,
  onEdit,
  onDelete,
  readOnly = false,
  darkMode = false,
  className = '',
}: RequirementDetailProps): JSX.Element {
  /**
   * Get priority color.
   *
   * @returns Color class
   */
  const getPriorityColor = (): string => {
    switch (requirement.priority) {
      case 'critical':
        return 'critical';
      case 'high':
        return 'high';
      case 'medium':
        return 'medium';
      case 'low':
        return 'low';
      default:
        return 'medium';
    }
  };

  /**
   * Get status color.
   *
   * @returns Color class
   */
  const getStatusColor = (): string => {
    switch (requirement.status) {
      case 'draft':
        return 'draft';
      case 'active':
        return 'active';
      case 'deprecated':
        return 'deprecated';
      case 'archived':
        return 'archived';
      default:
        return 'active';
    }
  };

  const modeClass = darkMode ? 'dark' : 'light';

  return (
    <div className={`requirement-detail requirement-detail--${modeClass} ${className}`}>
      {/* Header */}
      <div className="requirement-detail__header">
        <div className="requirement-detail__title-section">
          <h3 className="requirement-detail__title">{requirement.title}</h3>
          <p className="requirement-detail__id">{requirement.id}</p>
        </div>

        {/* Badges */}
        <div className="requirement-detail__badges">
          <span className={`requirement-detail__priority requirement-detail__priority--${getPriorityColor()}`}>
            {requirement.priority}
          </span>
          <span className={`requirement-detail__status requirement-detail__status--${getStatusColor()}`}>
            {requirement.status}
          </span>
        </div>
      </div>

      {/* Description */}
      {requirement.description && (
        <div className="requirement-detail__section">
          <h4 className="requirement-detail__section-title">Description</h4>
          <p className="requirement-detail__description">{requirement.description}</p>
        </div>
      )}

      {/* Component ID */}
      {requirement.componentId && (
        <div className="requirement-detail__section">
          <h4 className="requirement-detail__section-title">Component</h4>
          <p className="requirement-detail__component">{requirement.componentId}</p>
        </div>
      )}

      {/* Tags */}
      {requirement.tags.length > 0 && (
        <div className="requirement-detail__section">
          <h4 className="requirement-detail__section-title">Tags</h4>
          <div className="requirement-detail__tags">
            {requirement.tags.map((tag) => (
              <span key={tag} className="requirement-detail__tag">
                {tag}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Actions */}
      {!readOnly && (
        <div className="requirement-detail__actions">
          <button className="requirement-detail__button requirement-detail__button--primary" onClick={onEdit}>
            Edit
          </button>
          <button className="requirement-detail__button requirement-detail__button--danger" onClick={onDelete}>
            Delete
          </button>
        </div>
      )}
    </div>
  );
}
