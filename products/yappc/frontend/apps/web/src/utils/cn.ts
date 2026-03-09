/**
 * Class Name Utility
 * 
 * Combines class names with conditional logic and Tailwind merge support.
 * This is a common utility pattern used across the application.
 */

import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merge class names with Tailwind CSS conflict resolution
 * 
 * @param inputs - Class names to merge
 * @returns Merged class name string
 * 
 * @example
 * cn('px-2 py-1', 'px-4') // => 'py-1 px-4' (px-2 overridden)
 * cn('text-red-500', condition && 'text-blue-500') // conditional classes
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
