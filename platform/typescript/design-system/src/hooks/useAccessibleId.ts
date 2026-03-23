import { useId } from './useId';

/**
 * Accessible ID Hook
 * 
 * Generates a stable, unique accessible ID with an optional prefix.
 * Useful for linking labels, descriptions, and ARIA attributes.
 * 
 * @doc.type hook
 * @doc.purpose Generate stable accessible IDs
 * @doc.layer core
 * @doc.pattern Accessibility Hook
 * 
 * @example
 * ```tsx
 * function FormField({ label, children }) {
 *   const id = useAccessibleId('field');
 *   return (
 *     <div>
 *       <label htmlFor={id}>{label}</label>
 *       {React.cloneElement(children, { id })}
 *     </div>
 *   );
 * }
 * ```
 */
export function useAccessibleId(prefix = 'gh-ui'): string {
  return useId(prefix);
}
