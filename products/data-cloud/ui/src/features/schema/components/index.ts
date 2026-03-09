/**
 * Schema feature - Dynamic UI components based on collection schemas.
 *
 * <p><b>Purpose</b><br>
 * Provides reusable components for generating forms and UI elements
 * dynamically based on collection schema definitions.
 *
 * <p><b>Exported Components</b><br>
 * - `DynamicField`: Single field renderer
 * - `DynamicForm`: Complete form generator
 * - `PropertyPanel`: Schema property editor
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { DynamicForm, PropertyPanel } from '@/features/schema';
 *
 * export function MyComponent() {
 *   return (
 *     <>
 *       <DynamicForm schema={schema} onSubmit={handleSubmit} />
 *       <PropertyPanel schema={schema} onChange={handleChange} />
 *     </>
 *   );
 * }
 * }</pre>
 *
 * @doc.type index
 * @doc.purpose Schema-based UI component exports
 * @doc.layer frontend
 */

export { DynamicField } from './DynamicField';
export type { DynamicFieldProps } from './DynamicField';

export { DynamicForm } from './DynamicForm';
export type { DynamicFormProps } from './DynamicForm';

export { PropertyPanel } from './PropertyPanel';
export type { PropertyPanelProps } from './PropertyPanel';

