/**
 * Type definitions for requirement editor UI.
 *
 * @doc.type module
 * @doc.purpose Requirement editor UI types
 * @doc.layer product
 * @doc.pattern Value Object
 */

import type { ReactNode } from 'react';

/**
 * Requirement editor props.
 *
 * @doc.type interface
 * @doc.purpose Requirement editor props
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RequirementEditorProps {
  /** Requirement ID */
  requirementId?: string;
  /** Initial requirement data */
  initialData?: RequirementData;
  /** Callback when saved */
  onSave?: (data: RequirementData) => void;
  /** Callback when cancelled */
  onCancel?: () => void;
  /** Read-only mode */
  readOnly?: boolean;
  /** Dark mode */
  darkMode?: boolean;
  /** Custom class name */
  className?: string;
}

/**
 * Requirement data.
 *
 * @doc.type interface
 * @doc.purpose Requirement data
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RequirementData {
  /** Requirement ID */
  id: string;
  /** Title */
  title: string;
  /** Description */
  description: string;
  /** Priority */
  priority: 'critical' | 'high' | 'medium' | 'low';
  /** Status */
  status: 'draft' | 'active' | 'deprecated' | 'archived';
  /** Tags */
  tags: string[];
  /** Component ID */
  componentId?: string;
}

/**
 * Requirement list props.
 *
 * @doc.type interface
 * @doc.purpose Requirement list props
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RequirementListProps {
  /** Requirements */
  requirements: RequirementData[];
  /** Selected requirement ID */
  selectedId?: string;
  /** Callback when requirement selected */
  onSelect?: (requirement: RequirementData) => void;
  /** Callback when requirement deleted */
  onDelete?: (id: string) => void;
  /** Read-only mode */
  readOnly?: boolean;
  /** Dark mode */
  darkMode?: boolean;
  /** Custom class name */
  className?: string;
}

/**
 * Requirement form props.
 *
 * @doc.type interface
 * @doc.purpose Requirement form props
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RequirementFormProps {
  /** Initial data */
  initialData?: RequirementData;
  /** Callback when submitted */
  onSubmit?: (data: RequirementData) => void;
  /** Callback when cancelled */
  onCancel?: () => void;
  /** Read-only mode */
  readOnly?: boolean;
  /** Dark mode */
  darkMode?: boolean;
  /** Custom class name */
  className?: string;
}

/**
 * Requirement detail props.
 *
 * @doc.type interface
 * @doc.purpose Requirement detail props
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RequirementDetailProps {
  /** Requirement data */
  requirement: RequirementData;
  /** Callback when edit clicked */
  onEdit?: () => void;
  /** Callback when delete clicked */
  onDelete?: () => void;
  /** Read-only mode */
  readOnly?: boolean;
  /** Dark mode */
  darkMode?: boolean;
  /** Custom class name */
  className?: string;
}

/**
 * Requirement search props.
 *
 * @doc.type interface
 * @doc.purpose Requirement search props
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RequirementSearchProps {
  /** Requirements */
  requirements: RequirementData[];
  /** Callback when search results change */
  onResults?: (results: RequirementData[]) => void;
  /** Placeholder text */
  placeholder?: string;
  /** Dark mode */
  darkMode?: boolean;
  /** Custom class name */
  className?: string;
}

/**
 * Traceability viewer props.
 *
 * @doc.type interface
 * @doc.purpose Traceability viewer props
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface TraceabilityViewerProps {
  /** Component ID */
  componentId: string;
  /** Requirement IDs */
  requirementIds: string[];
  /** Requirements */
  requirements: RequirementData[];
  /** Callback when requirement selected */
  onSelectRequirement?: (requirement: RequirementData) => void;
  /** Callback when link removed */
  onRemoveLink?: (requirementId: string) => void;
  /** Read-only mode */
  readOnly?: boolean;
  /** Dark mode */
  darkMode?: boolean;
  /** Custom class name */
  className?: string;
}

/**
 * Editor state.
 *
 * @doc.type interface
 * @doc.purpose Editor state
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface EditorState {
  /** Current requirement */
  current?: RequirementData;
  /** Is editing */
  isEditing: boolean;
  /** Is saving */
  isSaving: boolean;
  /** Error message */
  error?: string;
  /** Success message */
  success?: string;
}

/**
 * Search filter.
 *
 * @doc.type interface
 * @doc.purpose Search filter
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface SearchFilter {
  /** Search query */
  query: string;
  /** Priority filter */
  priority?: 'critical' | 'high' | 'medium' | 'low';
  /** Status filter */
  status?: 'draft' | 'active' | 'deprecated' | 'archived';
  /** Tag filter */
  tags?: string[];
}

/**
 * Requirement item props.
 *
 * @doc.type interface
 * @doc.purpose Requirement item props
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RequirementItemProps {
  /** Requirement data */
  requirement: RequirementData;
  /** Is selected */
  isSelected?: boolean;
  /** Callback when clicked */
  onClick?: () => void;
  /** Callback when deleted */
  onDelete?: () => void;
  /** Read-only mode */
  readOnly?: boolean;
  /** Custom class name */
  className?: string;
}

/**
 * Priority badge props.
 *
 * @doc.type interface
 * @doc.purpose Priority badge props
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface PriorityBadgeProps {
  /** Priority level */
  priority: 'critical' | 'high' | 'medium' | 'low';
  /** Custom class name */
  className?: string;
}

/**
 * Status badge props.
 *
 * @doc.type interface
 * @doc.purpose Status badge props
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface StatusBadgeProps {
  /** Status */
  status: 'draft' | 'active' | 'deprecated' | 'archived';
  /** Custom class name */
  className?: string;
}
