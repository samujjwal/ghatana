/**
 * Class Name Utility (cn)
 *
 * Combines clsx (conditional class logic) and tailwind-merge (deduplication)
 * to intelligently merge Tailwind CSS classes.
 *
 * @migrated-from @ghatana/yappc-ui/utils/cn
 *
 * @example
 * ```tsx
 * // Basic usage
 * cn('px-4 py-2', 'rounded-md')
 * // → 'px-4 py-2 rounded-md'
 *
 * // Conditional classes
 * cn('px-4', isActive && 'bg-blue-500', disabled && 'opacity-50')
 * // → 'px-4 bg-blue-500 opacity-50' (when isActive=true, disabled=true)
 *
 * // Overriding classes (last wins)
 * cn('p-4', 'p-6')
 * // → 'p-6' (not 'p-4 p-6')
 *
 * // Component composition
 * <Button className={cn('px-4 py-2', variant === 'primary' && 'bg-blue-500', className)} />
 * ```
 *
 * @param inputs - Class names, conditionals, or arrays to merge
 * @returns Merged className string with duplicates removed
 *
 * @see {@link https://github.com/dcastil/tailwind-merge tailwind-merge}
 * @see {@link https://github.com/lukeed/clsx clsx}
 */
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merge class names with Tailwind CSS conflict resolution
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
